from __future__ import annotations

import re
import time
from http.cookies import SimpleCookie
from typing import Any, Callable
from urllib.parse import urlparse

import requests
from bs4 import BeautifulSoup

from . import database
from .models import (
    DIFFICULTY_NAMES,
    ParsedScoreRecord,
    as_float,
    as_int,
    difficulty_name,
    normalize_song_type,
    sha256_text,
)
from .privacy import redactor


WAHLAP_HOME_URL = "https://maimai.wahlap.com/maimai-mobile/home/"
WAHLAP_RECORD_URL = "https://maimai.wahlap.com/maimai-mobile/record/"
WAHLAP_DIFF_URL = (
    "https://maimai.wahlap.com/maimai-mobile/record/musicSort/search/"
    "?search=A&sort=1&playCheck=on&diff={diff}"
)
HTTP_TIMEOUT = 30
DEFAULT_DELAY = 2.5
MOBILE_UA = (
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
)


ProgressCallback = Callable[[int, str, dict[str, Any]], None]


def normalize_auth_url(auth_url: str) -> str:
    trimmed = auth_url.strip()
    if not trimmed.startswith(("http://", "https://")):
        raise ValueError("Captured auth URL must start with http:// or https://.")
    if trimmed.lower().startswith("http://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx"):
        return "https://" + trimmed[len("http://") :]
    return trimmed


def is_reqable_dump(text: str) -> bool:
    lowered = text.lower()
    return ":method:" in lowered or ":authority:" in lowered or "\ncookie:" in lowered


def parse_cookie_string(cookie_text: str) -> dict[str, str]:
    cookie = SimpleCookie()
    try:
        cookie.load(cookie_text)
    except Exception:
        pass
    parsed = {key: morsel.value for key, morsel in cookie.items()}
    if parsed:
        return parsed
    result: dict[str, str] = {}
    for part in cookie_text.split(";"):
        if "=" not in part:
            continue
        key, _, value = part.partition("=")
        result[key.strip()] = value.strip()
    return result


def parse_reqable_dump(raw_text: str) -> tuple[dict[str, str], dict[str, str]]:
    cookies: dict[str, str] = {}
    headers: dict[str, str] = {}
    for line in raw_text.splitlines():
        line = line.strip()
        if not line or ": " not in line:
            continue
        key, _, value = line.partition(": ")
        lower = key.lower()
        if lower in {":method", ":authority", ":path", ":scheme"}:
            continue
        if lower == "cookie":
            cookies.update(parse_cookie_string(value))
            continue
        if lower in {
            "user-agent",
            "accept",
            "accept-language",
            "x-requested-with",
            "sec-ch-ua",
            "sec-ch-ua-mobile",
            "sec-ch-ua-platform",
            "referer",
            "sec-fetch-site",
            "sec-fetch-mode",
            "sec-fetch-user",
            "sec-fetch-dest",
            "upgrade-insecure-requests",
        }:
            headers[_canonical_header(lower)] = value
    return cookies, headers


def cookies_from_input(text: str) -> tuple[dict[str, str], dict[str, str]]:
    if is_reqable_dump(text):
        return parse_reqable_dump(text)
    return parse_cookie_string(text), {}


def make_session_from_cookie_input(cookie_or_dump: str) -> tuple[requests.Session, dict[str, str]]:
    cookies, extra_headers = cookies_from_input(cookie_or_dump)
    missing = [name for name in ("_t", "userId") if name not in cookies]
    if missing:
        raise ValueError(f"Wahlap cookie is missing required fields: {', '.join(missing)}")
    session = requests.Session()
    session.trust_env = False
    for key, value in cookies.items():
        session.cookies.set(key, value, domain="maimai.wahlap.com", path="/")
    session.headers.update(build_wahlap_headers(extra_headers))
    return session, extra_headers


def build_wahlap_headers(extra: dict[str, str] | None = None) -> dict[str, str]:
    headers = dict(extra or {})
    headers.setdefault("User-Agent", MOBILE_UA)
    headers.setdefault("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    headers.setdefault("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
    headers.setdefault("Referer", WAHLAP_HOME_URL)
    headers.update(
        {
            "Sec-Fetch-Mode": "navigate",
            "Sec-Fetch-Dest": "document",
            "Sec-Fetch-User": "?1",
            "Upgrade-Insecure-Requests": "1",
        }
    )
    return headers


class WahlapClient:
    def __init__(self, session: requests.Session | None = None, delay_sec: float = DEFAULT_DELAY):
        self.session = session or requests.Session()
        self.session.trust_env = False
        self.delay_sec = delay_sec
        self.session.headers.update(build_wahlap_headers())

    def login_with_auth_url(self, auth_url: str) -> None:
        url = normalize_auth_url(auth_url)
        response = self.session.get(url, timeout=HTTP_TIMEOUT, headers=build_wahlap_headers())
        if response.status_code not in range(200, 400):
            raise RuntimeError(f"Wahlap auth returned HTTP {response.status_code}")
        self.assert_logged_in()

    def use_cookie_input(self, cookie_or_dump: str) -> None:
        session, headers = make_session_from_cookie_input(cookie_or_dump)
        self.session = session
        self.session.headers.update(build_wahlap_headers(headers))
        self.assert_logged_in()

    def assert_logged_in(self) -> None:
        response = self.session.get(WAHLAP_RECORD_URL, timeout=HTTP_TIMEOUT)
        final_url = response.url.lower()
        if "/error/" in final_url or "open.weixin.qq.com" in final_url or looks_like_auth_failure(response.text):
            raise RuntimeError("Wahlap login/cookie is invalid or expired.")

    def fetch_score_pages(self, progress: ProgressCallback | None = None) -> list[tuple[int, str]]:
        pages: list[tuple[int, str]] = []
        for diff in range(5):
            if progress:
                progress(diff, "fetching", {"label": difficulty_name(diff)})
            response = self.session.get(WAHLAP_DIFF_URL.format(diff=diff), timeout=HTTP_TIMEOUT)
            html = response.text
            if response.status_code not in range(200, 300) or looks_like_auth_failure(html):
                raise RuntimeError(f"Wahlap score page fetch failed for {difficulty_name(diff)}.")
            pages.append((diff, html))
            if progress:
                progress(
                    diff,
                    "fetched",
                    {
                        "label": difficulty_name(diff),
                        "http_status": response.status_code,
                        "size_kb": round(len(html.encode("utf-8")) / 1024, 1),
                        "has_data": looks_like_score_page(html),
                    },
                )
            if diff < 4 and self.delay_sec > 0:
                time.sleep(self.delay_sec)
        return pages


def parse_wahlap_pages(
    pages: list[tuple[int, str]],
    *,
    db_path: str | None = None,
) -> list[ParsedScoreRecord]:
    conn = database.connect(db_path)
    try:
        parser = WahlapHtmlParser(conn)
        records: list[ParsedScoreRecord] = []
        for diff, html in pages:
            records.extend(parser.parse(html, fixed_difficulty=diff))
        return records
    finally:
        conn.close()


class WahlapHtmlParser:
    def __init__(self, conn=None):
        self.conn = conn

    def parse(self, html: str, fixed_difficulty: int | None = None) -> list[ParsedScoreRecord]:
        if not html.strip():
            return []
        soup = BeautifulSoup(html, "html.parser")
        records: list[ParsedScoreRecord] = []
        for card in soup.select('form[action*="musicDetail"]'):
            title = self._text(card, ".music_name_block") or None
            level_index = fixed_difficulty if fixed_difficulty is not None else self._extract_level_index(card)
            detected_type = self._extract_song_type(card)
            level = self._extract_level(card)
            song_id = None
            chart_type = detected_type
            if self.conn is not None and title and level_index is not None:
                resolved = database.resolve_chart(
                    self.conn,
                    title=title,
                    chart_type=chart_type,
                    difficulty_index=level_index,
                    level=level,
                )
                if resolved is not None:
                    song_id = resolved["song_id"]
                    chart_type = resolved["chart_type"]
                    level = level or resolved["level"]
                    title = resolved["title"]
            outer = str(card)
            records.append(
                ParsedScoreRecord(
                    title=title,
                    song_id=song_id,
                    song_type=chart_type,
                    difficulty_index=level_index,
                    level=level,
                    achievements=self._extract_achievement(card),
                    dx_score=self._extract_dx_score(card),
                    full_combo=self._extract_clear_icon(card, {"fc", "fcp", "ap", "app"}),
                    full_sync=self._extract_clear_icon(card, {"sync", "fs", "fsp", "fsd", "fsdp"}),
                    raw_fingerprint=sha256_text(outer),
                )
            )
        return records

    def _text(self, card, selector: str) -> str:
        element = card.select_one(selector)
        return element.get_text("", strip=True) if element else ""

    def _extract_achievement(self, card) -> float | None:
        selectors = ".music_score_block.w_112.t_r.f_l.f_12, .music_score_block.w_150.t_l.f_r.f_12"
        for element in card.select(selectors):
            match = re.search(r"([0-9]{1,3}(?:\.[0-9]{1,4})?)\s*%", element.get_text(" ", strip=True))
            if match:
                return as_float(match.group(1))
        return None

    def _extract_dx_score(self, card) -> int | None:
        text = self._text(card, ".music_score_block.w_190.t_r.f_l.f_12")
        match = re.search(r"\d{1,3}(?:,\d{3})*", text)
        return as_int(match.group(0).replace(",", "")) if match else None

    def _extract_level(self, card) -> str:
        selector_text = self._text(
            card,
            ".music_lv_block, .music_level_block, .music_level, .level_block, .music_lv",
        )
        if re.fullmatch(r"[0-9]{1,2}\+?", selector_text):
            return selector_text
        match = re.search(r"(?:等级|LEVEL|Lv\.?)\s*([0-9]{1,2}\+?)", card.get_text(" ", strip=True), re.I)
        return match.group(1) if match else ""

    def _extract_level_index(self, card) -> int | None:
        for element in card.select("input[name=diff], input[name=difficulty], input[name=level_index], input[name=levelIndex]"):
            value = as_int(element.get("value"))
            if value is not None and 0 <= value <= 4:
                return value
        signal = " ".join(
            [
                card.get("class", []).__str__(),
                card.get_text(" ", strip=True),
                " ".join(img.get("src", "") + " " + img.get("alt", "") for img in card.select("img")),
            ]
        ).lower()
        patterns = [
            ("remaster", 4),
            ("re:master", 4),
            ("master", 3),
            ("expert", 2),
            ("advanced", 1),
            ("basic", 0),
        ]
        for token, idx in patterns:
            if token in signal:
                return idx
        return None

    def _extract_song_type(self, card) -> str:
        signal = " ".join(
            [
                card.get("id", ""),
                " ".join(
                    " ".join([img.get("src", ""), img.get("class", []).__str__(), img.get("alt", "")])
                    for img in card.select(".music_kind_icon, img.music_kind_icon, img[src*=music_dx], img[src*=music_standard]")
                ),
            ]
        )
        return "DX" if re.search(r"(^|[/_\-\s])dx([._\-\s/]|$)|music_dx", signal, re.I) else "SD"

    def _extract_clear_icon(self, card, allowed: set[str]) -> str:
        for img in card.select("img.h_30.f_r, img[src*=music_icon_]"):
            src = img.get("src", "")
            match = re.search(r"music_icon_([^./?]+)\.png", src)
            if match:
                value = match.group(1).lower()
                if value in allowed:
                    return value
        return ""


def looks_like_auth_failure(html: str) -> bool:
    lowered = html.lower()
    return (
        "please open in wechat" in lowered
        or "open.weixin.qq.com/connect/oauth2/authorize" in lowered
        or "/wc_auth/oauth/authorize/" in lowered
        or "title_error" in lowered
        or "错误码" in html
        or "登录失败" in html
    )


def looks_like_score_page(html: str) -> bool:
    return (
        "musicDetail" in html
        and "music_name_block" in html
        and "music_score_block" in html
    )


def safe_url_summary(url: str) -> str:
    try:
        parsed = urlparse(url)
        query = (parsed.query or "").lower()
        return (
            f"scheme={parsed.scheme} host={parsed.hostname} path={parsed.path} "
            f"hasCode={'code=' in query} hasState={'state=' in query}"
        )
    except Exception:
        return "unparseable"


def _canonical_header(lower_name: str) -> str:
    return "-".join(part.capitalize() for part in lower_name.split("-"))


def safe_error(exc: Exception) -> str:
    return redactor.redact(exc)
