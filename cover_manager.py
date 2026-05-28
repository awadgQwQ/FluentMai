"""
cover_manager.py — Jacket image downloader with multi-level ID fallback.
Diving-Fish covers use zero-padded IDs: 11512.png, 00123.png, 01000.png.
"""

from __future__ import annotations

import logging
import os
import sys

from PyQt6.QtCore import QThread, pyqtSignal
import requests

if getattr(sys, 'frozen', False):
    BASE_DIR = os.path.dirname(sys.executable)
else:
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))

JACKETS_DIR = os.path.join(BASE_DIR, "assets", "jackets")
BASE_URL = "https://www.diving-fish.com/covers/{}.png"
HTTP_TIMEOUT = 10

logger = logging.getLogger("cover_manager")


def _ensure_dir() -> None:
    os.makedirs(JACKETS_DIR, exist_ok=True)


def cover_path(song_id: int) -> str:
    return os.path.join(JACKETS_DIR, f"{song_id}.png")


def has_cover(song_id: int) -> bool:
    return os.path.isfile(cover_path(song_id))


def _build_urls(song_id: int) -> list[str]:
    """Return fallback URL list: raw ID, 5-digit, 4-digit zero-padded."""
    s = str(song_id)
    candidates = [s]
    if len(s) < 5:
        candidates.append(s.zfill(5))
    if len(s) < 4:
        candidates.append(s.zfill(4))
    seen = set()
    urls = []
    for c in candidates:
        if c not in seen:
            seen.add(c)
            urls.append(BASE_URL.format(c))
    return urls


def download_cover_sync(song_id: int) -> str | None:
    """Blocking download with multi-ID fallback. Returns path or None."""
    path = cover_path(song_id)
    if os.path.isfile(path) and os.path.getsize(path) > 0:
        return path

    _ensure_dir()
    urls = _build_urls(song_id)

    for url in urls:
        try:
            resp = requests.get(url, timeout=HTTP_TIMEOUT, stream=True)
            if resp.status_code == 404:
                continue
            resp.raise_for_status()

            tmp = path + ".tmp"
            with open(tmp, "wb") as f:
                for chunk in resp.iter_content(8192):
                    f.write(chunk)

            os.replace(tmp, path)
            return path

        except requests.RequestException:
            continue

    logger.debug("Cover not found for song %d (tried %d URLs)", song_id, len(urls))
    if os.path.isfile(path):
        return path
    return None


class BulkCoverWorker(QThread):
    cover_done = pyqtSignal(int, str)
    all_done = pyqtSignal()

    def __init__(self, song_ids: list[int], parent=None):
        super().__init__(parent)
        self.song_ids = song_ids

    def run(self) -> None:
        _ensure_dir()
        for sid in self.song_ids:
            path = download_cover_sync(sid)
            if path:
                self.cover_done.emit(sid, path)
        self.all_done.emit()
