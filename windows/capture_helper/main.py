from __future__ import annotations

import argparse
import asyncio
from dataclasses import dataclass
from http.cookies import SimpleCookie
import json
import os
from pathlib import Path
import re
import sys
import time

from mitmproxy import http, options
from mitmproxy.tools.dump import DumpMaster
import requests

from capture_helper import HELPER_VERSION
from capture_helper.certificate import (
    ensure_ca_store,
    install_ca_current_user,
    safe_certificate_fingerprint,
)
from capture_helper.ipc import AsyncIpcClient


TARGET_HOST = "maimai.wahlap.com"
HOME_PREFIX = "/maimai-mobile/home"
RECORD_REFERER = "https://maimai.wahlap.com/maimai-mobile/record/"
DIFFICULTY_URL = (
    "https://maimai.wahlap.com/maimai-mobile/record/musicSort/search/"
    "?search=A&sort=1&playCheck=on&diff={difficulty}"
)
HEADER_ALLOWLIST = {
    "user-agent",
    "accept",
    "accept-language",
    "sec-ch-ua",
    "sec-ch-ua-mobile",
    "sec-ch-ua-platform",
    "sec-fetch-site",
    "sec-fetch-mode",
    "sec-fetch-user",
    "sec-fetch-dest",
    "upgrade-insecure-requests",
}


@dataclass(frozen=True)
class CapturedBrowserSession:
    cookies: dict[str, str]
    headers: dict[str, str]
    home_html: str
    home_status: int


class WahlapCaptureAddon:
    def __init__(
        self,
        master: DumpMaster,
        ipc: AsyncIpcClient,
        *,
        request_timeout: float,
        wait_timeout: float,
        retries: int,
        retry_delay: float,
    ):
        self.master = master
        self.ipc = ipc
        self.request_timeout = request_timeout
        self.wait_timeout = wait_timeout
        self.retries = retries
        self.retry_delay = retry_delay
        self.started = False
        self.timeout_task: asyncio.Task | None = None

    async def running(self) -> None:
        await self.ipc.send(
            {
                "type": "ready",
                "helper_version": HELPER_VERSION,
                "proxy_host": "127.0.0.1",
            }
        )
        self.timeout_task = asyncio.create_task(self._capture_timeout())

    async def response(self, flow: http.HTTPFlow) -> None:
        if self.started or flow.response is None:
            return
        if flow.request.pretty_host.lower() != TARGET_HOST:
            return
        if not flow.request.path.startswith(HOME_PREFIX):
            return

        self.started = True
        if self.timeout_task:
            self.timeout_task.cancel()
        captured = _capture_browser_session(flow)
        await self.ipc.send(
            {
                "type": "session_captured",
                "home_status": captured.home_status,
                "home_bytes": len(captured.home_html.encode("utf-8")),
            }
        )
        asyncio.create_task(self._fetch_pages(captured))

    async def _capture_timeout(self) -> None:
        try:
            await asyncio.sleep(self.wait_timeout)
            if not self.started:
                await self.ipc.send({"type": "error", "category": "capture_timeout"})
                self.master.shutdown()
        except asyncio.CancelledError:
            return

    async def _fetch_pages(self, captured: CapturedBrowserSession) -> None:
        session = _session_from_capture(captured)
        page_count = 0
        parsed_bytes = 0
        try:
            await self.ipc.send(
                {
                    "type": "page",
                    "page_kind": "home",
                    "http_status": captured.home_status,
                    "body": captured.home_html,
                    "bytes": len(captured.home_html.encode("utf-8")),
                }
            )
            captured = CapturedBrowserSession({}, {}, "", captured.home_status)
            for difficulty in range(5):
                await self.ipc.send(
                    {
                        "type": "progress",
                        "stage": "fetching_difficulty",
                        "difficulty": difficulty,
                        "current": difficulty,
                        "total": 5,
                    }
                )
                response, attempt, elapsed_ms = await self._fetch_one(session, difficulty)
                body = response.text
                size = len(body.encode("utf-8"))
                page_count += 1
                parsed_bytes += size
                await self.ipc.send(
                    {
                        "type": "page",
                        "page_kind": "difficulty",
                        "difficulty": difficulty,
                        "http_status": response.status_code,
                        "body": body,
                        "bytes": size,
                        "attempt": attempt,
                        "elapsed_ms": elapsed_ms,
                    }
                )
                del body, response
                if difficulty < 4 and self.retry_delay > 0:
                    await asyncio.sleep(self.retry_delay)
            await self.ipc.send(
                {
                    "type": "complete",
                    "captured_pages": page_count,
                    "captured_bytes": parsed_bytes,
                }
            )
        except Exception as exc:
            await self.ipc.send(
                {
                    "type": "error",
                    "category": _safe_error_category(exc),
                }
            )
        finally:
            session.close()
            await asyncio.sleep(0.1)
            self.master.shutdown()

    async def _fetch_one(
        self,
        session: requests.Session,
        difficulty: int,
    ) -> tuple[requests.Response, int, int]:
        last_category = "network_error"
        for attempt in range(1, self.retries + 2):
            started = time.perf_counter()
            try:
                response = await asyncio.to_thread(
                    session.get,
                    DIFFICULTY_URL.format(difficulty=difficulty),
                    timeout=self.request_timeout,
                )
                elapsed_ms = round((time.perf_counter() - started) * 1000)
                if response.status_code == 200 and _looks_like_score_page(response.text):
                    return response, attempt, elapsed_ms
                last_category = (
                    "authentication_expired"
                    if _looks_like_auth_failure(response.text)
                    else "wahlap_challenge_or_unexpected_page"
                )
            except requests.Timeout:
                last_category = "network_timeout"
            except requests.RequestException:
                last_category = "network_error"
            if attempt <= self.retries:
                await self.ipc.send(
                    {
                        "type": "progress",
                        "stage": "retrying_difficulty",
                        "difficulty": difficulty,
                        "attempt": attempt,
                        "max_attempts": self.retries + 1,
                        "category": last_category,
                    }
                )
                await asyncio.sleep(self.retry_delay)
        raise RuntimeError(last_category)


def _capture_browser_session(flow: http.HTTPFlow) -> CapturedBrowserSession:
    cookies = _parse_cookie_header(flow.request.headers.get("cookie", ""))
    for header in flow.response.headers.get_all("set-cookie"):
        cookies.update(_parse_set_cookie(header))
    headers = {
        name: value
        for name, value in flow.request.headers.items()
        if name.lower() in HEADER_ALLOWLIST
    }
    headers["Referer"] = RECORD_REFERER
    return CapturedBrowserSession(
        cookies=cookies,
        headers=headers,
        home_html=flow.response.get_text(strict=False),
        home_status=flow.response.status_code,
    )


def _session_from_capture(captured: CapturedBrowserSession) -> requests.Session:
    session = requests.Session()
    session.trust_env = False
    session.headers.update(captured.headers)
    session.headers["Referer"] = RECORD_REFERER
    for name, value in captured.cookies.items():
        session.cookies.set(name, value, domain=TARGET_HOST, path="/")
    return session


def _parse_cookie_header(value: str) -> dict[str, str]:
    cookie = SimpleCookie()
    try:
        cookie.load(value)
    except Exception:
        return {}
    return {name: morsel.value for name, morsel in cookie.items()}


def _parse_set_cookie(value: str) -> dict[str, str]:
    cookie = SimpleCookie()
    try:
        cookie.load(value)
    except Exception:
        return {}
    return {name: morsel.value for name, morsel in cookie.items()}


def _looks_like_score_page(html: str) -> bool:
    return "musicDetail" in html and "music_name_block" in html and "music_score_block" in html


def _looks_like_auth_failure(html: str) -> bool:
    lowered = html.lower()
    return (
        "open.weixin.qq.com/connect/oauth2/authorize" in lowered
        or "/wc_auth/oauth/authorize/" in lowered
        or "title_error" in lowered
        or "登录失败" in html
    )


def _safe_error_category(exc: Exception) -> str:
    text = str(exc)
    allowed = {
        "network_error",
        "network_timeout",
        "authentication_expired",
        "wahlap_challenge_or_unexpected_page",
        "ca_installation_timeout",
    }
    return text if text in allowed else type(exc).__name__


def _read_bootstrap() -> dict:
    line = sys.stdin.readline(65536)
    value = json.loads(line)
    if not isinstance(value, dict):
        raise ValueError("Bootstrap value must be an object.")
    token = str(value.get("token") or "")
    if not re.fullmatch(r"[a-f0-9]{64}", token):
        raise ValueError("Invalid session token.")
    return value


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--ipc-port", type=int, required=True)
    parser.add_argument("--proxy-port", type=int, required=True)
    parser.add_argument("--cert-dir", required=True)
    return parser


async def _run(args: argparse.Namespace, bootstrap: dict) -> None:
    ipc = AsyncIpcClient(args.ipc_port, str(bootstrap["token"]))
    await ipc.connect()
    master: DumpMaster | None = None
    try:
        info = ensure_ca_store(args.cert_dir)
        if bool(bootstrap.get("install_ca", True)):
            info = install_ca_current_user(info)
        await ipc.send(
            {
                "type": "certificate",
                "installed": info.installed,
                "fingerprint": safe_certificate_fingerprint(info),
            }
        )
        proxy_options = options.Options(
            listen_host="127.0.0.1",
            listen_port=int(args.proxy_port),
            confdir=str(Path(args.cert_dir).resolve()),
            allow_hosts=[r"^maimai\.wahlap\.com(?::443)?$"],
            show_ignored_hosts=False,
        )
        master = DumpMaster(proxy_options, with_termlog=False, with_dumper=False)
        addon = WahlapCaptureAddon(
            master,
            ipc,
            request_timeout=float(bootstrap.get("request_timeout", 30)),
            wait_timeout=float(bootstrap.get("wait_timeout", 180)),
            retries=max(0, min(int(bootstrap.get("retries", 2)), 4)),
            retry_delay=max(0.0, min(float(bootstrap.get("retry_delay", 1.5)), 10.0)),
        )
        master.addons.add(addon)
        await master.run()
    except Exception as exc:
        try:
            await ipc.send({"type": "error", "category": _safe_error_category(exc)})
        except Exception:
            pass
        if master is not None:
            master.shutdown()
    finally:
        await ipc.close()


def main() -> int:
    os.environ.setdefault("PYTHONUNBUFFERED", "1")
    try:
        args = _parser().parse_args()
        if not (1024 <= args.ipc_port <= 65535 and 1024 <= args.proxy_port <= 65535):
            return 2
        bootstrap = _read_bootstrap()
        asyncio.run(_run(args, bootstrap))
        return 0
    except Exception:
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
