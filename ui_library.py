from __future__ import annotations

import os

from PyQt6.QtCore import (
    QAbstractListModel,
    QEvent,
    QModelIndex,
    QPoint,
    QRect,
    QRectF,
    QSize,
    Qt,
    QThread,
    QTimer,
    pyqtSignal,
)
from PyQt6.QtGui import QColor, QFont, QFontMetrics, QPainter, QPainterPath, QPen, QPixmap
from PyQt6.QtWidgets import (
    QAbstractItemView,
    QComboBox,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListView,
    QScrollArea,
    QSizePolicy,
    QSplitter,
    QStyle,
    QStyledItemDelegate,
    QVBoxLayout,
    QWidget,
)
from qfluentwidgets import BodyLabel, FluentIcon as FIF, InfoBar, InfoBarPosition, PushButton, SubtitleLabel

from cover_manager import cover_path
from fluentmai_core import database
from fluentmai_core.catalog import safe_api_error, sync_diving_fish_catalog, sync_lxns_catalog
from fluentmai_core.chart_browser import (
    DIFFICULTY_OPTIONS,
    SORT_OPTIONS,
    STATUS_OPTIONS,
    CatalogStats,
    ChartFilterOptions,
    ChartFilters,
    ChartQueryResult,
    ChartRecord,
    DISPLAY_LIMIT,
    FilterOption,
    catalog_stats,
    filter_options_for_records,
    load_chart_records,
    query_chart_records,
)


class ChartQueryWorker(QThread):
    loaded = pyqtSignal(int, object, object, object)
    failed = pyqtSignal(int, str)

    def __init__(self, sequence: int, filters: ChartFilters, parent=None):
        super().__init__(parent)
        self.sequence = sequence
        self.filters = filters

    def run(self) -> None:
        try:
            conn = database.connect()
            try:
                records = load_chart_records(conn)
                result = query_chart_records(records, self.filters)
                options = filter_options_for_records(records)
                stats = catalog_stats(conn)
            finally:
                conn.close()
            self.loaded.emit(self.sequence, result, options, stats)
        except Exception as exc:
            self.failed.emit(self.sequence, safe_api_error(exc))


class CatalogRefreshWorker(QThread):
    finished_with_message = pyqtSignal(bool, str)

    def run(self) -> None:
        try:
            count = sync_lxns_catalog()
            self.finished_with_message.emit(True, f"LXNS 曲库已刷新：{count} 首歌曲")
            return
        except Exception as exc:
            first_error = safe_api_error(exc)

        try:
            count = sync_diving_fish_catalog(replace=True)
            self.finished_with_message.emit(True, f"Diving-Fish 回退曲库已刷新：{count} 首歌曲；LXNS 失败：{first_error}")
        except Exception as exc:
            self.finished_with_message.emit(False, f"LXNS 失败：{first_error}；Diving-Fish 失败：{safe_api_error(exc)}")


class ChartListModel(QAbstractListModel):
    RECORD_ROLE = Qt.ItemDataRole.UserRole + 1

    def __init__(self, parent=None):
        super().__init__(parent)
        self._records: list[ChartRecord] = []

    def rowCount(self, parent: QModelIndex = QModelIndex()) -> int:
        return 0 if parent.isValid() else len(self._records)

    def data(self, index: QModelIndex, role: int = Qt.ItemDataRole.DisplayRole):
        if not index.isValid() or not 0 <= index.row() < len(self._records):
            return None
        record = self._records[index.row()]
        if role == Qt.ItemDataRole.DisplayRole:
            return record.title
        if role == self.RECORD_ROLE:
            return record
        return None

    def set_records(self, records: list[ChartRecord]) -> None:
        self.beginResetModel()
        self._records = records
        self.endResetModel()

    def record_at(self, row: int) -> ChartRecord | None:
        if 0 <= row < len(self._records):
            return self._records[row]
        return None


class ChartCardDelegate(QStyledItemDelegate):
    _difficulty_colors = {
        0: QColor("#2f9e44"),
        1: QColor("#d9480f"),
        2: QColor("#e03131"),
        3: QColor("#8e44d6"),
        4: QColor("#b15cff"),
    }

    def __init__(self, parent=None):
        super().__init__(parent)
        self._pixmap_cache: dict[int, QPixmap | None] = {}

    def sizeHint(self, option, index: QModelIndex) -> QSize:
        return QSize(420, 174)

    def paint(self, painter: QPainter, option, index: QModelIndex) -> None:
        record: ChartRecord | None = index.data(ChartListModel.RECORD_ROLE)
        if record is None:
            return

        painter.save()
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        rect = option.rect.adjusted(5, 5, -5, -5)
        selected = bool(option.state & QStyle.StateFlag.State_Selected)
        hovered = bool(option.state & QStyle.StateFlag.State_MouseOver)

        bg = QColor("#211936") if selected else QColor("#171b26" if hovered else "#141821")
        border = QColor("#8b5cf6") if selected else QColor("#2c3345")
        path = QPainterPath()
        path.addRoundedRect(QRectF(rect), 8, 8)
        painter.fillPath(path, bg)
        painter.setPen(QPen(border, 1.2))
        painter.drawPath(path)

        image_rect = QRect(rect.left() + 12, rect.top() + 14, 96, 96)
        self._draw_jacket(painter, image_rect, record)

        text_left = image_rect.right() + 14
        text_width = max(80, rect.right() - text_left - 14)
        top = rect.top() + 12

        title_font = QFont(option.font)
        title_font.setPointSize(11)
        title_font.setBold(True)
        painter.setFont(title_font)
        painter.setPen(QColor("#f5f7fb"))
        fm = QFontMetrics(title_font)
        painter.drawText(
            QRect(text_left, top, text_width, 24),
            Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter,
            fm.elidedText(record.title, Qt.TextElideMode.ElideRight, text_width),
        )

        body_font = QFont(option.font)
        body_font.setPointSize(9)
        painter.setFont(body_font)
        body_fm = QFontMetrics(body_font)
        painter.setPen(QColor("#aab2c4"))
        artist = record.artist or "未知艺术家"
        painter.drawText(
            QRect(text_left, top + 27, text_width, 20),
            Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter,
            body_fm.elidedText(artist, Qt.TextElideMode.ElideRight, text_width),
        )

        pill_y = top + 54
        x = text_left
        x = self._draw_pill(painter, QPoint(x, pill_y), record.type_label, QColor("#334155"), QColor("#dbeafe"))
        diff_color = QColor("#64748b") if record.is_utage else self._difficulty_colors.get(record.difficulty_index, QColor("#64748b"))
        x = self._draw_pill(painter, QPoint(x + 6, pill_y), record.difficulty_label, diff_color, QColor("#ffffff"))
        level_text = f"{record.level or '--'} / {record.const_label}"
        self._draw_pill(painter, QPoint(x + 6, pill_y), level_text, QColor("#27233b"), QColor("#e9d5ff"))

        meta_lines = [
            f"BPM {record.bpm or '--'}    {record.version_label}",
            f"物量 {record.notes_label}    谱师 {record.charter or '--'}",
        ]
        painter.setFont(body_font)
        for line_index, line in enumerate(meta_lines):
            painter.setPen(QColor("#9ba6bb"))
            painter.drawText(
                QRect(text_left, top + 88 + line_index * 20, text_width, 20),
                Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter,
                body_fm.elidedText(line, Qt.TextElideMode.ElideRight, text_width),
            )

        status = "已游玩" if record.played else "未游玩"
        score_text = ""
        if record.played:
            score_text = f"{record.achievements:.4f}%"
            if record.dx_score is not None:
                score_text += f"  DX {record.dx_score}"
        painter.setPen(QColor("#7dd3fc") if record.played else QColor("#94a3b8"))
        painter.drawText(
            QRect(image_rect.left(), image_rect.bottom() + 10, image_rect.width(), 22),
            Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter,
            status,
        )
        if score_text:
            painter.drawText(
                QRect(text_left, top + 128, text_width, 20),
                Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter,
                body_fm.elidedText(score_text, Qt.TextElideMode.ElideRight, text_width),
            )
        painter.restore()

    def _draw_jacket(self, painter: QPainter, rect: QRect, record: ChartRecord) -> None:
        painter.save()
        path = QPainterPath()
        path.addRoundedRect(QRectF(rect), 7, 7)
        painter.setClipPath(path)
        pixmap = self._pixmap_for(record.song_id)
        if pixmap and not pixmap.isNull():
            scaled = pixmap.scaled(
                rect.size(),
                Qt.AspectRatioMode.KeepAspectRatioByExpanding,
                Qt.TransformationMode.SmoothTransformation,
            )
            x = rect.left() + (rect.width() - scaled.width()) // 2
            y = rect.top() + (rect.height() - scaled.height()) // 2
            painter.drawPixmap(x, y, scaled)
        else:
            painter.fillRect(rect, QColor("#222938"))
            painter.setPen(QColor("#8b96aa"))
            font = QFont(painter.font())
            font.setPointSize(8)
            font.setBold(True)
            painter.setFont(font)
            painter.drawText(rect, Qt.AlignmentFlag.AlignCenter, "NO\nJACKET")
        painter.restore()
        painter.setPen(QPen(QColor("#30384c"), 1))
        painter.drawRoundedRect(rect, 7, 7)

    def _pixmap_for(self, song_id: int) -> QPixmap | None:
        if song_id in self._pixmap_cache:
            return self._pixmap_cache[song_id]
        path = cover_path(song_id)
        pixmap = QPixmap(path) if os.path.exists(path) else QPixmap()
        self._pixmap_cache[song_id] = pixmap if not pixmap.isNull() else None
        return self._pixmap_cache[song_id]

    def _draw_pill(self, painter: QPainter, pos: QPoint, text: str, bg: QColor, fg: QColor) -> int:
        font = QFont(painter.font())
        font.setPointSize(8)
        font.setBold(True)
        painter.setFont(font)
        fm = QFontMetrics(font)
        width = min(150, fm.horizontalAdvance(text) + 18)
        rect = QRect(pos.x(), pos.y(), width, 24)
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(bg)
        painter.drawRoundedRect(rect, 6, 6)
        painter.setPen(fg)
        painter.drawText(
            rect.adjusted(8, 0, -8, 0),
            Qt.AlignmentFlag.AlignCenter,
            fm.elidedText(text, Qt.TextElideMode.ElideRight, width - 16),
        )
        return rect.right()


class ChartDetailPanel(QFrame):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setObjectName("DetailPanel")
        self.setMinimumWidth(300)
        self.setMaximumWidth(380)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 18, 18, 18)
        layout.setSpacing(12)

        self.cover_label = QLabel("NO\nJACKET")
        self.cover_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.cover_label.setFixedSize(180, 180)
        self.cover_label.setStyleSheet("background:#222938; color:#8b96aa; border:1px solid #30384c; border-radius:8px; font-weight:700;")
        layout.addWidget(self.cover_label, alignment=Qt.AlignmentFlag.AlignHCenter)

        self.title_label = SubtitleLabel("选择一张谱面")
        self.title_label.setWordWrap(True)
        self.title_label.setStyleSheet("font-size: 18px; font-weight: 700;")
        layout.addWidget(self.title_label)

        self.subtitle_label = BodyLabel("点击左侧卡片查看详情")
        self.subtitle_label.setWordWrap(True)
        self.subtitle_label.setStyleSheet("color:#aab2c4;")
        layout.addWidget(self.subtitle_label)

        self.fields: dict[str, BodyLabel] = {}
        for key in (
            "difficulty",
            "constant",
            "meta",
            "notes",
            "charter",
            "status",
            "score",
            "source",
        ):
            label = BodyLabel("")
            label.setWordWrap(True)
            label.setStyleSheet("color:#d7dce8; line-height:1.45;")
            self.fields[key] = label
            layout.addWidget(label)
        layout.addStretch(1)

    def set_record(self, record: ChartRecord | None) -> None:
        if record is None:
            self.cover_label.setPixmap(QPixmap())
            self.cover_label.setText("NO\nJACKET")
            self.title_label.setText("选择一张谱面")
            self.subtitle_label.setText("点击左侧卡片查看详情")
            for label in self.fields.values():
                label.clear()
            return

        self.title_label.setText(record.title)
        self.subtitle_label.setText(record.artist or "未知艺术家")
        self._set_cover(record.song_id)
        self.fields["difficulty"].setText(f"难度：{record.type_label} / {record.difficulty_label}")
        self.fields["constant"].setText(f"等级：{record.level or '--'}    定数：{record.const_label}")
        self.fields["meta"].setText(f"BPM：{record.bpm or '--'}    版本：{record.version_label}\n分区：{record.genre or '--'}")
        self.fields["notes"].setText(
            "物量：{total}    Tap {tap} / Hold {hold} / Slide {slide} / Touch {touch} / Break {break_}".format(
                total=record.notes_label,
                tap=record.notes_tap if record.notes_tap is not None else "--",
                hold=record.notes_hold if record.notes_hold is not None else "--",
                slide=record.notes_slide if record.notes_slide is not None else "--",
                touch=record.notes_touch if record.notes_touch is not None else "--",
                break_=record.notes_break if record.notes_break is not None else "--",
            )
        )
        self.fields["charter"].setText(f"谱师：{record.charter or '--'}")
        self.fields["status"].setText(f"游玩状态：{'已游玩' if record.played else '未游玩'}")
        if record.played:
            score = f"达成率：{record.achievements:.4f}%"
            if record.dx_score is not None:
                score += f"    DX Score：{record.dx_score}"
            if record.full_combo:
                score += f"    FC：{record.full_combo.upper()}"
            if record.full_sync:
                score += f"    FS：{record.full_sync.upper()}"
            self.fields["score"].setText(score)
            source = record.score_source or "--"
            if record.play_time:
                source += f"    游玩时间：{record.play_time}"
            elif record.score_updated_at:
                source += f"    导入时间：{record.score_updated_at:.0f}"
            self.fields["source"].setText(f"来源：{source}")
        else:
            self.fields["score"].setText("达成率：--    DX Score：--")
            self.fields["source"].setText("来源：--")

    def _set_cover(self, song_id: int) -> None:
        path = cover_path(song_id)
        if os.path.exists(path):
            pixmap = QPixmap(path)
            if not pixmap.isNull():
                self.cover_label.setText("")
                self.cover_label.setPixmap(
                    pixmap.scaled(
                        self.cover_label.size(),
                        Qt.AspectRatioMode.KeepAspectRatio,
                        Qt.TransformationMode.SmoothTransformation,
                    )
                )
                return
        self.cover_label.setPixmap(QPixmap())
        self.cover_label.setText("NO\nJACKET")


class LibraryInterface(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent=parent)
        self.setObjectName("LibraryInterface")
        self._query_sequence = 0
        self._query_workers: dict[int, ChartQueryWorker] = {}
        self._refresh_worker: CatalogRefreshWorker | None = None

        self._debounce = QTimer(self)
        self._debounce.setInterval(180)
        self._debounce.setSingleShot(True)
        self._debounce.timeout.connect(self._start_query)

        self.layout = QVBoxLayout(self)
        self.layout.setContentsMargins(24, 28, 24, 20)
        self.layout.setSpacing(14)

        self._build_header()
        self._build_filters()
        self._build_results()
        self._apply_style()

        self._schedule_query()

    def _build_header(self) -> None:
        header = QFrame()
        header.setObjectName("HeaderPanel")
        layout = QHBoxLayout(header)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(12)

        title_box = QVBoxLayout()
        title_box.setSpacing(5)
        title = SubtitleLabel("歌曲与谱面查询")
        title.setStyleSheet("font-size: 25px; font-weight: 800;")
        self.catalog_count_label = BodyLabel("曲库谱面 --")
        self.catalog_count_label.setStyleSheet("color:#aab2c4;")
        title_box.addWidget(title)
        title_box.addWidget(self.catalog_count_label)

        self.result_count_label = BodyLabel("匹配 --")
        self.result_count_label.setStyleSheet("color:#d7dce8; font-weight:600;")
        self.refresh_button = PushButton(FIF.UPDATE, "刷新曲库")
        self.refresh_button.clicked.connect(self._refresh_catalog)

        layout.addLayout(title_box, 1)
        layout.addWidget(self.result_count_label)
        layout.addWidget(self.refresh_button)
        self.layout.addWidget(header)

    def _build_filters(self) -> None:
        panel = QFrame()
        panel.setObjectName("FilterPanel")
        grid = QGridLayout(panel)
        grid.setContentsMargins(0, 0, 0, 0)
        grid.setHorizontalSpacing(10)
        grid.setVerticalSpacing(10)

        self.search_input = QLineEdit()
        self.search_input.setPlaceholderText("曲名 / 艺术家 / 谱师 / 版本 / ID")
        self.level_input = QLineEdit()
        self.level_input.setPlaceholderText("等级或定数：13 / 13+ / 13.3")
        self.difficulty_combo = QComboBox()
        self.genre_combo = QComboBox()
        self.version_combo = QComboBox()
        self.status_combo = QComboBox()
        self.sort_combo = QComboBox()

        self._set_combo_options(self.difficulty_combo, DIFFICULTY_OPTIONS, "all")
        self._set_combo_options(self.genre_combo, [FilterOption("all", "全部分区")], "all")
        self._set_combo_options(self.version_combo, [FilterOption("all", "全部版本")], "all")
        self._set_combo_options(self.status_combo, STATUS_OPTIONS, "all")
        self._set_combo_options(self.sort_combo, SORT_OPTIONS, "constant_desc")

        widgets = [
            ("搜索", self.search_input),
            ("等级 / 定数", self.level_input),
            ("难度", self.difficulty_combo),
            ("分区", self.genre_combo),
            ("版本", self.version_combo),
            ("游玩状态", self.status_combo),
            ("排序", self.sort_combo),
        ]
        for index, (label, widget) in enumerate(widgets):
            row = index // 2
            col = (index % 2) * 2
            grid.addWidget(self._filter_label(label), row, col)
            grid.addWidget(widget, row, col + 1)
            if isinstance(widget, QComboBox):
                widget.setMinimumWidth(150)
            else:
                widget.setMinimumWidth(220)

        for widget in (
            self.search_input,
            self.level_input,
            self.difficulty_combo,
            self.genre_combo,
            self.version_combo,
            self.status_combo,
            self.sort_combo,
        ):
            if isinstance(widget, QLineEdit):
                widget.textChanged.connect(self._schedule_query)
            else:
                widget.currentIndexChanged.connect(self._schedule_query)

        grid.setColumnStretch(1, 2)
        grid.setColumnStretch(3, 1)
        self.layout.addWidget(panel)

    def _build_results(self) -> None:
        splitter = QSplitter(Qt.Orientation.Horizontal)
        splitter.setChildrenCollapsible(False)

        self.model = ChartListModel(self)
        self.delegate = ChartCardDelegate(self)
        self.result_view = QListView()
        self.result_view.setModel(self.model)
        self.result_view.setItemDelegate(self.delegate)
        self.result_view.setViewMode(QListView.ViewMode.IconMode)
        self.result_view.setResizeMode(QListView.ResizeMode.Adjust)
        self.result_view.setFlow(QListView.Flow.LeftToRight)
        self.result_view.setWrapping(True)
        self.result_view.setUniformItemSizes(True)
        self.result_view.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self.result_view.setVerticalScrollMode(QAbstractItemView.ScrollMode.ScrollPerPixel)
        self.result_view.setMouseTracking(True)
        self.result_view.clicked.connect(self._on_chart_clicked)
        self.result_view.viewport().installEventFilter(self)

        self.detail_panel = ChartDetailPanel()
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        scroll.setWidget(self.detail_panel)

        splitter.addWidget(self.result_view)
        splitter.addWidget(scroll)
        splitter.setStretchFactor(0, 1)
        splitter.setStretchFactor(1, 0)
        splitter.setSizes([760, 340])
        self.layout.addWidget(splitter, 1)
        QTimer.singleShot(0, self._update_grid_size)

    def _filter_label(self, text: str) -> QLabel:
        label = QLabel(text)
        label.setAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
        label.setStyleSheet("color:#aab2c4; font-size:12px;")
        label.setSizePolicy(QSizePolicy.Policy.Fixed, QSizePolicy.Policy.Preferred)
        return label

    def _schedule_query(self) -> None:
        self._debounce.start()

    def _start_query(self) -> None:
        self._query_sequence += 1
        sequence = self._query_sequence
        worker = ChartQueryWorker(sequence, self._current_filters(), self)
        self._query_workers[sequence] = worker
        worker.loaded.connect(self._on_query_loaded)
        worker.failed.connect(self._on_query_failed)
        worker.finished.connect(lambda seq=sequence: self._query_workers.pop(seq, None))
        worker.start()

    def _on_query_loaded(
        self,
        sequence: int,
        result: ChartQueryResult,
        options: ChartFilterOptions,
        stats: CatalogStats,
    ) -> None:
        if sequence != self._query_sequence:
            return
        self._apply_filter_options(options)
        self._apply_result(result, stats)

    def _on_query_failed(self, sequence: int, message: str) -> None:
        if sequence != self._query_sequence:
            return
        self.model.set_records([])
        self.result_count_label.setText("匹配 0")
        self.detail_panel.set_record(None)
        InfoBar.error("曲库查询失败", message, duration=5000, parent=self)

    def _apply_result(self, result: ChartQueryResult, stats: CatalogStats) -> None:
        self.model.set_records(result.records)
        self._update_grid_size()
        catalog_text = f"曲库谱面 {stats.chart_count}"
        if stats.chart_count:
            catalog_text += f"（常规 {stats.regular_chart_count} / 宴会 {stats.utage_count}）"
        if stats.metadata_sparse:
            catalog_text += " · 元数据待刷新"
        self.catalog_count_label.setText(catalog_text)

        if result.is_limited:
            self.result_count_label.setText(
                f"匹配 {result.total_count}，展示 {result.displayed_count} / {result.total_count}（上限 {result.limit}）"
            )
        else:
            self.result_count_label.setText(f"匹配 {result.total_count}")

        if result.records:
            first = self.model.index(0, 0)
            self.result_view.setCurrentIndex(first)
            self.detail_panel.set_record(result.records[0])
        else:
            self.detail_panel.set_record(None)

    def _apply_filter_options(self, options: ChartFilterOptions) -> None:
        self._set_combo_options(self.genre_combo, options.genres, self._combo_code(self.genre_combo))
        self._set_combo_options(self.version_combo, options.versions, self._combo_code(self.version_combo))

    def _current_filters(self) -> ChartFilters:
        diff_code = self._combo_code(self.difficulty_combo)
        difficulty_index = None if diff_code == "all" else int(diff_code)
        return ChartFilters(
            search=self.search_input.text(),
            level=self.level_input.text(),
            difficulty_index=difficulty_index,
            genre=self._combo_code(self.genre_combo),
            version=self._combo_code(self.version_combo),
            status=self._combo_code(self.status_combo),
            sort=self._combo_code(self.sort_combo),
            limit=DISPLAY_LIMIT,
        )

    def _combo_code(self, combo: QComboBox) -> str:
        data = combo.currentData()
        return str(data) if data is not None else "all"

    def _set_combo_options(self, combo: QComboBox, options: list[FilterOption], current_code: str) -> None:
        combo.blockSignals(True)
        combo.clear()
        valid_codes = {option.code for option in options}
        selected_code = current_code if current_code in valid_codes else options[0].code
        selected_index = 0
        for index, option in enumerate(options):
            combo.addItem(option.label, option.code)
            if option.code == selected_code:
                selected_index = index
        combo.setCurrentIndex(selected_index)
        combo.blockSignals(False)

    def _on_chart_clicked(self, index: QModelIndex) -> None:
        self.detail_panel.set_record(self.model.record_at(index.row()))

    def _refresh_catalog(self) -> None:
        if self._refresh_worker and self._refresh_worker.isRunning():
            return
        self.refresh_button.setEnabled(False)
        self.refresh_button.setText("刷新中")
        self._refresh_worker = CatalogRefreshWorker(self)
        self._refresh_worker.finished_with_message.connect(self._on_catalog_refreshed)
        self._refresh_worker.start()

    def _on_catalog_refreshed(self, success: bool, message: str) -> None:
        self.refresh_button.setEnabled(True)
        self.refresh_button.setText("刷新曲库")
        if success:
            InfoBar.success("曲库已刷新", message, duration=3500, parent=self, position=InfoBarPosition.TOP_RIGHT)
            self._schedule_query()
        else:
            InfoBar.error("曲库刷新失败", message, duration=7000, parent=self, position=InfoBarPosition.TOP_RIGHT)

    def refresh_for_test(self) -> None:
        conn = database.connect()
        try:
            records = load_chart_records(conn)
            self._apply_filter_options(filter_options_for_records(records))
            self._apply_result(query_chart_records(records, self._current_filters()), catalog_stats(conn))
        finally:
            conn.close()

    def eventFilter(self, watched, event) -> bool:
        if watched is getattr(self, "result_view", None).viewport() and event.type() == QEvent.Type.Resize:
            self._update_grid_size()
        return super().eventFilter(watched, event)

    def resizeEvent(self, event) -> None:
        super().resizeEvent(event)
        self._update_grid_size()

    def _update_grid_size(self) -> None:
        if not hasattr(self, "result_view"):
            return
        width = max(320, self.result_view.viewport().width() - 18)
        columns = 2 if width >= 760 else 1
        card_width = max(320, (width - (columns - 1) * 12) // columns)
        self.result_view.setGridSize(QSize(card_width, 184))
        self.result_view.setSpacing(10)

    def _apply_style(self) -> None:
        self.setStyleSheet(
            """
            QWidget#LibraryInterface {
                background: #0f1117;
                color: #eef2ff;
            }
            QLineEdit, QComboBox {
                min-height: 34px;
                border: 1px solid #30384c;
                border-radius: 7px;
                padding: 0 10px;
                background: #151a24;
                color: #eef2ff;
                selection-background-color: #7c3aed;
            }
            QLineEdit:focus, QComboBox:focus {
                border: 1px solid #8b5cf6;
            }
            QListView {
                background: transparent;
                border: none;
                outline: none;
            }
            QScrollArea {
                background: transparent;
            }
            QFrame#DetailPanel {
                background: #141821;
                border: 1px solid #2c3345;
                border-radius: 8px;
            }
            """
        )
