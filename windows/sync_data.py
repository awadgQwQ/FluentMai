"""
sync_data.py — 从水鱼 API 拉取舞萌 DX 曲库数据并存入本地 SQLite 数据库。

用法: python sync_data.py
"""

import json
import logging
import os
import sqlite3
import sys
import tempfile
from typing import Any

import requests

# ---------------------------------------------------------------------------
# 配置
# ---------------------------------------------------------------------------

API_URL = "https://www.diving-fish.com/api/maimaidxprober/music_data"
DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "maimai_data.db")
REQUEST_TIMEOUT = 30  # 秒

# 难度标签（按 ds / level 数组索引顺序）
DIFF_LABELS = ["basic", "advanced", "expert", "master", "remaster"]

logging.basicConfig(
    level=logging.INFO,
    format="[%(asctime)s] %(levelname)s - %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("sync_data")


# ---------------------------------------------------------------------------
# 数据拉取
# ---------------------------------------------------------------------------

def fetch_music_data() -> list[dict[str, Any]]:
    """向水鱼 API 发送 GET 请求，返回曲库 JSON 列表。"""
    logger.info("正在请求 %s ...", API_URL)
    try:
        resp = requests.get(API_URL, timeout=REQUEST_TIMEOUT)
        resp.raise_for_status()
    except requests.RequestException as exc:
        logger.error("网络请求失败: %s", exc)
        sys.exit(1)

    data: list[dict[str, Any]] = resp.json()
    if not isinstance(data, list):
        logger.error("API 返回格式异常（期望 list，收到 %s）", type(data).__name__)
        sys.exit(1)

    logger.info("成功获取 %d 首歌曲数据", len(data))
    return data


# ---------------------------------------------------------------------------
# 数据清洗
# ---------------------------------------------------------------------------

def clean_record(raw: dict[str, Any]) -> dict[str, Any]:
    """将单条 API 记录清洗为数据库行字典。"""
    ds = raw.get("ds", [])
    levels = raw.get("level", [])

    row: dict[str, Any] = {
        "id": int(raw["id"]),
        "title": raw.get("title", ""),
        "type": raw.get("type", "SD"),
    }

    for i, label in enumerate(DIFF_LABELS):
        row[f"ds_{label}"] = ds[i] if i < len(ds) else None
        row[f"level_{label}"] = str(levels[i]) if i < len(levels) else None

    return row


# ---------------------------------------------------------------------------
# 数据库操作
# ---------------------------------------------------------------------------

CREATE_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS music_data (
    id              INTEGER PRIMARY KEY,
    title           TEXT    NOT NULL,
    type            TEXT    NOT NULL,
    ds_basic        REAL,
    ds_advanced     REAL,
    ds_expert       REAL,
    ds_master       REAL,
    ds_remaster     REAL,
    level_basic     TEXT,
    level_advanced  TEXT,
    level_expert    TEXT,
    level_master    TEXT,
    level_remaster  TEXT
);
"""

INSERT_SQL = """
INSERT OR REPLACE INTO music_data (
    id, title, type,
    ds_basic, ds_advanced, ds_expert, ds_master, ds_remaster,
    level_basic, level_advanced, level_expert, level_master, level_remaster
) VALUES (
    :id, :title, :type,
    :ds_basic, :ds_advanced, :ds_expert, :ds_master, :ds_remaster,
    :level_basic, :level_advanced, :level_expert, :level_master, :level_remaster
);
"""


def sync_to_database(records: list[dict[str, Any]]) -> None:
    """将清洗后的数据写入 SQLite，使用临时文件 + 原子替换策略保证旧库安全。"""
    cleaned = [clean_record(r) for r in records]
    logger.info("清洗完成，共 %d 条记录待写入", len(cleaned))

    # 在同目录下创建临时库文件，确保 os.replace 可在同一驱动器上原子替换
    tmp_fd, tmp_path = tempfile.mkstemp(
        suffix=".db", prefix="maimai_sync_", dir=os.path.dirname(DB_PATH)
    )
    os.close(tmp_fd)

    try:
        conn = sqlite3.connect(tmp_path)
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute(CREATE_TABLE_SQL)
        conn.executemany(INSERT_SQL, cleaned)
        conn.commit()
        conn.close()
    except Exception:
        conn.close()
        os.unlink(tmp_path)
        logger.exception("写入临时数据库失败")
        sys.exit(1)

    # 原子替换：先备份旧库（如果存在），再将临时库重命名为正式库
    backup_path = DB_PATH + ".bak"
    if os.path.exists(DB_PATH):
        os.replace(DB_PATH, backup_path)

    os.replace(tmp_path, DB_PATH)

    # 替换成功后删除备份
    if os.path.exists(backup_path):
        os.unlink(backup_path)

    logger.info("数据库已更新: %s (%d 条记录)", DB_PATH, len(cleaned))


# ---------------------------------------------------------------------------
# 入口
# ---------------------------------------------------------------------------

def main() -> None:
    raw_data = fetch_music_data()
    sync_to_database(raw_data)
    logger.info("同步完成。")


if __name__ == "__main__":
    main()
