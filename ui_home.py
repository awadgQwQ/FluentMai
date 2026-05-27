from PyQt6.QtCore import Qt, pyqtSignal, QThread
from PyQt6.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout,
                              QTextEdit, QLineEdit)
from qfluentwidgets import (SubtitleLabel, BodyLabel, CardWidget,
                            PushButton, FluentIcon as FIF, InfoBar,
                            InfoBarPosition, IndeterminateProgressRing)

from i18n import i18n
from sync_core import sync, DIFF_LABELS


class SyncWorker(QThread):
    """成绩同步工作线程 — 不阻塞 GUI。"""
    progress = pyqtSignal(int, str, dict)
    finished = pyqtSignal(dict)

    def __init__(self, cookie_str: str, import_token: str):
        super().__init__()
        self.cookie_str = cookie_str
        self.import_token = import_token

    def run(self) -> None:
        def on_progress(diff: int, stage: str, info: dict) -> None:
            self.progress.emit(diff, stage, info)

        result = sync(self.cookie_str, self.import_token,
                      progress_callback=on_progress)
        self.finished.emit(result)


class HomeInterface(QWidget):
    """成绩同步页面 — Cookie 输入 + Token 输入 + 同步按钮 + 进度显示。"""

    def __init__(self, parent=None):
        super().__init__(parent=parent)
        self.setObjectName("HomeInterface")
        self._worker: SyncWorker | None = None

        self.layout = QVBoxLayout(self)
        self.layout.setContentsMargins(24, 32, 24, 24)
        self.layout.setSpacing(16)

        # ---- 标题 ----
        title = SubtitleLabel(i18n.tr("home_title"))
        title.setStyleSheet("font-size: 26px; font-weight: bold;")
        self.layout.addWidget(title)

        desc = BodyLabel(i18n.tr("home_desc"))
        desc.setStyleSheet("color: #a0a0a0; font-size: 14px; margin-bottom: 8px;")
        self.layout.addWidget(desc)

        # ---- Cookie 输入 ----
        cookie_card, self.cookie_input = self._make_input_card(
            i18n.tr("home_cookie_label"),
            i18n.tr("home_cookie_placeholder"),
            i18n.tr("home_cookie_hint"),
            password_mode=True,
        )
        self.layout.addWidget(cookie_card)

        # ---- Token 输入 ----
        token_card, self.token_input = self._make_input_card(
            i18n.tr("home_token_label"),
            i18n.tr("home_token_placeholder"),
            hint="", password_mode=True,
        )
        self.layout.addWidget(token_card)

        # ---- 按钮行 ----
        btn_layout = QHBoxLayout()
        btn_layout.setSpacing(12)

        self.sync_btn = PushButton(FIF.SYNC, i18n.tr("home_sync_btn"))
        self.sync_btn.setMinimumHeight(40)
        self.sync_btn.clicked.connect(self._on_sync_clicked)
        btn_layout.addWidget(self.sync_btn)

        self.spinner = IndeterminateProgressRing(self)
        self.spinner.setFixedSize(24, 24)
        self.spinner.hide()
        btn_layout.addWidget(self.spinner)

        btn_layout.addStretch(1)
        self.layout.addLayout(btn_layout)

        # ---- 状态标签 ----
        self.status_label = BodyLabel(i18n.tr("home_status_ready"))
        self.status_label.setStyleSheet("color: #a0a0a0; font-size: 13px;")
        self.layout.addWidget(self.status_label)

        # ---- 进度日志 ----
        self.log_output = QTextEdit()
        self.log_output.setReadOnly(True)
        self.log_output.setMaximumHeight(220)
        self.log_output.setStyleSheet("""
            QTextEdit {
                background: #1a1a2e;
                color: #c0c0c0;
                border: 1px solid #333;
                border-radius: 6px;
                font-size: 12px;
                padding: 8px;
            }
        """)
        self.layout.addWidget(self.log_output)

        self.layout.addStretch(1)

    # ------------------------------------------------------------------
    def _make_input_card(self, label: str, placeholder: str, hint: str,
                         password_mode: bool = False):
        card = CardWidget()
        vbox = QVBoxLayout(card)
        vbox.setContentsMargins(20, 16, 20, 16)
        vbox.setSpacing(8)

        title_lbl = BodyLabel(label)
        title_lbl.setStyleSheet("font-size: 14px;")
        vbox.addWidget(title_lbl)

        input_widget = QLineEdit()
        input_widget.setPlaceholderText(placeholder)
        input_widget.setMinimumHeight(36)
        input_widget.setStyleSheet("""
            QLineEdit {
                background: #12121e;
                border: 1px solid #333;
                border-radius: 6px;
                padding: 6px 12px;
                color: #e0e0e0;
                font-size: 13px;
            }
            QLineEdit:focus { border-color: #51bcf3; }
        """)
        if password_mode:
            input_widget.setEchoMode(QLineEdit.EchoMode.Password)
        vbox.addWidget(input_widget)

        if hint:
            hint_lbl = BodyLabel(hint)
            hint_lbl.setStyleSheet("color: #707070; font-size: 11px;")
            vbox.addWidget(hint_lbl)

        return card, input_widget

    # ------------------------------------------------------------------
    def _on_sync_clicked(self):
        cookie_str = self.cookie_input.text().strip()
        import_token = self.token_input.text().strip()

        if not cookie_str or not import_token:
            InfoBar.warning("", "请填写 Cookie 和 Import Token",
                            duration=3000, parent=self)
            return

        self.sync_btn.setEnabled(False)
        self.spinner.show()
        self.log_output.clear()
        self.status_label.setText(i18n.tr("home_status_validating"))
        self.status_label.setStyleSheet("color: #f0c040; font-size: 13px;")

        self._worker = SyncWorker(cookie_str, import_token)
        self._worker.progress.connect(self._on_progress)
        self._worker.finished.connect(self._on_finished)
        self._worker.start()

    # ------------------------------------------------------------------
    def _on_progress(self, diff: int, stage: str, info: dict):
        label = info.get("label", DIFF_LABELS[diff] if 0 <= diff < 5 else "")

        if stage == "fetching":
            self._log(f"📥 [{label}] 正在拉取页面...")
        elif stage == "fetched":
            has = "✅" if info.get("has_data") else "⚠️"
            self._log(f"  {has} [{label}] HTTP {info.get('http_status')}, "
                      f"{info.get('size_kb', 0)} KB")
        elif stage == "uploading":
            self._log(f"📤 [{label}] 上传中...")
        elif stage == "uploaded":
            self._log(f"  ✅ [{label}] 更新 {info.get('updates', 0)}, "
                      f"新增 {info.get('creates', 0)}")
        elif stage == "error":
            self._log(f"  ❌ [{label}] {info.get('error', '未知错误')}")

    # ------------------------------------------------------------------
    def _on_finished(self, result: dict):
        self.sync_btn.setEnabled(True)
        self.spinner.hide()

        if result.get("success"):
            msg = i18n.tr("home_status_done").format(
                updates=result["total_updates"],
                creates=result["total_creates"],
            )
            self.status_label.setText(msg)
            self.status_label.setStyleSheet("color: #4caf50; font-size: 13px;")
            self._log("─" * 40)
            self._log(f"🎉 同步完成! 更新 {result['total_updates']}, "
                      f"新增 {result['total_creates']}")
            InfoBar.success("同步完成",
                            f"更新 {result['total_updates']} 条, "
                            f"新增 {result['total_creates']} 条",
                            duration=5000,
                            position=InfoBarPosition.TOP_RIGHT,
                            parent=self)
        else:
            err = result.get("error", "未知错误")
            msg = i18n.tr("home_status_failed").format(error=err)
            self.status_label.setText(msg)
            self.status_label.setStyleSheet("color: #ff5252; font-size: 13px;")
            self._log(f"❌ {err}")

            if result.get("cookie_valid") is False:
                InfoBar.error("Cookie 无效",
                              "Cookie 已过期或格式错误，请重新获取",
                              duration=8000,
                              position=InfoBarPosition.TOP_RIGHT,
                              parent=self)

    # ------------------------------------------------------------------
    def _log(self, text: str):
        self.log_output.append(text)
        # 自动滚到底部
        scrollbar = self.log_output.verticalScrollBar()
        scrollbar.setValue(scrollbar.maximum())
