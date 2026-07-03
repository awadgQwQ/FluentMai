from __future__ import annotations

from PyQt6.QtWidgets import QHBoxLayout, QLineEdit, QTableWidget, QTableWidgetItem, QVBoxLayout, QWidget
from qfluentwidgets import BodyLabel, FluentIcon as FIF, PushButton, SubtitleLabel

from fluentmai_core import database


class LibraryInterface(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent=parent)
        self.setObjectName("LibraryInterface")
        self._song_ids: list[int] = []

        self.layout = QVBoxLayout(self)
        self.layout.setContentsMargins(24, 32, 24, 24)
        self.layout.setSpacing(12)

        title = SubtitleLabel("歌曲与谱面")
        title.setStyleSheet("font-size: 26px; font-weight: bold;")
        self.layout.addWidget(title)

        row = QHBoxLayout()
        self.search_input = QLineEdit()
        self.search_input.setPlaceholderText("按歌曲名、艺术家或 ID 搜索")
        self.search_input.textChanged.connect(self.refresh_songs)
        row.addWidget(self.search_input, 1)
        refresh_btn = PushButton(FIF.SEARCH, "查询")
        refresh_btn.clicked.connect(self.refresh_songs)
        row.addWidget(refresh_btn)
        self.layout.addLayout(row)

        self.summary = BodyLabel("")
        self.summary.setStyleSheet("color: #a0a0a0; font-size: 12px;")
        self.layout.addWidget(self.summary)

        tables = QHBoxLayout()
        self.song_table = QTableWidget(0, 7)
        self.song_table.setHorizontalHeaderLabels(["ID", "歌曲", "艺术家", "分类", "版本", "BPM", "谱面数"])
        self.song_table.itemSelectionChanged.connect(self._on_song_selected)
        tables.addWidget(self.song_table, 2)

        self.chart_table = QTableWidget(0, 9)
        self.chart_table.setHorizontalHeaderLabels(["类型", "难度", "等级", "定数", "谱师", "物量", "达成率", "DX 分", "来源"])
        tables.addWidget(self.chart_table, 3)
        self.layout.addLayout(tables, 1)

        self.refresh_songs()

    def refresh_songs(self) -> None:
        conn = database.connect()
        try:
            rows = database.search_songs(conn, self.search_input.text())
        finally:
            conn.close()

        self._song_ids = []
        self.song_table.setRowCount(len(rows))
        for row_idx, row in enumerate(rows):
            self._song_ids.append(row["song_id"])
            values = [
                str(row["song_id"]),
                row["title"],
                row["artist"] or "",
                row["genre"] or "",
                "" if row["version"] is None else str(row["version"]),
                "" if row["bpm"] is None else str(row["bpm"]),
                str(row["chart_count"]),
            ]
            for col, value in enumerate(values):
                self.song_table.setItem(row_idx, col, QTableWidgetItem(value))
        self.song_table.resizeColumnsToContents()
        self.summary.setText(f"找到 {len(rows)} 首歌曲。选择一首可查看谱面和本地成绩。")
        if rows:
            self.song_table.selectRow(0)
        else:
            self.chart_table.setRowCount(0)

    def _on_song_selected(self) -> None:
        indexes = self.song_table.selectionModel().selectedRows()
        if not indexes:
            return
        row_index = indexes[0].row()
        if row_index >= len(self._song_ids):
            return
        self.refresh_charts(self._song_ids[row_index])

    def refresh_charts(self, song_id: int) -> None:
        conn = database.connect()
        try:
            rows = database.list_charts_for_song(conn, song_id)
        finally:
            conn.close()

        self.chart_table.setRowCount(len(rows))
        for row_idx, row in enumerate(rows):
            values = [
                row["chart_type"],
                row["difficulty_name"],
                row["level"] or "",
                "" if row["level_value"] is None else f"{row['level_value']:.1f}",
                row["charter"] or "",
                "" if row["notes_total"] is None else str(row["notes_total"]),
                "" if row["achievements"] is None else f"{row['achievements']:.4f}%",
                "" if row["dx_score"] is None else str(row["dx_score"]),
                row["source"] or "",
            ]
            for col, value in enumerate(values):
                self.chart_table.setItem(row_idx, col, QTableWidgetItem(value))
        self.chart_table.resizeColumnsToContents()
