"""
interceptor.py — mitmproxy 插件：拦截舞萌 DX 成绩页面并转储 HTML。

用法:
    mitmdump -s interceptor.py -p 8080
    mitmproxy -s interceptor.py -p 8080
"""

import os
import re
import time
from pathlib import Path

from mitmproxy import http

# ---------------------------------------------------------------------------
# 配置
# ---------------------------------------------------------------------------

# URL 匹配模式
URL_PATTERN = re.compile(r"maimai\.wahlap\.com/maimai-mobile/record", re.IGNORECASE)

# 项目根目录（interceptor.py 所在目录）
PROJECT_ROOT = Path(__file__).resolve().parent
DUMP_DIR = PROJECT_ROOT / "html_dumps"

# ANSI 颜色
GREEN_BOLD = "\033[1;32m"
CYAN = "\033[0;36m"
YELLOW_BOLD = "\033[1;33m"
RED_BOLD = "\033[1;31m"
RESET = "\033[0m"


# ---------------------------------------------------------------------------
# 辅助函数
# ---------------------------------------------------------------------------

def _ensure_dump_dir() -> None:
    """确保 html_dumps 目录存在。"""
    DUMP_DIR.mkdir(exist_ok=True)


def _extract_url_tag(url: str) -> str:
    """从 URL 中提取简短特征标识，用于文件名。

    例如:
      .../record/diff/3  →  record_diff_3
      .../record         →  record
    """
    # 取 URL path 部分，去掉域名和参数
    path = url.split("?")[0]                      # 去参数
    path = re.sub(r"^https?://[^/]+", "", path)   # 去域名
    path = path.strip("/")

    # 取最后两段路径作为特征
    parts = path.split("/")[-2:]
    tag = "_".join(parts)
    # 清洗掉非字母数字和下划线的字符
    tag = re.sub(r"[^a-zA-Z0-9_]", "_", tag)
    return tag or "unknown"


def _is_html_response(flow: http.HTTPFlow) -> bool:
    """判断响应是否为 HTML 页面。"""
    content_type = flow.response.headers.get("content-type", "")
    return "text/html" in content_type.lower()


# ---------------------------------------------------------------------------
# mitmproxy 插件入口
# ---------------------------------------------------------------------------

class MaimaiInterceptor:
    """拦截舞萌成绩页面并保存 HTML 到本地。"""

    def request(self, flow: http.HTTPFlow) -> None:
        """诊断钩子：打印所有命中 URL 模式的请求（无论最终是否保存）。"""
        url = flow.request.pretty_url
        if URL_PATTERN.search(url):
            print(
                f"{YELLOW_BOLD}"
                f"🔍 [探测] 命中舞萌 URL: {url}"
                f"{RESET}"
            )

    def response(self, flow: http.HTTPFlow) -> None:
        url = flow.request.pretty_url

        # ---- 过滤：只处理匹配的 URL ----
        if not URL_PATTERN.search(url):
            return

        # ---- 过滤：只处理 HTML 响应 ----
        if not _is_html_response(flow):
            content_type = flow.response.headers.get("content-type", "<缺失>")
            status = flow.response.status_code
            print(
                f"{RED_BOLD}"
                f"❌ [跳过] 非 HTML | HTTP {status} | "
                f"Content-Type: {content_type} | URL: {url}"
                f"{RESET}"
            )
            return

        # ---- 确保输出目录存在 ----
        _ensure_dump_dir()

        # ---- 构建文件名 ----
        tag = _extract_url_tag(url)
        timestamp = int(time.time())
        filename = f"{tag}_{timestamp}.html"
        filepath = DUMP_DIR / filename

        # ---- 写入 HTML (跳过空 body，例如 302 重定向) ----
        raw_bytes = flow.response.get_content(strict=False)
        if not raw_bytes:
            print(
                f"{YELLOW_BOLD}"
                f"⚠️ [跳过] 空响应体 | HTTP {flow.response.status_code} | {url}"
                f"{RESET}"
            )
            return
        html_content = raw_bytes.decode("utf-8", errors="replace")
        filepath.write_bytes(raw_bytes)

        # ---- 终端反馈 ----
        size_kb = len(html_content) / 1024
        print(
            f"\n{GREEN_BOLD}"
            f"✅ 成功捕获舞萌成绩页面！"
            f"{RESET}"
        )
        print(
            f"{CYAN}   → 已保存至 html_dumps/{filename}"
            f"  ({size_kb:.1f} KB){RESET}\n"
        )


# mitmproxy 加载入口
addons = [MaimaiInterceptor()]
