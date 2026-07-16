from __future__ import annotations

from datetime import datetime

from PyQt6.QtWidgets import QComboBox, QHBoxLayout, QLineEdit, QTableWidget, QTableWidgetItem, QVBoxLayout, QWidget
from qfluentwidgets import BodyLabel, FluentIcon as FIF, PushButton, SubtitleLabel

from fluentmai_core import database


class ScoresInterface(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent=parent)
        self.setObjectName("ScoresInterface")
        self.layout = QVBoxLayout(self)
        self.layout.setContentsMargins(24, 32, 24, 24)
        self.layout.setSpacing(12)

        title = SubtitleLabel("本地成绩")
        title.setStyleSheet("font-size: 26px; font-weight: bold;")
        self.layout.addWidget(title)

        controls = QHBoxLayout()
        self.search_input = QLineEdit()
        self.search_input.setPlaceholderText("搜索歌曲名或歌曲 ID")
        self.search_input.textChanged.connect(self.refresh)
        controls.addWidget(self.search_input, 2)

        self.diff_combo = QComboBox()
        self.diff_combo.addItem("全部难度", None)
        for idx, label in enumerate(["Basic", "Advanced", "Expert", "Master", "Re:Master"]):
            self.diff_combo.addItem(label, idx)
        self.diff_combo.currentIndexChanged.connect(self.refresh)
        controls.addWidget(self.diff_combo)

        self.source_combo = QComboBox()
        self.source_combo.addItem("全部来源", "")
        self.source_combo.currentIndexChanged.connect(self.refresh)
        controls.addWidget(self.source_combo)

        refresh_btn = PushButton(FIF.UPDATE, "刷新")
        refresh_btn.clicked.connect(self.refresh)
        controls.addWidget(refresh_btn)
        self.layout.addLayout(controls)

        self.summary = BodyLabel("")
        self.summary.setStyleSheet("color: #a0a0a0; font-size: 12px;")
        self.layout.addWidget(self.summary)

        self.table = QTableWidget(0, 10)
        self.table.setHorizontalHeaderLabels(
            ["歌曲", "ID", "类型", "难度", "等级", "达成率", "DX 分", "FC", "FS", "来源"]
        )
        self.table.setSortingEnabled(True)
        self.layout.addWidget(self.table, 1)
        self.refresh_sources()
        self.refresh()

    def refresh_sources(self) -> None:
        current = self.source_combo.currentData() or ""
        self.source_combo.blockSignals(True)
        self.source_combo.clear()
        self.source_combo.addItem("全部来源", "")
        conn = database.connect()
        try:
            for source in database.sources(conn):
                self.source_combo.addItem(source, source)
        finally:
            conn.close()
        index = max(0, self.source_combo.findData(current))
        self.source_combo.setCurrentIndex(index)
        self.source_combo.blockSignals(False)

    def refresh(self) -> None:
        conn = database.connect()
        try:
            rows = database.list_scores(
                conn,
                query=self.search_input.text(),
                source=self.source_combo.currentData() or "",
                difficulty_index=self.diff_combo.currentData(),
            )
            quarantine_count = database.count_quarantine(conn)
        finally:
            conn.close()

        self.table.setSortingEnabled(False)
        self.table.setRowCount(len(rows))
        for row_idx, row in enumerate(rows):
            values = [
                row["title"],
                "" if row["song_id"] is None else str(row["song_id"]),
                row["chart_type"],
                row["difficulty_name"],
                row["level"] or "",
                f"{row['achievements']:.4f}%",
                "" if row["dx_score"] is None else str(row["dx_score"]),
                row["full_combo"] or "",
                row["full_sync"] or "",
                row["source"],
            ]
            for col, value in enumerate(values):
                self.table.setItem(row_idx, col, QTableWidgetItem(value))
        self.table.setSortingEnabled(True)
        self.table.resizeColumnsToContents()
        now = datetime.now().strftime("%H:%M:%S")
        self.summary.setText(f"显示 {len(rows)} 条成绩；quarantine {quarantine_count} 条；刷新于 {now}")
