from __future__ import annotations

from PyQt6.QtGui import QFont
from PyQt6.QtWidgets import QApplication


FONT_FAMILY = "Microsoft YaHei UI"

PAGE_MAX_WIDTH = 1280
PAGE_MARGIN_X = 28
PAGE_MARGIN_Y = 28
PAGE_GAP = 14

TITLE_PX = 26
BODY_PX = 15
SECONDARY_PX = 13
CONTROL_HEIGHT = 42
CARD_RADIUS = 8
CARD_PADDING = 16


GLOBAL_STYLE = f"""
QWidget {{
    font-family: "{FONT_FAMILY}", "Segoe UI", sans-serif;
    font-size: {BODY_PX}px;
}}
QLineEdit, QTextEdit, QPlainTextEdit, QComboBox {{
    min-height: {CONTROL_HEIGHT}px;
    font-size: {BODY_PX}px;
}}
PushButton, PrimaryPushButton, ToolButton {{
    min-height: {CONTROL_HEIGHT}px;
    font-size: {BODY_PX}px;
}}
QComboBox QAbstractItemView {{
    background: #171b26;
    color: #eef2ff;
    border: 1px solid #485166;
    border-radius: 8px;
    outline: 0;
    padding: 6px;
    selection-background-color: #3730a3;
    selection-color: #ffffff;
}}
QComboBox QAbstractItemView::item {{
    min-height: 34px;
    padding: 7px 10px;
    background: #171b26;
    color: #eef2ff;
}}
QComboBox QAbstractItemView::item:hover {{
    background: #243044;
}}
QComboBox QAbstractItemView::item:selected {{
    background: #3730a3;
    color: #ffffff;
}}
"""


def apply_app_style(app: QApplication) -> None:
    font = QFont(FONT_FAMILY)
    font.setPointSize(10)
    app.setFont(font)
    app.setStyleSheet(GLOBAL_STYLE)
