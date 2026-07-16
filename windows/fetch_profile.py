"""
fetch_profile.py — Diving-Fish player data client + SQLite offline cache.
POST /api/maimaidxprober/query/player → structured dict + local persistence.
"""

from __future__ import annotations

import json
import logging
import sqlite3
import time
from typing import Any

import requests
from fluentmai_core.runtime_paths import database_path

API_QUERY_PLAYER = "https://www.diving-fish.com/api/maimaidxprober/query/player"
HTTP_TIMEOUT = 15


def default_db_path() -> str:
    return str(database_path())


logger = logging.getLogger("fetch_profile")


# ── public API ────────────────────────────────────────────────────

def query_player(*, qq: str = "", username: str = "", b50: bool = True) -> dict[str, Any]:
    """Fetch player profile + B50 from Diving-Fish.

    Returns raw API dict, or {"error": "..."} on failure.
    Auto-saves to local SQLite on success.
    """
    payload: dict[str, Any] = {"b50": b50}
    if qq:
        payload["qq"] = qq
    elif username:
        payload["username"] = username
    else:
        return {"error": "请提供 QQ 号或用户名"}

    try:
        resp = requests.post(
            API_QUERY_PLAYER,
            json=payload,
            timeout=HTTP_TIMEOUT,
            headers={"Content-Type": "application/json"},
        )
    except requests.RequestException as exc:
        logger.error("查询玩家数据失败: %s", exc)
        return {"error": f"网络请求失败: {exc}"}

    if resp.status_code == 400:
        return {"error": "未找到该玩家，请检查 QQ/用户名是否正确"}
    if resp.status_code == 403:
        detail = ""
        try:
            detail = resp.json().get("message", "")
        except Exception:
            pass
        return {"error": detail or "该玩家已设置隐私保护"}

    try:
        data = resp.json()
    except ValueError as exc:
        logger.error("API 返回非 JSON: %s", resp.text[:200])
        return {"error": f"API 返回格式异常: {exc}"}

    # Persist to local DB on success
    try:
        save_b50_to_db(data, qq=qq, username=username)
    except Exception as exc:
        logger.warning("保存到本地数据库失败: %s", exc)

    return data


# ── SQLite helpers ────────────────────────────────────────────────

def _get_conn() -> sqlite3.Connection:
    conn = sqlite3.connect(default_db_path())
    conn.execute("PRAGMA journal_mode=WAL")
    conn.row_factory = sqlite3.Row
    return conn


def init_b50_table() -> None:
    conn = _get_conn()
    conn.execute("""
        CREATE TABLE IF NOT EXISTS user_b50_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            qq TEXT,
            username TEXT,
            nickname TEXT,
            rating INTEGER,
            plate TEXT,
            song_type TEXT,
            sort_order INTEGER,
            song_id INTEGER,
            title TEXT,
            achievements REAL,
            ds REAL,
            level TEXT,
            level_index INTEGER,
            level_label TEXT,
            type TEXT,
            rate TEXT,
            fc TEXT,
            fs TEXT,
            dxScore INTEGER,
            ra INTEGER,
            fetched_at REAL
        )
    """)
    conn.execute("""
        CREATE INDEX IF NOT EXISTS idx_b50_qq ON user_b50_records(qq)
    """)
    conn.commit()
    conn.close()


def save_b50_to_db(data: dict, *, qq: str = "", username: str = "") -> None:
    """Upsert B50 data into SQLite. Clears old records for this user first."""
    init_b50_table()

    qq = qq or ""
    username = username or data.get("username", "")
    nickname = data.get("nickname", "")
    rating = data.get("rating", 0)
    plate = data.get("plate", "")
    fetched_at = time.time()

    conn = _get_conn()

    # Remove old records for this user
    if qq:
        conn.execute("DELETE FROM user_b50_records WHERE qq = ?", (qq,))
    elif username:
        conn.execute("DELETE FROM user_b50_records WHERE username = ?", (username,))

    charts = data.get("charts", {})
    rows: list[tuple] = []
    for song_type in ("sd", "dx"):
        for idx, rec in enumerate(charts.get(song_type, [])):
            rows.append((
                qq, username, nickname, rating, plate,
                song_type, idx,
                rec.get("song_id", 0),
                rec.get("title", ""),
                rec.get("achievements", 0.0),
                rec.get("ds", 0.0),
                rec.get("level", ""),
                rec.get("level_index", 0),
                rec.get("level_label", ""),
                rec.get("type", ""),
                rec.get("rate", ""),
                rec.get("fc", ""),
                rec.get("fs", ""),
                rec.get("dxScore", 0),
                rec.get("ra", 0),
                fetched_at,
            ))

    conn.executemany("""
        INSERT INTO user_b50_records
            (qq, username, nickname, rating, plate,
             song_type, sort_order, song_id, title, achievements, ds,
             level, level_index, level_label, type, rate, fc, fs, dxScore, ra, fetched_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, rows)
    conn.commit()
    conn.close()
    logger.info("Saved %d records to local DB", len(rows))


def load_b50_from_db(qq: str = "", username: str = "") -> dict | None:
    """Load cached B50 data from SQLite. Returns API-shaped dict or None."""
    init_b50_table()
    conn = _get_conn()

    if qq:
        rows = conn.execute(
            "SELECT * FROM user_b50_records WHERE qq = ? ORDER BY song_type, sort_order",
            (qq,),
        ).fetchall()
    elif username:
        rows = conn.execute(
            "SELECT * FROM user_b50_records WHERE username = ? ORDER BY song_type, sort_order",
            (username,),
        ).fetchall()
    else:
        conn.close()
        return None

    if not rows:
        conn.close()
        return None

    first = rows[0]
    data: dict[str, Any] = {
        "username": first["username"],
        "nickname": first["nickname"],
        "rating": first["rating"],
        "plate": first["plate"],
        "additional_rating": 0,
        "charts": {"sd": [], "dx": []},
    }

    for row in rows:
        rec = {
            "song_id": row["song_id"],
            "title": row["title"],
            "achievements": row["achievements"],
            "ds": row["ds"],
            "level": row["level"],
            "level_index": row["level_index"],
            "level_label": row["level_label"],
            "type": row["type"],
            "rate": row["rate"],
            "fc": row["fc"],
            "fs": row["fs"],
            "dxScore": row["dxScore"],
            "ra": row["ra"],
        }
        st = row["song_type"]
        if st in data["charts"]:
            data["charts"][st].append(rec)

    conn.close()
    logger.info("Loaded %d cached records from local DB",
                len(data["charts"]["sd"]) + len(data["charts"]["dx"]))
    return data
