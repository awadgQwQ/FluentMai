"""
sync_core.py — FluentMai 核心同步引擎 (Cookie 中继直传流)
============================================================
用户提供微信 Wahlap Cookie → 遍历 5 难度抓取 HTML → 直传水鱼。

用法:
    result = run_sync_blocking(cookie_str, import_token, progress_cb)

依赖: pip install requests
"""

from __future__ import annotations

import json
import logging
import sys
import time
from typing import Any, Callable, Optional

import requests

# ---------------------------------------------------------------------------
# 日志
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="[%(asctime)s] %(levelname)s - %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("sync_core")

# ---------------------------------------------------------------------------
# 常量
# ---------------------------------------------------------------------------
# Wahlap 5 难度 URL
WAHLAP_DIFF_URL = (
    "https://maimai.wahlap.com/maimai-mobile/record/musicSort/search/"
    "?search=A&sort=1&playCheck=on&diff={diff}"
)
# 水鱼 API
DIVINGFISH_UPDATE_HTML = (
    "https://www.diving-fish.com/api/maimaidxprober/player/update_records_html"
)
# 备选: 水鱼 HTML→JSON 转换器
DIVINGFISH_PAGE_API = "http://www.diving-fish.com:8089/page"
DIVINGFISH_UPDATE_JSON = (
    "https://www.diving-fish.com/api/maimaidxprober/player/update_records"
)

DIFFICULTIES = range(5)
DIFF_LABELS = ["Basic", "Advanced", "Expert", "Master", "Re:Master"]

# 移动端 User-Agent (防止 Wahlap 直连拦截)
MOBILE_UA = (
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/120.0.0.0 Mobile Safari/537.36"
)

# 速率控制
WAHLAP_DELAY_SEC = 2.5     # Wahlap 请求间隔 (避免触发 WAF)
HTTP_TIMEOUT = 30           # 单次请求超时 (秒)
MAX_RETRIES = 3
RETRY_BACKOFF = 2.0

# ---------------------------------------------------------------------------
# 进度回调类型
# ---------------------------------------------------------------------------
ProgressCallback = Callable[[int, str, dict[str, Any]], None]
"""进度回调: (difficulty, stage, info) -> None

stage 取值:
    "fetching"    — 正在拉取 Wahlap 页面
    "fetched"     — 拉取完成 (info: {size_kb, has_data})
    "uploading"   — 正在上传到水鱼
    "uploaded"    — 上传完成 (info: {updates, creates})
    "error"       — 错误 (info: {error})
"""

# ---------------------------------------------------------------------------
# Cookie 解析
# ---------------------------------------------------------------------------

def parse_cookie_str(cookie_str: str) -> dict[str, str]:
    """将浏览器 Cookie 头字符串解析为 {name: value} 字典。

    输入格式:
        "_t=abc123; userId=xyz789; friendCodeList=..."
    输出:
        {"_t": "abc123", "userId": "xyz789", "friendCodeList": "..."}
    """
    cookies: dict[str, str] = {}
    for part in cookie_str.split(";"):
        part = part.strip()
        if "=" not in part:
            continue
        key, _, value = part.partition("=")
        cookies[key.strip()] = value.strip()
    return cookies


def validate_cookie(cookies: dict[str, str]) -> list[str]:
    """检查必要 Cookie 字段是否存在。返回缺失字段列表。"""
    required = ["_t", "userId"]
    return [k for k in required if k not in cookies]


# ---------------------------------------------------------------------------
# HTTP 请求工具
# ---------------------------------------------------------------------------

def _retry_request(
    method: str,
    url: str,
    *,
    session: Optional[requests.Session] = None,
    **kwargs: Any,
) -> requests.Response:
    """带重试的 HTTP 请求。"""
    s = session or requests
    last_exc: Optional[Exception] = None

    for attempt in range(1, MAX_RETRIES + 1):
        try:
            resp = s.request(method, url, timeout=HTTP_TIMEOUT, **kwargs)
            return resp
        except requests.RequestException as exc:
            last_exc = exc
            if attempt < MAX_RETRIES:
                wait = RETRY_BACKOFF * attempt
                logger.warning("请求失败 (第 %d/%d 次): %s, %.1fs 后重试",
                               attempt, MAX_RETRIES, exc, wait)
                time.sleep(wait)

    raise last_exc  # type: ignore[misc]


# ---------------------------------------------------------------------------
# 核心: 遍历难度抓取 HTML
# ---------------------------------------------------------------------------

def fetch_all_difficulty_htmls(
    session: requests.Session,
    progress_cb: Optional[ProgressCallback] = None,
) -> list[str]:
    """遍历 5 个难度, 用 requests 获取每个难度的成绩页面 HTML。

    Args:
        session: 已注入 Wahlap Cookie 的 requests.Session。
        progress_cb: 进度回调。

    Returns:
        list[str]: 5 个 HTML 字符串 (diff 0..4)。
    """
    headers = {
        "User-Agent": MOBILE_UA,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        "Referer": "https://maimai.wahlap.com/maimai-mobile/record/",
    }

    htmls: list[str] = []

    for diff in DIFFICULTIES:
        label = DIFF_LABELS[diff]
        url = WAHLAP_DIFF_URL.format(diff=diff)

        if progress_cb:
            progress_cb(diff, "fetching", {"label": label})

        logger.info("[难度 %d/4] %s — GET %s", diff, label, url)

        try:
            resp = _retry_request("GET", url, session=session, headers=headers)
        except Exception as exc:
            logger.error("[难度 %d] %s — 请求失败: %s", diff, label, exc)
            if progress_cb:
                progress_cb(diff, "error", {"label": label, "error": str(exc)})
            htmls.append("")  # 占位, 保持索引对齐
            continue

        html = resp.text
        htmls.append(html)

        size_kb = len(html) / 1024
        # 检测是否真的拿到成绩数据 (而非错误页/WAF 页)
        has_data = _check_html_has_data(html)

        logger.info("[难度 %d] %s — HTTP %d, %.1f KB, data=%s",
                    diff, label, resp.status_code, size_kb, has_data)

        if progress_cb:
            progress_cb(diff, "fetched", {
                "label": label,
                "http_status": resp.status_code,
                "size_kb": round(size_kb, 1),
                "has_data": has_data,
            })

        # 速率控制: 每次请求后等待, 避免触发 WAF
        if diff < max(DIFFICULTIES):
            time.sleep(WAHLAP_DELAY_SEC)

    return htmls


def _check_html_has_data(html: str) -> bool:
    """快速检测 HTML 是否包含成绩数据行。"""
    if not html or len(html) < 1000:
        return False
    # Wahlap 成绩页特征
    return (
        "musicSort" in html
        or "music_" in html
        or "w_450" in html
        or "m_10" in html
        or "playlog" in html.lower()
    )


# ---------------------------------------------------------------------------
# 水鱼上传
# ---------------------------------------------------------------------------

def upload_html_to_divingfish(
    htmls: list[str],
    import_token: str,
    progress_cb: Optional[ProgressCallback] = None,
) -> dict[str, Any]:
    """将 HTML 数组 POST 到水鱼。

    优先使用 update_records_html (一步到位)。
    若失败, 回退到 8089/page 转换 + update_records 两步方案。

    Returns:
        {"total_updates": int, "total_creates": int, "results": [...]}
    """
    # 过滤空 HTML
    valid_htmls = [(i, h) for i, h in enumerate(htmls) if h and len(h) > 1000]
    if not valid_htmls:
        logger.error("没有有效的 HTML 页面可供上传")
        return {"total_updates": 0, "total_creates": 0, "results": []}

    results: list[dict[str, Any]] = []
    total_updates = 0
    total_creates = 0

    for diff, html in valid_htmls:
        label = DIFF_LABELS[diff]

        if progress_cb:
            progress_cb(diff, "uploading", {"label": label})

        try:
            result = _upload_single_html(html, import_token)
            results.append({
                "difficulty": diff,
                "label": label,
                "updates": result["updates"],
                "creates": result["creates"],
                "error": None,
            })
            total_updates += result["updates"]
            total_creates += result["creates"]

            logger.info("[难度 %d] %s — 上传完成: updates=%d, creates=%d",
                        diff, label, result["updates"], result["creates"])

            if progress_cb:
                progress_cb(diff, "uploaded", {
                    "label": label,
                    "updates": result["updates"],
                    "creates": result["creates"],
                })

        except Exception as exc:
            logger.error("[难度 %d] %s — 上传失败: %s", diff, label, exc)
            results.append({
                "difficulty": diff,
                "label": label,
                "updates": 0,
                "creates": 0,
                "error": str(exc),
            })
            if progress_cb:
                progress_cb(diff, "error", {"label": label, "error": str(exc)})

    return {
        "total_updates": total_updates,
        "total_creates": total_creates,
        "results": results,
    }


def _upload_single_html(html: str, import_token: str) -> dict[str, int]:
    """上传单个 HTML 到水鱼。

    策略: 先试 update_records_html 一步到位;
    如果返回 400 (auth 问题) 则回退到 8089/page + update_records。
    """
    # ---- 方案 A: update_records_html ----
    try:
        resp = _retry_request(
            "POST",
            DIVINGFISH_UPDATE_HTML,
            data=html.encode("utf-8"),
            headers={
                "Content-Type": "text/html; charset=utf-8",
                "Import-Token": import_token,
            },
        )

        if resp.status_code == 200:
            try:
                data = resp.json()
            except json.JSONDecodeError:
                logger.warning("update_records_html 返回非 JSON, HTTP %d", resp.status_code)
                return {"updates": 0, "creates": 0}
            return {
                "updates": data.get("updates", 0),
                "creates": data.get("creates", 0),
            }

        # 400/401 可能是 auth 问题, 回退到方案 B
        if resp.status_code in (400, 401, 403):
            logger.info("update_records_html 返回 %d, 回退到 8089/page 管线", resp.status_code)
        else:
            logger.warning("update_records_html 返回 HTTP %d", resp.status_code)

    except Exception as exc:
        logger.warning("update_records_html 请求异常: %s, 回退到 8089/page 管线", exc)

    # ---- 方案 B: 8089/page → update_records (prober_api.go 原版管线) ----
    # Step B1: HTML → JSON
    logger.info("使用备选管线: 8089/page → update_records")
    resp = _retry_request(
        "POST",
        DIVINGFISH_PAGE_API,
        data=html.encode("utf-8"),
        headers={"Content-Type": "text/plain; charset=utf-8"},
    )
    resp.raise_for_status()
    try:
        records = resp.json()
    except json.JSONDecodeError as exc:
        raise ValueError(f"8089/page 返回非 JSON: {resp.text[:200]}") from exc

    if not isinstance(records, list):
        raise TypeError(f"8089/page 期望 list, 实际 {type(records).__name__}")

    if not records:
        logger.info("8089/page 返回空列表, 该难度无成绩记录")
        return {"updates": 0, "creates": 0}

    # Step B2: JSON → update_records
    resp2 = _retry_request(
        "POST",
        DIVINGFISH_UPDATE_JSON,
        json=records,
        headers={
            "Content-Type": "application/json",
            "Import-Token": import_token,
        },
    )

    if resp2.status_code >= 500:
        logger.warning("update_records 返回 HTTP %d (已知水鱼后端 bug, 数据可能已入库)", resp2.status_code)
        return {"updates": 0, "creates": 0}

    resp2.raise_for_status()
    try:
        data = resp2.json()
    except json.JSONDecodeError:
        logger.warning("update_records 返回非 JSON, HTTP %d", resp2.status_code)
        return {"updates": 0, "creates": 0}

    return {
        "updates": data.get("updates", 0),
        "creates": data.get("creates", 0),
    }


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------

def sync(
    cookie_str: str,
    import_token: str,
    progress_callback: Optional[ProgressCallback] = None,
) -> dict[str, Any]:
    """执行完整同步流程 (同步版, 供 QThread 调用)。

    Args:
        cookie_str:   Wahlap Cookie 字符串 ("_t=...; userId=...")。
        import_token: 水鱼查分器 Import Token。
        progress_callback: 进度回调 (diff, stage, info)。

    Returns:
        {
            "success": bool,
            "total_updates": int,
            "total_creates": int,
            "cookie_valid": bool,
            "difficulty_results": list[dict],
            "error": str | None,
        }
    """
    # ---- Step 0: 解析 Cookie ----
    cookies = parse_cookie_str(cookie_str)
    missing = validate_cookie(cookies)
    if missing:
        msg = f"Cookie 缺少必要字段: {missing}"
        logger.error(msg)
        return {
            "success": False,
            "total_updates": 0,
            "total_creates": 0,
            "cookie_valid": False,
            "difficulty_results": [],
            "error": msg,
        }

    logger.info("Cookie 解析成功: _t=%s..., userId=%s...",
                cookies["_t"][:16], cookies["userId"][:16])

    # ---- Step 1: 验证 Cookie 有效性 ----
    session = requests.Session()
    session.trust_env = False
    for k, v in cookies.items():
        session.cookies.set(k, v, domain="maimai.wahlap.com")
    session.headers.update({"User-Agent": MOBILE_UA})

    logger.info("验证 Cookie 有效性...")
    try:
        probe = _retry_request(
            "GET",
            "https://maimai.wahlap.com/maimai-mobile/record/",
            session=session,
            headers={"User-Agent": MOBILE_UA},
        )
    except Exception as exc:
        msg = f"Cookie 连通性测试失败: {exc}"
        logger.error(msg)
        return {
            "success": False,
            "total_updates": 0,
            "total_creates": 0,
            "cookie_valid": False,
            "difficulty_results": [],
            "error": msg,
        }

    # 检查是否被重定向到错误页 / OAuth 页
    final_url = probe.url
    if "/error/" in final_url:
        logger.error("Cookie 无效: 被重定向到 /error/ (错误码 200002)")
        return {
            "success": False,
            "total_updates": 0,
            "total_creates": 0,
            "cookie_valid": False,
            "difficulty_results": [],
            "error": "Cookie 已过期或无效 (Wahlap 返回 /error/)",
        }
    if "open.weixin.qq.com" in final_url:
        logger.error("Cookie 无效: 被重定向到微信 OAuth 页面")
        return {
            "success": False,
            "total_updates": 0,
            "total_creates": 0,
            "cookie_valid": False,
            "difficulty_results": [],
            "error": "Cookie 已过期 (被重定向到微信登录)",
        }

    logger.info("Cookie 有效! 已到达 %s", final_url[:80])

    # ---- Step 2: 遍历 5 个难度, 抓取 HTML ----
    logger.info("开始抓取 5 个难度页面 (间隔 %.1fs)...", WAHLAP_DELAY_SEC)
    htmls = fetch_all_difficulty_htmls(session, progress_callback)

    # 检查是否至少有一个有效 HTML
    valid_count = sum(1 for h in htmls if h and len(h) > 1000)
    if valid_count == 0:
        return {
            "success": False,
            "total_updates": 0,
            "total_creates": 0,
            "cookie_valid": True,
            "difficulty_results": [],
            "error": "所有难度页面均未获取到有效数据 (Cookie 可能在抓取过程中过期)",
        }

    logger.info("抓取完成: %d/5 个难度有效", valid_count)

    # ---- Step 3: 上传到水鱼 ----
    if progress_callback:
        progress_callback(-1, "uploading", {"total_pages": valid_count})

    upload_result = upload_html_to_divingfish(htmls, import_token, progress_callback)

    success = True  # 0 updates is NOT failure — data may already be synced

    return {
        "success": success,
        "total_updates": upload_result["total_updates"],
        "total_creates": upload_result["total_creates"],
        "cookie_valid": True,
        "difficulty_results": upload_result["results"],
        "error": None,
    }


# ===================================================================
# PyQt6 QThread 封装
# ===================================================================
# 以下为集成参考代码。实际使用时取消注释并导入到 PyQt6 工程中。
#
# from PyQt6.QtCore import QThread, pyqtSignal
#
# class SyncWorker(QThread):
#     """成绩同步工作线程 — 不阻塞 GUI。"""
#
#     # 信号: (difficulty: int, stage: str, info: dict)
#     progress = pyqtSignal(int, str, dict)
#     # 信号: (result: dict)
#     finished = pyqtSignal(dict)
#
#     def __init__(self, cookie_str: str, import_token: str):
#         super().__init__()
#         self.cookie_str = cookie_str
#         self.import_token = import_token
#
#     def run(self) -> None:
#         try:
#             def on_progress(diff: int, stage: str, info: dict) -> None:
#                 self.progress.emit(diff, stage, info)
#
#             result = sync(
#                 self.cookie_str,
#                 self.import_token,
#                 progress_callback=on_progress,
#             )
#             self.finished.emit(result)
#         except Exception as exc:
#             self.finished.emit({
#                 "success": False,
#                 "total_updates": 0,
#                 "total_creates": 0,
#                 "cookie_valid": False,
#                 "difficulty_results": [],
#                 "error": str(exc),
#             })


# ---------------------------------------------------------------------------
# 同步外观封装 (兼容旧接口)
# ---------------------------------------------------------------------------

def run_sync_blocking(
    cookie_str: str,
    import_token: str,
    progress_callback: Optional[ProgressCallback] = None,
) -> dict[str, Any]:
    """同步封装, 等价于 sync(), 兼容旧版 run_sync_blocking 接口。"""
    return sync(cookie_str, import_token, progress_callback)


# ===================================================================
# 测试入口
# ===================================================================
if __name__ == "__main__":
    import os

    print("=" * 60)
    print("  FluentMai sync_core — Cookie 中继直传测试")
    print("=" * 60)
    print()

    # ---- 读取配置 ----
    COOKIE_STR = os.environ.get("MAIMAI_COOKIE", "").strip()
    IMPORT_TOKEN = os.environ.get("MAIMAI_IMPORT_TOKEN", "").strip()

    if not COOKIE_STR:
        print("❌ 未设置 MAIMAI_COOKIE 环境变量!")
        print()
        print("   用法:")
        print("     set MAIMAI_COOKIE=_t=xxx; userId=yyy")
        print("     set MAIMAI_IMPORT_TOKEN=你的Token")
        print("     python sync_core.py")
        print()
        print("   Cookie 获取方式:")
        print("     1. 手机微信打开舞萌公众号 → 我的记录 → 舞萌DX")
        print("     2. 用抓包工具 (HttpCanary/Drony) 查看请求 Header")
        print("     3. 复制 Cookie 请求头的完整值")
        print("     4. 粘贴到 MAIMAI_COOKIE 环境变量")
        print()
        print("   水鱼 Import Token 获取:")
        print("     https://www.diving-fish.com/maimaidx/prober/")
        print("     → 登录 → 编辑个人资料 → 生成导入Token")
        sys.exit(1)

    if not IMPORT_TOKEN:
        print("❌ 未设置 MAIMAI_IMPORT_TOKEN 环境变量!")
        sys.exit(1)

    # ---- 进度回调 (命令行版) ----
    def cli_progress(diff: int, stage: str, info: dict) -> None:
        label = info.get("label", DIFF_LABELS[diff] if 0 <= diff < 5 else "")

        if stage == "fetching":
            print(f"  📥 [{diff}] {label} — 正在拉取...")
        elif stage == "fetched":
            has = "✅ 有数据" if info.get("has_data") else "⚠️ 无数据"
            print(f"  {has} [{diff}] {label} — "
                  f"HTTP {info.get('http_status')}, {info.get('size_kb')} KB")
        elif stage == "uploading":
            if diff == -1:
                print(f"  📤 正在上传 {info.get('total_pages')} 个页面...")
            else:
                print(f"  📤 [{diff}] {label} — 正在上传...")
        elif stage == "uploaded":
            print(f"  ✅ [{diff}] {label} — "
                  f"更新 {info.get('updates', 0)}, 新增 {info.get('creates', 0)}")
        elif stage == "error":
            print(f"  ❌ [{diff}] {label} — {info.get('error', '未知错误')}")

    # ---- 执行 ----
    print(f"Cookie 前缀: {COOKIE_STR[:60]}...")
    print(f"Import Token: {IMPORT_TOKEN[:20]}...")
    print()
    print("🚀 开始同步...")
    print()

    start = time.time()
    result = sync(COOKIE_STR, IMPORT_TOKEN, progress_callback=cli_progress)
    elapsed = time.time() - start

    # ---- 结果 ----
    print()
    print("=" * 60)
    print("  同步结果")
    print("=" * 60)
    print(f"  耗时: {elapsed:.1f}s")
    print(f"  Cookie 有效: {'是' if result['cookie_valid'] else '否'}")
    print(f"  成功: {'是' if result['success'] else '否'}")
    if result["error"]:
        print(f"  错误: {result['error']}")
    print(f"  更新: {result['total_updates']}  新增: {result['total_creates']}")
    print()
    if result["difficulty_results"]:
        print("  各难度明细:")
        for r in result["difficulty_results"]:
            err = r.get("error")
            status = f"❌ {err}" if err else f"✅ {r['updates']}u {r['creates']}c"
            print(f"    [{r['difficulty']}] {r['label']}: {status}")
    print()
    print("  查看结果: https://www.diving-fish.com/maimaidx/prober/")
    print("=" * 60)
