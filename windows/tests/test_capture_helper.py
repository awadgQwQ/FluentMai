from __future__ import annotations

import asyncio
import json
from pathlib import Path
import socket
import subprocess
import sys

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.x509.oid import NameOID
import pytest
import requests

from capture_helper import certificate
from capture_helper.ipc import encode_event, receive_event
from capture_helper.main import WahlapCaptureAddon


SCORE_HTML = """
<html><body>
<form action="/maimai-mobile/record/musicDetail/">
<div class="music_name_block">Fixture</div>
<div class="music_score_block">100.0000%</div>
</form>
</body></html>
"""


def _free_port() -> int:
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])
    finally:
        sock.close()


def _response(body: str = SCORE_HTML, status: int = 200) -> requests.Response:
    response = requests.Response()
    response.status_code = status
    response._content = body.encode("utf-8")
    response.encoding = "utf-8"
    return response


def test_ca_store_has_fluentmai_identity_and_wahlap_name_constraint(monkeypatch, tmp_path):
    monkeypatch.setattr(certificate, "is_ca_installed", lambda _thumbprint: False)

    info = certificate.ensure_ca_store(tmp_path)
    cert = x509.load_pem_x509_certificate(info.certificate_path.read_bytes())
    key = serialization.load_pem_private_key(info.combined_key_path.read_bytes(), password=None)
    names = cert.subject.get_attributes_for_oid(NameOID.COMMON_NAME)
    constraints = cert.extensions.get_extension_for_class(x509.NameConstraints).value
    permitted = {item.value for item in constraints.permitted_subtrees or [] if isinstance(item, x509.DNSName)}

    assert names[0].value == certificate.CA_COMMON_NAME
    assert permitted == {"wahlap.com"}
    assert key.public_key().public_numbers() == cert.public_key().public_numbers()
    assert info.thumbprint == cert.fingerprint(hashes.SHA1()).hex().upper()
    assert (tmp_path / "mitmproxy-dhparam.pem").is_file()
    assert certificate.ensure_ca_store(tmp_path).thumbprint == info.thumbprint


def test_ca_install_timeout_has_safe_category(monkeypatch, tmp_path):
    monkeypatch.setattr(certificate, "is_ca_installed", lambda _thumbprint: False)
    info = certificate.ensure_ca_store(tmp_path)

    def timeout(*_args, **_kwargs):
        raise subprocess.TimeoutExpired("certutil", 30)

    monkeypatch.setattr(certificate.subprocess, "run", timeout)

    with pytest.raises(RuntimeError, match="^ca_installation_timeout$"):
        certificate.install_ca_current_user(info)


def test_ipc_frame_round_trip_uses_length_prefix():
    left, right = socket.socketpair()
    try:
        event = {"type": "progress", "stage": "safe", "count": 3}
        left.sendall(encode_event(event))
        assert receive_event(right, timeout=1) == event
    finally:
        left.close()
        right.close()


def test_difficulty_fetch_retries_are_finite_and_reported():
    class Ipc:
        def __init__(self):
            self.events = []

        async def send(self, event):
            self.events.append(event)

    class Session:
        def __init__(self):
            self.calls = 0

        def get(self, *_args, **_kwargs):
            self.calls += 1
            if self.calls < 3:
                raise requests.Timeout()
            return _response()

    ipc = Ipc()
    session = Session()
    addon = WahlapCaptureAddon(
        master=object(),
        ipc=ipc,
        request_timeout=1,
        wait_timeout=1,
        retries=2,
        retry_delay=0,
    )

    response, attempt, _elapsed = asyncio.run(addon._fetch_one(session, 3))

    assert response.status_code == 200
    assert attempt == 3
    assert session.calls == 3
    assert [event["stage"] for event in ipc.events] == ["retrying_difficulty", "retrying_difficulty"]


def test_difficulty_fetch_stops_after_configured_attempts():
    class Ipc:
        async def send(self, _event):
            return None

    class Session:
        def __init__(self):
            self.calls = 0

        def get(self, *_args, **_kwargs):
            self.calls += 1
            raise requests.Timeout()

    session = Session()
    addon = WahlapCaptureAddon(
        master=object(),
        ipc=Ipc(),
        request_timeout=1,
        wait_timeout=1,
        retries=2,
        retry_delay=0,
    )

    with pytest.raises(RuntimeError, match="network_timeout"):
        asyncio.run(addon._fetch_one(session, 3))

    assert session.calls == 3


def test_helper_starts_loopback_proxy_without_installing_ca(tmp_path):
    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.bind(("127.0.0.1", 0))
    listener.listen(1)
    listener.settimeout(15)
    ipc_port = int(listener.getsockname()[1])
    proxy_port = _free_port()
    token = "a" * 64
    command = [
        sys.executable,
        "-m",
        "capture_helper.main",
        "--ipc-port",
        str(ipc_port),
        "--proxy-port",
        str(proxy_port),
        "--cert-dir",
        str(tmp_path / "certs"),
    ]
    assert token not in command
    process = subprocess.Popen(
        command,
        cwd=Path(__file__).resolve().parents[1],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
    )
    try:
        assert process.stdin is not None
        process.stdin.write(
            json.dumps(
                {
                    "token": token,
                    "install_ca": False,
                    "wait_timeout": 0.5,
                    "request_timeout": 1,
                    "retries": 0,
                    "retry_delay": 0,
                }
            )
            + "\n"
        )
        process.stdin.close()
        connection, peer = listener.accept()
        assert peer[0] == "127.0.0.1"
        try:
            assert receive_event(connection, 5) == {"type": "auth", "token": token}
            events = []
            while True:
                event = receive_event(connection, 10)
                events.append(event)
                if event.get("type") == "ready":
                    probe = socket.create_connection(("127.0.0.1", proxy_port), timeout=3)
                    probe.close()
                if event.get("type") == "error":
                    break
            assert any(event.get("type") == "certificate" for event in events)
            assert any(event.get("type") == "ready" for event in events)
            assert events[-1].get("category") == "capture_timeout"
        finally:
            connection.close()
        assert process.wait(timeout=15) == 0
    finally:
        listener.close()
        if process.poll() is None:
            process.kill()
            process.wait(timeout=5)
