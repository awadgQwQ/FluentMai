from __future__ import annotations

from PyQt6.QtCore import QThread, pyqtSignal
from PyQt6.QtWidgets import QWidget, QVBoxLayout, QHBoxLayout, QTextEdit, QLineEdit
from qfluentwidgets import (
    BodyLabel,
    CardWidget,
    FluentIcon as FIF,
    IndeterminateProgressRing,
    InfoBar,
    InfoBarPosition,
    PushButton,
    SubtitleLabel,
)

from fluentmai_core.catalog import sync_diving_fish_catalog, sync_lxns_catalog
from fluentmai_core.import_pipeline import import_parsed_records
from fluentmai_core.models import ImportSummary
from fluentmai_core.privacy import redactor
from fluentmai_core.providers import DivingFishProvider, LxnsProvider
from fluentmai_core.wahlap import WahlapClient, parse_wahlap_pages


class ImportWorker(QThread):
    progress = pyqtSignal(str)
    finished = pyqtSignal(dict)

    def __init__(self, action: str, payload: dict):
        super().__init__()
        self.action = action
        self.payload = payload

    def run(self) -> None:
        try:
            if self.action == "wahlap":
                self.finished.emit(self._run_wahlap())
            elif self.action == "diving-fish":
                self.finished.emit(self._run_diving_fish())
            elif self.action == "lxns":
                self.finished.emit(self._run_lxns())
            elif self.action == "catalog":
                self.finished.emit(self._run_catalog())
            else:
                raise ValueError(f"Unknown action: {self.action}")
        except Exception as exc:
            self.finished.emit({"success": False, "error": redactor.redact(exc)})

    def _run_wahlap(self) -> dict:
        credential = self.payload["credential"].strip()
        self.progress.emit("Connecting to Wahlap...")
        client = WahlapClient()
        if credential.startswith(("http://", "https://")):
            client.login_with_auth_url(credential)
            self.progress.emit("Auth URL accepted. Fetching five difficulty pages...")
        else:
            client.use_cookie_input(credential)
            self.progress.emit("Cookie accepted. Fetching five difficulty pages...")

        pages = client.fetch_score_pages(
            progress=lambda diff, stage, info: self.progress.emit(
                f"{info.get('label', diff)}: {stage}, {info.get('size_kb', '')} KB"
            )
        )
        records = parse_wahlap_pages(pages, db_path=self.payload.get("db_path"))
        self.progress.emit(f"Parsed {len(records)} Wahlap records. Importing locally...")
        summary = import_parsed_records(
            records,
            source="wahlap",
            db_path=self.payload.get("db_path"),
            fetched_count=sum(1 for _, html in pages if html.strip()),
        )
        return {"success": True, "summary": summary.as_dict()}

    def _run_diving_fish(self) -> dict:
        token = self.payload["token"].strip()
        self.progress.emit("Fetching full Diving-Fish records...")
        records = DivingFishProvider().fetch_parsed_scores(token)
        self.progress.emit(f"Fetched {len(records)} Diving-Fish records. Importing locally...")
        summary = import_parsed_records(
            records,
            source="diving-fish",
            db_path=self.payload.get("db_path"),
            fetched_count=len(records),
        )
        return {"success": True, "summary": summary.as_dict()}

    def _run_lxns(self) -> dict:
        token = self.payload["token"].strip()
        self.progress.emit("Fetching full LXNS records...")
        records = LxnsProvider().fetch_parsed_scores(token)
        self.progress.emit(f"Fetched {len(records)} LXNS records. Importing locally...")
        summary = import_parsed_records(
            records,
            source="lxns",
            db_path=self.payload.get("db_path"),
            fetched_count=len(records),
        )
        return {"success": True, "summary": summary.as_dict()}

    def _run_catalog(self) -> dict:
        errors: list[str] = []
        self.progress.emit("Refreshing LXNS song and chart catalog...")
        try:
            count = sync_lxns_catalog(self.payload.get("db_path"))
            message = f"LXNS {count}"
        except Exception as exc:
            errors.append(f"LXNS: {redactor.redact(exc)}")
            self.progress.emit("Refreshing Diving-Fish fallback catalog...")
            try:
                count = sync_diving_fish_catalog(self.payload.get("db_path"), replace=True)
                message = f"Diving-Fish fallback {count}"
            except Exception as fallback_exc:
                errors.append(f"Diving-Fish: {redactor.redact(fallback_exc)}")
                raise RuntimeError("; ".join(errors) or "Catalog refresh failed.")
        return {
            "success": True,
            "summary": {
                "source": "catalog",
                "inserted": 0,
                "updated": 0,
                "skipped_duplicate": 0,
                "quarantined": 0,
                "rejected": 0,
                "failed": len(errors),
                "message": "; ".join([message] + errors),
            },
        }


class HomeInterface(QWidget):
    """Import center for local Wahlap, Diving-Fish, and LXNS data."""

    def __init__(self, parent=None):
        super().__init__(parent=parent)
        self.setObjectName("HomeInterface")
        self._worker: ImportWorker | None = None

        self.layout = QVBoxLayout(self)
        self.layout.setContentsMargins(24, 32, 24, 24)
        self.layout.setSpacing(16)

        title = SubtitleLabel("导入中心")
        title.setStyleSheet("font-size: 26px; font-weight: bold;")
        self.layout.addWidget(title)

        desc = BodyLabel("从华立、水鱼或落雪导入真实成绩；异常数据会进入 quarantine，不会污染本地成绩表。")
        desc.setStyleSheet("color: #a0a0a0; font-size: 14px; margin-bottom: 8px;")
        self.layout.addWidget(desc)

        self.wahlap_input = QTextEdit()
        self._add_text_card(
            "华立导入",
            "粘贴华立授权 URL、Cookie，或 Reqable 请求头。不会写入日志。",
            self.wahlap_input,
        )
        self.wahlap_btn = PushButton(FIF.SYNC, "获取并导入华立成绩")
        self.wahlap_btn.clicked.connect(self._on_wahlap)
        self.layout.addWidget(self.wahlap_btn)

        self.df_token = QLineEdit()
        self.df_token.setEchoMode(QLineEdit.EchoMode.Password)
        self._add_line_card("水鱼导入", "Import Token，用于拉取完整个人成绩。", self.df_token)
        self.df_btn = PushButton(FIF.DOWNLOAD, "从水鱼导入")
        self.df_btn.clicked.connect(self._on_diving_fish)
        self.layout.addWidget(self.df_btn)

        self.lxns_token = QLineEdit()
        self.lxns_token.setEchoMode(QLineEdit.EchoMode.Password)
        self._add_line_card("落雪导入", "个人 API 密钥，对应请求头 X-User-Token。", self.lxns_token)
        self.lxns_btn = PushButton(FIF.DOWNLOAD, "从落雪导入")
        self.lxns_btn.clicked.connect(self._on_lxns)
        self.layout.addWidget(self.lxns_btn)

        tools = QHBoxLayout()
        self.catalog_btn = PushButton(FIF.UPDATE, "刷新歌曲与谱面缓存")
        self.catalog_btn.clicked.connect(self._on_catalog)
        tools.addWidget(self.catalog_btn)
        self.spinner = IndeterminateProgressRing(self)
        self.spinner.setFixedSize(24, 24)
        self.spinner.hide()
        tools.addWidget(self.spinner)
        tools.addStretch(1)
        self.layout.addLayout(tools)

        self.status_label = BodyLabel("就绪")
        self.status_label.setStyleSheet("color: #a0a0a0; font-size: 13px;")
        self.layout.addWidget(self.status_label)

        self.log_output = QTextEdit()
        self.log_output.setReadOnly(True)
        self.log_output.setMaximumHeight(190)
        self.log_output.setStyleSheet(
            """
            QTextEdit {
                background: #151824;
                color: #d8dee9;
                border: 1px solid #303545;
                border-radius: 6px;
                font-size: 12px;
                padding: 8px;
            }
            """
        )
        self.layout.addWidget(self.log_output)
        self.layout.addStretch(1)

    def _add_text_card(self, title: str, hint: str, input_widget: QTextEdit) -> None:
        card = CardWidget()
        vbox = QVBoxLayout(card)
        vbox.setContentsMargins(20, 16, 20, 16)
        vbox.setSpacing(8)
        vbox.addWidget(BodyLabel(title))
        hint_label = BodyLabel(hint)
        hint_label.setStyleSheet("color: #a0a0a0; font-size: 12px;")
        hint_label.setWordWrap(True)
        vbox.addWidget(hint_label)
        input_widget.setMinimumHeight(88)
        input_widget.setPlaceholderText("https://tgk-wcaime.wahlap.com/... 或 _t=...; userId=...")
        vbox.addWidget(input_widget)
        self.layout.addWidget(card)

    def _add_line_card(self, title: str, hint: str, input_widget: QLineEdit) -> None:
        card = CardWidget()
        vbox = QVBoxLayout(card)
        vbox.setContentsMargins(20, 16, 20, 16)
        vbox.setSpacing(8)
        vbox.addWidget(BodyLabel(title))
        hint_label = BodyLabel(hint)
        hint_label.setStyleSheet("color: #a0a0a0; font-size: 12px;")
        vbox.addWidget(hint_label)
        input_widget.setMinimumHeight(34)
        vbox.addWidget(input_widget)
        self.layout.addWidget(card)

    def _on_wahlap(self) -> None:
        credential = self.wahlap_input.toPlainText().strip()
        if not credential:
            self._warn("请先粘贴华立授权 URL、Cookie 或请求头。")
            return
        self._start_worker("wahlap", {"credential": credential})

    def _on_diving_fish(self) -> None:
        token = self.df_token.text().strip()
        if not token:
            self._warn("请先填写水鱼 Import Token。")
            return
        self._start_worker("diving-fish", {"token": token})

    def _on_lxns(self) -> None:
        token = self.lxns_token.text().strip()
        if not token:
            self._warn("请先填写落雪个人 API 密钥。")
            return
        self._start_worker("lxns", {"token": token})

    def _on_catalog(self) -> None:
        self._start_worker("catalog", {})

    def _start_worker(self, action: str, payload: dict) -> None:
        self._set_busy(True)
        self.log_output.clear()
        self.status_label.setText("运行中...")
        self._worker = ImportWorker(action, payload)
        self._worker.progress.connect(self._log)
        self._worker.finished.connect(self._on_finished)
        self._worker.start()

    def _on_finished(self, result: dict) -> None:
        self._set_busy(False)
        if not result.get("success"):
            err = redactor.redact(result.get("error", "未知错误"))
            self.status_label.setText(f"失败：{err}")
            self._log(f"失败：{err}")
            InfoBar.error("导入失败", err, duration=6000, position=InfoBarPosition.TOP_RIGHT, parent=self)
            return
        summary = result.get("summary") or {}
        text = self._summary_text(summary)
        self.status_label.setText(text)
        self._log(text)
        InfoBar.success("完成", text, duration=5000, position=InfoBarPosition.TOP_RIGHT, parent=self)

    def _summary_text(self, summary: dict) -> str:
        source = summary.get("source", "")
        return (
            f"{source} 完成：新增 {summary.get('inserted', 0)}，更新 {summary.get('updated', 0)}，"
            f"重复 {summary.get('skipped_duplicate', 0)}，quarantine {summary.get('quarantined', 0)}，"
            f"失败 {summary.get('failed', 0)}。{summary.get('message', '')}"
        ).strip()

    def _set_busy(self, busy: bool) -> None:
        for button in (self.wahlap_btn, self.df_btn, self.lxns_btn, self.catalog_btn):
            button.setEnabled(not busy)
        self.spinner.setVisible(busy)

    def _warn(self, message: str) -> None:
        InfoBar.warning("缺少输入", message, duration=3000, parent=self)

    def _log(self, text: str) -> None:
        self.log_output.append(redactor.redact(text))
        scrollbar = self.log_output.verticalScrollBar()
        scrollbar.setValue(scrollbar.maximum())
