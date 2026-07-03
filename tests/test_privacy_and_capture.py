import pytest

from fluentmai_core.capture_proxy import CaptureHelperController, NoopSystemProxyBackend, ProxySnapshot
from fluentmai_core.privacy import PrivacyRedactor


def test_privacy_redactor_masks_tokens_urls_and_html():
    text = (
        'Cookie: _t=abc; token=secret https://maimai.wahlap.com/callback?code=abc '
        '<html><body>secret</body></html>'
    )
    redacted = PrivacyRedactor().redact(text)

    assert "abc" not in redacted
    assert "secret" not in redacted
    assert "[REDACTED_AUTH_URL]" in redacted
    assert "[REDACTED_HTML]" in redacted


def test_capture_helper_requires_real_executable(tmp_path):
    controller = CaptureHelperController(helper_path=str(tmp_path / "missing.exe"))

    with pytest.raises(FileNotFoundError):
        controller.start(config_path=str(tmp_path / "config.json"))


def test_noop_proxy_backend_records_restore():
    backend = NoopSystemProxyBackend()
    snap = ProxySnapshot(1, "127.0.0.1:7890", None)

    backend.apply("127.0.0.1:8033")
    backend.restore(snap)

    assert backend.last_applied == "127.0.0.1:8033"
    assert backend.restored == snap
