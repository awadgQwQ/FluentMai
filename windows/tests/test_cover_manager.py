from __future__ import annotations

from pathlib import Path

from PyQt6.QtCore import QBuffer, QIODevice
from PyQt6.QtGui import QColor, QImage

import cover_manager


def _png_bytes() -> bytes:
    image = QImage(2, 2, QImage.Format.Format_RGB32)
    image.fill(QColor("#8b5cf6"))
    data = QBuffer()
    data.open(QIODevice.OpenModeFlag.WriteOnly)
    image.save(data, "PNG")
    return bytes(data.data())


PNG_BYTES = _png_bytes()


def _write_png(path: Path) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(PNG_BYTES)
    return path


def _patch_roots(monkeypatch, tmp_path: Path) -> tuple[Path, Path, Path]:
    source = tmp_path / "source"
    bundled = tmp_path / "bundle"
    exe = tmp_path / "exe"
    monkeypatch.setenv("FLUENTMAI_CACHE_DIR", str(tmp_path / "cache"))
    monkeypatch.setattr(cover_manager, "source_root", lambda: source)
    monkeypatch.setattr(cover_manager, "bundled_root", lambda: bundled)
    monkeypatch.setattr(cover_manager, "executable_root", lambda: exe)
    return source, bundled, exe


def test_resolves_user_cache_before_static_assets(monkeypatch, tmp_path):
    source, _bundled, _exe = _patch_roots(monkeypatch, tmp_path)
    source_hit = _write_png(source / "assets" / "jackets" / "42.png")
    cache_hit = _write_png(tmp_path / "cache" / "jackets" / "42.png")

    location = cover_manager.resolve_jacket_location(42)

    assert location is not None
    assert location.path == cache_hit
    assert location.source == "user-cache"
    assert source_hit.exists()


def test_resolves_source_and_packaged_mode_paths(monkeypatch, tmp_path):
    source, bundled, _exe = _patch_roots(monkeypatch, tmp_path)
    source_hit = _write_png(source / "assets" / "jackets" / "00042.jpg")
    bundled_hit = _write_png(bundled / "assets" / "jackets" / "77.webp")

    assert cover_manager.resolve_jacket_path(42, "https://example.test/00042.jpg") == str(source_hit)
    assert cover_manager.resolve_jacket_path(77) == str(bundled_hit)


def test_normalizes_jacket_keys_and_extensions():
    names = cover_manager.jacket_filename_candidates(8, "https://assets2.lxns.net/maimai/jacket/00008.png")

    assert "00008.png" in names
    assert "8.png" in names
    assert "0008.png" in names
    assert "8.webp" in names


def test_download_writes_only_to_user_cache(monkeypatch, tmp_path):
    source, _bundled, _exe = _patch_roots(monkeypatch, tmp_path)

    class Response:
        status_code = 200

        def raise_for_status(self):
            return None

        def iter_content(self, _chunk_size):
            yield PNG_BYTES

    monkeypatch.setattr(cover_manager.requests, "get", lambda *_args, **_kwargs: Response())

    path = Path(cover_manager.download_cover_sync(900001, "https://example.test/900001.png"))

    assert path.is_file()
    assert path.is_relative_to(tmp_path / "cache")
    assert not (source / "assets" / "jackets" / "900001.png").exists()


def test_missing_and_disabled_network_fall_back_to_none(monkeypatch, tmp_path):
    _patch_roots(monkeypatch, tmp_path)
    monkeypatch.setenv("FLUENTMAI_DISABLE_JACKET_NETWORK", "1")

    assert cover_manager.resolve_jacket_path(123456) is None
    assert cover_manager.download_cover_sync(123456) is None
