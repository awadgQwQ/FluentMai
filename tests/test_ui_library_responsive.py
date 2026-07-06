from __future__ import annotations

import os
from pathlib import Path
import subprocess
import sys

import pytest
from PyQt6.QtCore import QBuffer, QIODevice
from PyQt6.QtGui import QColor, QImage
from PyQt6.QtWidgets import QApplication

from fluentmai_core import database
from fluentmai_core.models import Chart, Song, normalize_title, now_ts, score_identity_key


def _png_bytes() -> bytes:
    image = QImage(2, 2, QImage.Format.Format_RGB32)
    image.fill(QColor("#8b5cf6"))
    data = QBuffer()
    data.open(QIODevice.OpenModeFlag.WriteOnly)
    image.save(data, "PNG")
    return bytes(data.data())


PNG_BYTES = _png_bytes()


@pytest.fixture(scope="session")
def qapp():
    os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
    app = QApplication.instance()
    if app is None:
        app = QApplication(sys.argv[:1])
    return app


def _write_png(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(PNG_BYTES)


def _seed_catalog(db_path: str) -> None:
    conn = database.connect(db_path)
    database.replace_catalog(
        conn,
        [
            Song(
                song_id=900001,
                title="Alpha Jacket",
                artist="Artist A",
                genre="maimai",
                version=25000,
                bpm=180,
                jacket_url="https://assets2.lxns.net/maimai/jacket/900001.png",
                provider="test",
            ),
            Song(
                song_id=900002,
                title="Broken Jacket",
                artist="Artist B",
                genre="POPS & ANIME",
                version=24000,
                bpm=160,
                jacket_url="https://assets2.lxns.net/maimai/jacket/900002.png",
                provider="test",
            ),
            Song(
                song_id=900003,
                title="Gamma Missing",
                artist="Mapper Search",
                genre="オンゲキCHUNITHM",
                version=23000,
                bpm=150,
                jacket_url="https://assets2.lxns.net/maimai/jacket/900003.png",
                provider="test",
            ),
        ],
        [
            Chart(song_id=900001, chart_type="SD", difficulty_index=3, level="13", level_value=13.3, charter="Shared Mapper", chart_version=25000, chart_version_name="DX 2025", notes_total=700),
            Chart(song_id=900001, chart_type="DX", difficulty_index=4, level="14", level_value=14.2, charter="Shared Mapper", chart_version=25000, chart_version_name="DX 2025", notes_total=900),
            Chart(song_id=900002, chart_type="DX", difficulty_index=2, level="11", level_value=11.4, charter="Broken Mapper", chart_version=24000, chart_version_name="DX 2024", notes_total=500),
            Chart(song_id=900003, chart_type="DX", difficulty_index=1, level="8+", level_value=8.8, charter="Mapper Search", chart_version=23000, chart_version_name="DX 2023", notes_total=320),
        ],
    )
    _insert_score(conn, title="Alpha Jacket", song_id=900001, chart_type="DX", difficulty_index=4, achievements=100.1234)
    conn.close()


def _insert_score(conn, *, title: str, song_id: int, chart_type: str, difficulty_index: int, achievements: float) -> None:
    identity_key = score_identity_key(
        title=title,
        song_type=chart_type,
        difficulty_index=difficulty_index,
        song_id=song_id,
    )
    timestamp = now_ts()
    conn.execute(
        """
        INSERT INTO score_records(
            identity_key, song_id, chart_type, difficulty_index, difficulty_name,
            title, title_norm, achievements, dx_score, rank, full_combo, full_sync,
            play_time, source, source_batch_id, raw_identifier, raw_fingerprint,
            level, level_value, imported_at, updated_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 3000, '', 'fc', '', NULL, 'fixture', 'batch', ?, ?, '14', 14.2, ?, ?)
        """,
        (
            identity_key,
            song_id,
            chart_type,
            difficulty_index,
            ["Basic", "Advanced", "Expert", "Master", "Re:Master"][difficulty_index],
            title,
            normalize_title(title),
            achievements,
            identity_key,
            identity_key,
            timestamp,
            timestamp,
        ),
    )
    conn.commit()


def _make_page(qapp, tmp_path, monkeypatch):
    db_path = str(tmp_path / "ui.db")
    _seed_catalog(db_path)
    monkeypatch.setenv("FLUENTMAI_DB_PATH", db_path)
    monkeypatch.setenv("FLUENTMAI_CACHE_DIR", str(tmp_path / "cache"))
    monkeypatch.setenv("FLUENTMAI_DISABLE_JACKET_NETWORK", "1")
    _write_png(tmp_path / "cache" / "jackets" / "900001.png")
    (tmp_path / "cache" / "jackets").mkdir(parents=True, exist_ok=True)
    (tmp_path / "cache" / "jackets" / "900002.png").write_bytes(b"not an image")

    from ui_library import LibraryInterface

    page = LibraryInterface()
    page._debounce.stop()
    page.refresh_for_test()
    qapp.processEvents()
    return page


@pytest.mark.parametrize("size", [(1024, 720), (1280, 800), (1440, 900), (1920, 1080), (2560, 1600)])
def test_library_layout_bounds_across_window_sizes(qapp, tmp_path, monkeypatch, size):
    page = _make_page(qapp, tmp_path, monkeypatch)
    page.resize(*size)
    page.show()
    qapp.processEvents()
    page._sync_responsive_layout()
    qapp.processEvents()

    assert page.content.width() <= 1280
    assert page.result_view.gridSize().width() <= 500
    assert page.result_view.gridSize().width() >= 390
    assert page.search_input.height() >= 40
    assert page.sort_combo.height() >= 40

    for _label, widget, _maximum in page._filter_items:
        assert widget.isVisible()
        assert widget.geometry().right() <= page.filter_panel.contentsRect().right() + 1

    if page.content.width() >= 980:
        assert page.detail_scroll.isVisible()
        assert page.result_view.width() >= 430
    else:
        assert not page.detail_scroll.isVisible()

    if page.content.width() >= 940:
        assert page.search_input.width() <= 520

    page.close()
    page.deleteLater()


def test_combo_popup_is_opaque_and_sort_updates_query(qapp, tmp_path, monkeypatch):
    page = _make_page(qapp, tmp_path, monkeypatch)
    page.resize(1280, 800)
    page.show()
    qapp.processEvents()

    combo = page.sort_combo
    combo.showPopup()
    qapp.processEvents()
    view = combo.view()

    assert combo.maxVisibleItems() == 8
    assert view.viewport().autoFillBackground()
    assert "background: #171b26" in view.styleSheet()
    assert "selection" in view.styleSheet()

    index = combo.findData("title_asc")
    combo.setCurrentIndex(index)
    combo.hidePopup()
    page.refresh_for_test()

    assert page.model.record_at(0).title == "Alpha Jacket"
    page.close()
    page.deleteLater()


def test_detail_panel_loads_valid_jacket_and_uses_placeholder_for_corrupt_file(qapp, tmp_path, monkeypatch):
    page = _make_page(qapp, tmp_path, monkeypatch)

    records = [page.model.record_at(row) for row in range(page.model.rowCount())]
    alpha_sd = next(record for record in records if record.song_id == 900001 and record.chart_type == "SD")
    alpha_dx = next(record for record in records if record.song_id == 900001 and record.chart_type == "DX")
    broken = next(record for record in records if record.song_id == 900002)

    page.detail_panel.set_record(alpha_sd)
    pixmap_a = page.detail_panel.cover_label.pixmap()
    page.detail_panel.set_record(alpha_dx)
    pixmap_b = page.detail_panel.cover_label.pixmap()

    assert pixmap_a is not None and not pixmap_a.isNull()
    assert pixmap_b is not None and not pixmap_b.isNull()
    assert alpha_dx.played
    assert "100.1234%" in page.detail_panel.fields["score"].text()

    page.detail_panel.set_record(broken)
    assert page.detail_panel.cover_label.text() == "No\nJacket"

    page.close()
    page.deleteLater()


def test_library_page_creates_under_qt_scale_factors(tmp_path):
    script = """
import os, sys
os.environ.setdefault('QT_QPA_PLATFORM', 'offscreen')
os.environ.setdefault('FLUENTMAI_DISABLE_JACKET_NETWORK', '1')
from PyQt6.QtWidgets import QApplication
from ui_library import LibraryInterface
app = QApplication(sys.argv[:1])
page = LibraryInterface()
page.resize(1024, 720)
page.show()
app.processEvents()
page._debounce.stop()
page.refresh_for_test()
page._sync_responsive_layout()
assert page.search_input.height() >= 40
assert page.sort_combo.height() >= 40
assert page.content.width() <= 1280
page.close()
"""
    for scale in ("1", "1.25", "1.5", "2"):
        env = os.environ.copy()
        env["QT_QPA_PLATFORM"] = "offscreen"
        env["QT_SCALE_FACTOR"] = scale
        env["FLUENTMAI_DB_PATH"] = str(tmp_path / f"dpi-{scale}.db")
        env["FLUENTMAI_CACHE_DIR"] = str(tmp_path / "cache")
        env["FLUENTMAI_DISABLE_JACKET_NETWORK"] = "1"
        result = subprocess.run(
            [sys.executable, "-c", script],
            cwd=Path(__file__).resolve().parents[1],
            env=env,
            text=True,
            capture_output=True,
            timeout=20,
        )
        assert result.returncode == 0, result.stderr
