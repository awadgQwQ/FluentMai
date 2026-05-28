"""
ui_dashboard.py — Dark Tech Bento Box dashboard.
Features: SQLite offline cache, cover thumbnails + blurred backgrounds,
staggered fade-in + slide-up entrance animations.
"""

from __future__ import annotations

from datetime import datetime

from PyQt6.QtCore import (
    Qt, QThread, pyqtSignal, pyqtProperty,
    QPropertyAnimation, QEasingCurve, QTimer, QPoint, QSize, QRectF,
)
from PyQt6.QtGui import QPixmap, QPainter, QColor, QPainterPath, QRegion, QFontMetrics, QFont
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel,
    QScrollArea, QFrame, QGraphicsOpacityEffect,
    QGraphicsBlurEffect, QApplication, QSizePolicy, QSpacerItem,
)

from qfluentwidgets import (
    CardWidget, SubtitleLabel, BodyLabel,
    PushButton, FluentIcon as FIF, InfoBar, InfoBarPosition,
    IndeterminateProgressRing,
)

from fetch_profile import query_player, load_b50_from_db, save_b50_to_db, init_b50_table
from cover_manager import cover_path, has_cover, BulkCoverWorker, download_cover_sync

# ── colour & layout constants ─────────────────────────────────────
BG_DARK      = "#1a1a2e"
CARD_BG      = "#1e2030"
CARD_BORDER  = "rgba(255,255,255,0.1)"
ACCENT       = "#00d4ff"
TEXT_PRIMARY = "#e0e0e0"
TEXT_DIM     = "#808090"
CARD_RADIUS  = 10

COVER_SIZE   = 56
CARD_WIDTH   = 360
CARD_HEIGHT  = 84
GAP          = 10
RA_PANEL_W   = 50

RATE_COLORS = {
    "sssp": "#ffd700", "sss": "#ff8c00", "ssp": "#ff6347",
    "ss": "#ff4444", "sp": "#ff2222", "s": "#cc0000", "aaa": "#00ccff",
}
FC_BADGE_COLORS = {
    "ap": "#ffd700", "app": "#ffd700",
    "fc": "#00ff88", "fcp": "#00ff88",
    "fs": "#00d4ff",  "fsp": "#00d4ff",
    "fsd": "#00d4ff", "fsdp": "#00d4ff",
}
DIFF_COLORS = {
    0: "#22c55e",  # Basic — green
    1: "#f97316",  # Advanced — orange
    2: "#ef4444",  # Expert — red
    3: "#9333ea",  # Master — purple
    4: "#f0abfc",  # Re:Master — pink-white
}
DIFF_LABELS = {
    0: "BASIC", 1: "ADVANCED", 2: "EXPERT", 3: "MASTER", 4: "Re:MASTER",
}

# ── helpers ───────────────────────────────────────────────────────

def _card_sheet() -> str:
    return (
        f"background: {CARD_BG}; "
        f"border: 1px solid {CARD_BORDER}; "
        f"border-radius: {CARD_RADIUS}px; "
    )


def _hex_to_rgba(hex_color: str, alpha: float) -> str:
    """Convert '#rrggbb' to Qt-compatible 'rgba(r,g,b,a)'."""
    h = hex_color.lstrip("#")
    r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
    return f"rgba({r},{g},{b},{alpha:.0%})"



def _pill_badge(fg: str, bg_rgba: str, border_rgba: str) -> str:
    """Precision pill badge — fixed height, semi-transparent, no vertical stretch."""
    return (
        f"color: {fg}; font-size: 10px; font-weight: 900; "
        f"background: {bg_rgba}; border: 1px solid {border_rgba}; "
        "border-radius: 4px; padding: 0px 6px; "
    )


def _type_pill(song_type: str) -> str:
    if song_type.upper() == "DX":
        return _pill_badge("#ff8c00", "rgba(255,140,0,0.14)", "rgba(255,140,0,0.30)")
    return _pill_badge("#3b82f6", "rgba(59,130,246,0.14)", "rgba(59,130,246,0.30)")


def _diff_pill(hex_color: str) -> str:
    """Difficulty pill — solid background, white text."""
    return (
        f"color: #ffffff; font-size: 10px; font-weight: bold; "
        f"background: {hex_color}; "
        "border-radius: 4px; padding: 0px 6px; "
    )


def _rate_pill(color_hex: str) -> str:
    return _pill_badge(color_hex, _hex_to_rgba(color_hex, 0.12), _hex_to_rgba(color_hex, 0.28))


def _star_pill() -> str:
    return (
        "color: #facc15; font-size: 14px; font-weight: 900; "
        "background: transparent; border: none; "
    )


def _compute_stars(dx_score: int, level_index: int) -> int:
    """Estimate DX stars from dxScore using rough max-dx per difficulty."""
    est = {0: 1500, 1: 2200, 2: 2800, 3: 3400, 4: 3800}
    max_dx = est.get(level_index, 3200)
    ratio = dx_score / max_dx if max_dx > 0 else 0
    if ratio >= 0.97: return 5
    if ratio >= 0.95: return 4
    if ratio >= 0.93: return 3
    if ratio >= 0.90: return 2
    if ratio >= 0.85: return 1
    return 0


def _rate_display(rate: str) -> str:
    m = {
        "sssp": "SSS+", "sss": "SSS", "ssp": "SS+", "ss": "SS",
        "sp": "S+", "s": "S",
        "aaap": "AAA+", "aaa": "AAA", "aap": "AA+", "aa": "AA",
        "ap": "A+", "a": "A",
    }
    return m.get(rate.lower(), rate.upper())


def _fc_badge(fc: str, fs: str) -> tuple[str, str] | None:
    """Return (label, color_key) for the best badge available.
    AP/AP+ > FC/FC+ > FS/FS+/FSD/FSD+. fc/fs fields are exact API strings."""
    fc_map = {"app": "AP+", "ap": "AP", "fcp": "FC+", "fc": "FC"}
    fs_map = {"fsdp": "FSD+", "fsd": "FSD", "fsp": "FS+", "fs": "FSD",
              "sync": "FSD+"}

    # AP/FC take priority over FS
    fc_lower = fc.lower()
    if fc_lower in fc_map:
        label = fc_map[fc_lower]
        return (label, label.lower())

    fs_lower = fs.lower()
    if fs_lower in fs_map:
        label = fs_map[fs_lower]
        # Normalize "FSD" → "fsd" for color key
        color_key = "fsd" if label.startswith("FSD") else fs_lower
        return (label, color_key)

    return None


# ── worker threads ────────────────────────────────────────────────

class FetchWorker(QThread):
    finished = pyqtSignal(dict)

    def __init__(self, qq: str = "", username: str = ""):
        super().__init__()
        self.qq = qq
        self.username = username

    def run(self) -> None:
        data = query_player(qq=self.qq, username=self.username, b50=True)
        self.finished.emit(data)


# ── SongCard — layout-driven, no absolute positioning ─────────────

class SongCard(CardWidget):
    """Dark-tech song card — diff colour strip, ds, RA panel, strict badges."""

    def __init__(self, record: dict, rank: int, parent=None):
        super().__init__(parent)
        self.setFixedSize(CARD_WIDTH, CARD_HEIGHT)
        self._record = record
        self._slide_offset = 40.0
        self._target_pos: QPoint | None = None
        self._cover_loaded = False
        self._anims: list = []

        lv_idx = record.get("level_index", 3)
        self._diff_color = DIFF_COLORS.get(lv_idx, DIFF_COLORS[3])
        self._diff_label = DIFF_LABELS.get(lv_idx, "MASTER")
        ds = record.get("ds", 0)
        song_type = record.get("type", "")
        ach = record.get("achievements", 0)
        rate = record.get("rate", "")
        ach_color = RATE_COLORS.get(rate.lower(), TEXT_PRIMARY)

        # ---- blur bg + overlay ----
        self._blur_bg = QLabel(self)
        self._blur_bg.hide()
        be = QGraphicsBlurEffect()
        be.setBlurRadius(32)
        self._blur_bg.setGraphicsEffect(be)

        self._overlay = QLabel(self)
        self._overlay.setStyleSheet(
            f"background: rgba(10, 10, 20, 0.55); "
            f"border-radius: {CARD_RADIUS}px; border: none;"
        )
        self._overlay.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents)
        self._overlay.hide()

        # ---- fade ----
        self._opacity_effect = QGraphicsOpacityEffect(self)
        self._opacity_effect.setOpacity(0.0)
        self.setGraphicsEffect(self._opacity_effect)

        # ---- card style (no left/right border — strips are widgets) ----
        self.setStyleSheet(f"""
            SongCard {{
                background: transparent;
                border-radius: {CARD_RADIUS}px;
            }}
        """)

        # ── root HBox (spacing=0, strips are the gaps) ──
        self._root = QHBoxLayout(self)
        self._root.setContentsMargins(0, 0, 0, 0)
        self._root.setSpacing(0)

        # ── LEFT strip: 3px colour bar ──
        left_strip = QWidget()
        left_strip.setFixedWidth(3)
        left_strip.setStyleSheet(
            f"background: {self._diff_color}; "
            f"border-top-left-radius: {CARD_RADIUS}px; "
            f"border-bottom-left-radius: {CARD_RADIUS}px; "
            "border: none;"
        )
        self._root.addWidget(left_strip)

        # ── Inner body ──
        inner = QWidget()
        inner.setStyleSheet("background: transparent; border: none;")
        inner_lay = QHBoxLayout(inner)
        inner_lay.setContentsMargins(7, 10, 7, 10)
        inner_lay.setSpacing(8)
        inner_lay.setAlignment(Qt.AlignmentFlag.AlignVCenter)

        # ── COVER 56×56, border-radius 6px ──
        self._cover_box = QWidget()
        self._cover_box.setFixedSize(COVER_SIZE, COVER_SIZE)
        self._cover_box.setStyleSheet(
            "background: rgba(0,0,0,0.4); border-radius: 6px; "
            "border: 1px solid rgba(255,255,255,0.08);"
        )
        cv_lay = QVBoxLayout(self._cover_box)
        cv_lay.setContentsMargins(0, 0, 0, 0)

        self._cover = QLabel()
        self._cover.setFixedSize(COVER_SIZE, COVER_SIZE)
        self._cover.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._cover.setScaledContents(False)
        cv_lay.addWidget(self._cover)

        self._cover_placeholder = QLabel("♫")
        self._cover_placeholder.setFixedSize(COVER_SIZE, COVER_SIZE)
        self._cover_placeholder.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._cover_placeholder.setStyleSheet(
            "color: #505060; font-size: 20px; background: transparent; border: none;"
        )
        cv_lay.addWidget(self._cover_placeholder)
        inner_lay.addWidget(self._cover_box)

        # ── CENTER: 3-row info area ──
        center = QVBoxLayout()
        center.setSpacing(2)
        center.setAlignment(Qt.AlignmentFlag.AlignVCenter)

        # Row 1: Title (flex: 1; min-width: 0; overflow: hidden)
        title_text = record.get("title", "???")
        self._title_lbl = QLabel(title_text)
        self._title_lbl.setStyleSheet(
            "color: #e0e0e0; font-size: 13px; font-weight: 500; "
            "background: transparent; border: none;"
        )
        self._title_lbl.setToolTip(title_text)
        self._title_lbl.setSizePolicy(
            QSizePolicy.Policy.Ignored, QSizePolicy.Policy.Preferred)
        self._title_lbl.setMinimumWidth(0)
        center.addWidget(self._title_lbl)

        # Row 2: Difficulty pill + ds
        r2 = QHBoxLayout()
        r2.setSpacing(6)
        r2.setAlignment(Qt.AlignmentFlag.AlignVCenter | Qt.AlignmentFlag.AlignLeft)

        dp = QLabel(self._diff_label)
        dp.setStyleSheet(_diff_pill(self._diff_color))
        dp.setFixedHeight(18)
        dp.setAlignment(Qt.AlignmentFlag.AlignVCenter | Qt.AlignmentFlag.AlignHCenter)
        r2.addWidget(dp)

        ds_lbl = QLabel(f"{ds:.1f}")
        ds_lbl.setStyleSheet(
            "color: #a0a0ab; font-size: 12px; background: transparent; border: none;"
        )
        r2.addWidget(ds_lbl)
        r2.addStretch(1)
        center.addLayout(r2)

        # Row 3: [DX/SD]  ach%  ---stretch---  badges
        r3 = QHBoxLayout()
        r3.setSpacing(6)
        r3.setAlignment(Qt.AlignmentFlag.AlignVCenter)

        tp = QLabel("DX" if song_type.upper() == "DX" else "SD")
        tp.setStyleSheet(_type_pill(song_type))
        tp.setFixedHeight(18)
        tp.setAlignment(Qt.AlignmentFlag.AlignVCenter | Qt.AlignmentFlag.AlignHCenter)
        r3.addWidget(tp)

        self._ach_lbl = QLabel(f"{ach:.4f}%")
        self._ach_lbl.setMinimumWidth(85)
        self._ach_lbl.setSizePolicy(
            QSizePolicy.Policy.Minimum, QSizePolicy.Policy.Fixed)
        self._ach_lbl.setStyleSheet(
            "color: #ffffff; font-size: 15px; font-weight: 600; "
            "background: transparent; border: none;"
        )
        self._ach_lbl.setAlignment(
            Qt.AlignmentFlag.AlignVCenter | Qt.AlignmentFlag.AlignRight)
        r3.addWidget(self._ach_lbl)

        r3.addStretch(1)

        rate_text = _rate_display(rate)
        if rate_text:
            rb = QLabel(rate_text)
            rb.setStyleSheet(_rate_pill(ach_color))
            rb.setFixedHeight(18)
            rb.setAlignment(Qt.AlignmentFlag.AlignVCenter | Qt.AlignmentFlag.AlignHCenter)
            r3.addWidget(rb)

        dx_score = record.get("dxScore", 0)
        star_count = _compute_stars(dx_score, lv_idx)
        if star_count > 0:
            sl = QLabel("★" * star_count)
            sl.setStyleSheet(_star_pill())
            sl.setAlignment(Qt.AlignmentFlag.AlignVCenter | Qt.AlignmentFlag.AlignHCenter)
            r3.addWidget(sl)

        badge_info = _fc_badge(record.get("fc", ""), record.get("fs", ""))
        if badge_info:
            blabel, color_key = badge_info
            short = {"AP+": "AP+", "AP": "AP", "FC+": "FC+", "FC": "FC",
                     "FSD+": "SYNC", "FSD": "SYNC", "FS+": "SYNC"}
            blabel = short.get(blabel, blabel)
            bcolor = FC_BADGE_COLORS.get(color_key, TEXT_PRIMARY)
            fb = QLabel(blabel)
            fb.setStyleSheet(_rate_pill(bcolor))
            fb.setFixedHeight(18)
            fb.setAlignment(Qt.AlignmentFlag.AlignVCenter | Qt.AlignmentFlag.AlignHCenter)
            r3.addWidget(fb)

        center.addLayout(r3)
        inner_lay.addLayout(center, 1)

        # ── Rating value (right, flex-shrink: 0) ──
        ra = record.get("ra", 0)
        rating_lbl = QLabel(str(ra))
        rating_lbl.setFixedWidth(54)
        rating_lbl.setSizePolicy(
            QSizePolicy.Policy.Fixed, QSizePolicy.Policy.Fixed)
        rating_lbl.setAlignment(
            Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
        rating_lbl.setStyleSheet(
            "color: #22d3ee; font-size: 22px; font-weight: bold; "
            "background: transparent; border: none;"
        )
        inner_lay.addWidget(rating_lbl)

        self._root.addWidget(inner, 1)

        # ── RIGHT strip: 3px colour bar ──
        right_strip = QWidget()
        right_strip.setFixedWidth(3)
        right_strip.setStyleSheet(
            f"background: {self._diff_color}; "
            f"border-top-right-radius: {CARD_RADIUS}px; "
            f"border-bottom-right-radius: {CARD_RADIUS}px; "
            "border: none;"
        )
        self._root.addWidget(right_strip)

        QTimer.singleShot(0, self._elide_title)

    # ── cover loading ──────────────────────────────────────────

    def load_cover(self, scan_only: bool = False) -> None:
        song_id = self._record.get("song_id", 0)
        if not song_id:
            return
        path = cover_path(song_id)
        if has_cover(song_id):
            self._apply_cover(path)
        elif not scan_only:
            path = download_cover_sync(song_id)
            if path:
                self._apply_cover(path)

    def _apply_cover(self, path: str) -> None:
        pix = QPixmap(path)
        if pix.isNull():
            return
        self._cover_loaded = True

        # blur background
        scaled_bg = pix.scaled(
            CARD_WIDTH, CARD_HEIGHT,
            Qt.AspectRatioMode.KeepAspectRatioByExpanding,
            Qt.TransformationMode.SmoothTransformation,
        )
        self._blur_bg.setPixmap(scaled_bg)
        self._blur_bg.setScaledContents(True)
        self._blur_bg.setGeometry(0, 0, CARD_WIDTH, CARD_HEIGHT)
        self._clip_rounded(self._blur_bg)
        self._blur_bg.show()
        self._overlay.setGeometry(0, 0, CARD_WIDTH, CARD_HEIGHT)
        self._overlay.show()

        # cover thumbnail — square crop
        thumb = pix.scaled(
            COVER_SIZE, COVER_SIZE,
            Qt.AspectRatioMode.KeepAspectRatioByExpanding,
            Qt.TransformationMode.SmoothTransformation,
        )
        if thumb.width() > COVER_SIZE or thumb.height() > COVER_SIZE:
            x = (thumb.width() - COVER_SIZE) // 2
            y = (thumb.height() - COVER_SIZE) // 2
            thumb = thumb.copy(x, y, COVER_SIZE, COVER_SIZE)
        self._cover.setPixmap(thumb)
        self._cover.setScaledContents(False)
        self._cover_placeholder.hide()

    def _clip_rounded(self, widget: QWidget) -> None:
        from PyQt6.QtGui import QBitmap, QPainter as QP2
        bmp = QBitmap(CARD_WIDTH, CARD_HEIGHT)
        bmp.clear()
        p = QP2(bmp)
        p.setRenderHint(QP2.RenderHint.Antialiasing)
        p.setBrush(Qt.GlobalColor.color1)
        p.setPen(Qt.PenStyle.NoPen)
        p.drawRoundedRect(QRectF(0, 0, CARD_WIDTH, CARD_HEIGHT), CARD_RADIUS, CARD_RADIUS)
        p.end()
        widget.setMask(bmp)

    # ── title elide ────────────────────────────────────────────

    def _elide_title(self) -> None:
        fm = QFontMetrics(self._title_lbl.font())
        avail = self._title_lbl.width()
        title = self._record.get("title", "???")
        elided = fm.elidedText(title, Qt.TextElideMode.ElideRight, avail)
        self._title_lbl.setText(elided)

    def resizeEvent(self, event) -> None:
        super().resizeEvent(event)
        self._elide_title()

    # ── animation properties ───────────────────────────────────

    @pyqtProperty(float)
    def slideOffset(self) -> float:
        return self._slide_offset

    @slideOffset.setter
    def slideOffset(self, value: float) -> None:
        self._slide_offset = value
        if self._target_pos is not None:
            self.move(
                self._target_pos.x(),
                self._target_pos.y() + int(value),
            )

    @pyqtProperty(float)
    def cardOpacity(self) -> float:
        return self._opacity_effect.opacity()

    @cardOpacity.setter
    def cardOpacity(self, value: float) -> None:
        self._opacity_effect.setOpacity(value)


# ── section header ────────────────────────────────────────────────

class SectionHeader(QWidget):
    def __init__(self, title: str, parent=None):
        super().__init__(parent)
        self._layout = QHBoxLayout(self)
        self._layout.setContentsMargins(0, 0, 0, 4)

        dot = QLabel("◆")
        dot.setStyleSheet(f"color: {ACCENT}; font-size: 12px; background: transparent;")
        self._layout.addWidget(dot)

        self._title_lbl = SubtitleLabel(title)
        self._title_lbl.setStyleSheet(f"color: {TEXT_PRIMARY}; font-size: 15px; font-weight: bold;")
        self._layout.addWidget(self._title_lbl)

        self._count_lbl = BodyLabel("(0)")
        self._count_lbl.setStyleSheet(f"color: {TEXT_DIM}; font-size: 12px;")
        self._layout.addWidget(self._count_lbl)

        self._layout.addStretch(1)

        self._total_lbl = QLabel("Σ 0")
        self._total_lbl.setStyleSheet(
            f"color: {ACCENT}; font-size: 13px; font-weight: bold; "
            "background: transparent; border: none;"
        )
        self._layout.addWidget(self._total_lbl)

    def set_count(self, count: int) -> None:
        self._count_lbl.setText(f"({count})")

    def set_total(self, total: int) -> None:
        self._total_lbl.setText(f"Σ {total}")


# ── animated song grid ───────────────────────────────────────────

class AnimatedSongGrid(QWidget):
    """Manual grid layout with staggered entrance animations."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self._cards: list[SongCard] = []

    def populate(self, records: list[dict], cover_ids: set[int] | None = None) -> None:
        self._clear()
        if not records:
            return

        cover_ids = cover_ids or set()

        available_w = self.parent().width() - 56 if self.parent() else 1100
        cols = max(1, (available_w + GAP) // (CARD_WIDTH + GAP))

        for i, rec in enumerate(records):
            col = i % cols
            row = i // cols
            x = col * (CARD_WIDTH + GAP)
            y = row * (CARD_HEIGHT + GAP)

            card = SongCard(rec, i + 1, self)
            card._target_pos = QPoint(x, y)
            card.move(x, y + 40)
            card.cardOpacity = 0.0
            card.show()

            sid = rec.get("song_id", 0)
            if sid and sid in cover_ids and has_cover(sid):
                card.load_cover(scan_only=True)

            self._cards.append(card)

        total_h = ((len(records) - 1) // cols + 1) * (CARD_HEIGHT + GAP)
        self.setMinimumHeight(total_h)

    def animate_in(self, delay_ms: int = 25) -> None:
        for i, card in enumerate(self._cards):
            QTimer.singleShot(i * delay_ms, lambda c=card: self._start_card_anim(c))

    def _start_card_anim(self, card: SongCard) -> None:
        slide = QPropertyAnimation(card, b"slideOffset")
        slide.setDuration(420)
        slide.setStartValue(card.slideOffset)
        slide.setEndValue(0.0)
        slide.setEasingCurve(QEasingCurve.Type.OutCubic)

        fade = QPropertyAnimation(card, b"cardOpacity")
        fade.setDuration(380)
        fade.setStartValue(0.0)
        fade.setEndValue(1.0)
        fade.setEasingCurve(QEasingCurve.Type.OutCubic)

        card._anims.extend([slide, fade])
        slide.start()
        fade.start()

    def _clear(self) -> None:
        for card in self._cards:
            card.deleteLater()
        self._cards.clear()

    def cards(self) -> list[SongCard]:
        return self._cards


# ── main dashboard ────────────────────────────────────────────────

class DashboardInterface(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setObjectName("DashboardInterface")
        self._fetch_worker: FetchWorker | None = None
        self._cover_worker: BulkCoverWorker | None = None
        self._data: dict | None = None
        self._qq = "869920298"

        init_b50_table()

        self.outer = QScrollArea(self)
        self.outer.setWidgetResizable(True)
        self.outer.setFrameShape(QFrame.Shape.NoFrame)
        self.outer.setStyleSheet(f"QScrollArea {{ background: {BG_DARK}; border: none; }}")

        self.container = QWidget()
        self.container.setStyleSheet(f"background: {BG_DARK};")
        self.outer.setWidget(self.container)

        self.page_layout = QVBoxLayout(self.container)
        self.page_layout.setContentsMargins(28, 20, 28, 28)
        self.page_layout.setSpacing(16)

        self._build_top_bar()
        self._build_player_row()
        self._build_b35()
        self._build_b15()
        self.page_layout.addStretch(1)

        main_layout = QVBoxLayout(self)
        main_layout.setContentsMargins(0, 0, 0, 0)
        main_layout.addWidget(self.outer)

        self._show_placeholder()
        QTimer.singleShot(50, self._load_cached_first)

    # ── builders ────────────────────────────────────────────────

    def _build_top_bar(self) -> None:
        bar = QHBoxLayout()

        title = SubtitleLabel("数据看板")
        title.setStyleSheet("font-size: 26px; font-weight: bold; color: #e0e0e0;")
        bar.addWidget(title)
        bar.addStretch(1)

        self._updated_lbl = BodyLabel("")
        self._updated_lbl.setStyleSheet(f"color: {TEXT_DIM}; font-size: 11px; background: transparent;")
        bar.addWidget(self._updated_lbl)
        bar.addSpacing(12)

        self.fetch_btn = PushButton(FIF.SEARCH, "刷新数据")
        self.fetch_btn.setMinimumHeight(36)
        self.fetch_btn.clicked.connect(self._on_fetch)
        bar.addWidget(self.fetch_btn)

        self.spinner = IndeterminateProgressRing(self)
        self.spinner.setFixedSize(24, 24)
        self.spinner.hide()
        bar.addWidget(self.spinner)

        self.page_layout.addLayout(bar)

    def _build_player_row(self) -> None:
        row = QHBoxLayout()
        row.setSpacing(16)

        self.player_card = CardWidget()
        self.player_card.setMinimumHeight(110)
        self.player_card.setStyleSheet(f"CardWidget {{ {_card_sheet()} }}")
        pl = QVBoxLayout(self.player_card)
        pl.setContentsMargins(22, 16, 22, 16)
        pl.setSpacing(6)

        self.nickname_lbl = BodyLabel("—")
        self.nickname_lbl.setStyleSheet(
            f"color: {ACCENT}; font-size: 20px; font-weight: bold; background: transparent;"
        )
        pl.addWidget(self.nickname_lbl)

        self.qq_lbl = BodyLabel("")
        self.qq_lbl.setStyleSheet(f"color: {TEXT_DIM}; font-size: 12px; background: transparent;")
        pl.addWidget(self.qq_lbl)
        pl.addStretch(1)
        row.addWidget(self.player_card, 3)

        self.rating_card = CardWidget()
        self.rating_card.setFixedWidth(170)
        self.rating_card.setStyleSheet(f"CardWidget {{ {_card_sheet()} }}")
        rl = QVBoxLayout(self.rating_card)
        rl.setContentsMargins(20, 16, 20, 16)
        rl.setSpacing(4)

        rlabel = BodyLabel("Rating")
        rlabel.setStyleSheet(f"color: {TEXT_DIM}; font-size: 11px; background: transparent;")
        rl.addWidget(rlabel)

        self.rating_value = QLabel("—")
        self.rating_value.setStyleSheet(
            f"color: {ACCENT}; font-size: 36px; font-weight: bold; background: transparent;"
        )
        rl.addWidget(self.rating_value)
        rl.addStretch(1)
        row.addWidget(self.rating_card, 1)

        self.plate_card = CardWidget()
        self.plate_card.setFixedWidth(150)
        self.plate_card.setStyleSheet(f"CardWidget {{ {_card_sheet()} }}")
        pbl = QVBoxLayout(self.plate_card)
        pbl.setContentsMargins(20, 16, 20, 16)
        pbl.setSpacing(4)

        plabel = BodyLabel("段位")
        plabel.setStyleSheet(f"color: {TEXT_DIM}; font-size: 11px; background: transparent;")
        pbl.addWidget(plabel)

        self.plate_value = SubtitleLabel("—")
        self.plate_value.setStyleSheet(
            f"color: {TEXT_PRIMARY}; font-size: 18px; font-weight: bold; background: transparent;"
        )
        pbl.addWidget(self.plate_value)
        pbl.addStretch(1)
        row.addWidget(self.plate_card, 1)

        self.page_layout.addLayout(row)

    def _build_b35(self) -> None:
        self.b35_header = SectionHeader("B35 · 旧曲 Best 35")
        self.b35_header.hide()
        self.page_layout.addWidget(self.b35_header)

        self.b35_grid = AnimatedSongGrid()
        self.b35_grid.hide()
        self.page_layout.addWidget(self.b35_grid)

    def _build_b15(self) -> None:
        self.b15_header = SectionHeader("B15 · 新曲 Best 15")
        self.b15_header.hide()
        self.page_layout.addWidget(self.b15_header)

        self.b15_grid = AnimatedSongGrid()
        self.b15_grid.hide()
        self.page_layout.addWidget(self.b15_grid)

    # ── visibility ─────────────────────────────────────────────

    def _show_placeholder(self) -> None:
        self.player_card.hide()
        self.rating_card.hide()
        self.plate_card.hide()
        self.b35_header.hide()
        self.b35_grid.hide()
        self.b15_header.hide()
        self.b15_grid.hide()

    def _show_data(self) -> None:
        self.player_card.show()
        self.rating_card.show()
        self.plate_card.show()
        self.b35_header.show()
        self.b35_grid.show()
        self.b15_header.show()
        self.b15_grid.show()

    # ── offline-first load ─────────────────────────────────────

    def _load_cached_first(self) -> None:
        cached = load_b50_from_db(qq=self._qq)
        if cached:
            self._render(cached, from_cache=True)
        self._background_refresh()

    def _background_refresh(self) -> None:
        self._fetch_worker = FetchWorker(qq=self._qq)
        self._fetch_worker.finished.connect(self._on_bg_data)
        self._fetch_worker.start()

    def _on_bg_data(self, data: dict) -> None:
        if "error" in data:
            if self._data is None:
                InfoBar.error("查询失败", data["error"], duration=5000,
                              position=InfoBarPosition.TOP_RIGHT, parent=self)
            else:
                self._updated_lbl.setText("(离线模式)")
            return

        new_rating = data.get("rating", 0)
        old_rating = (self._data or {}).get("rating", 0)

        if new_rating != old_rating or self._data is None:
            self._render(data, from_cache=False)

    # ── manual fetch ───────────────────────────────────────────

    def _on_fetch(self) -> None:
        self.fetch_btn.setEnabled(False)
        self.spinner.show()

        self._fetch_worker = FetchWorker(qq=self._qq)
        self._fetch_worker.finished.connect(self._on_data_arrived)
        self._fetch_worker.start()

    def _on_data_arrived(self, data: dict) -> None:
        self.fetch_btn.setEnabled(True)
        self.spinner.hide()

        if "error" in data:
            InfoBar.error("查询失败", data["error"], duration=5000,
                          position=InfoBarPosition.TOP_RIGHT, parent=self)
            return

        self._render(data, from_cache=False)

    # ── render ─────────────────────────────────────────────────

    def _render(self, data: dict, from_cache: bool = False) -> None:
        self._data = data
        self._show_data()

        nickname = data.get("nickname", "") or data.get("username", "")
        username = data.get("username", "")
        self.nickname_lbl.setText(nickname)
        self.qq_lbl.setText(f"@{username}" if username else "")

        self.rating_value.setText(str(data.get("rating", 0)))
        self.plate_value.setText(data.get("plate", "") or "无段位")

        now = datetime.now().strftime("%H:%M")
        self._updated_lbl.setText(f"缓存于 {now}" if from_cache else f"已更新 {now}")

        charts = data.get("charts", {})
        sd = charts.get("sd", [])
        dx = charts.get("dx", [])

        all_records = sd + dx
        cached_covers: set[int] = set()
        for rec in all_records:
            sid = rec.get("song_id", 0)
            if sid and has_cover(sid):
                cached_covers.add(sid)

        b35_total = sum(r.get("ra", 0) for r in sd)
        self.b35_header.set_count(len(sd))
        self.b35_header.set_total(b35_total)
        self.b35_grid.populate(sd, cached_covers)
        if from_cache:
            for card in self.b35_grid.cards():
                card.cardOpacity = 1.0
                card.slideOffset = 0.0
        else:
            self.b35_grid.animate_in(delay_ms=25)

        b15_total = sum(r.get("ra", 0) for r in dx)
        self.b15_header.set_count(len(dx))
        self.b15_header.set_total(b15_total)
        self.b15_grid.populate(dx, cached_covers)
        if from_cache:
            for card in self.b15_grid.cards():
                card.cardOpacity = 1.0
                card.slideOffset = 0.0
        else:
            self.b15_grid.animate_in(delay_ms=25)

        missing_ids = [
            rec.get("song_id", 0) for rec in all_records
            if rec.get("song_id", 0) and rec["song_id"] not in cached_covers
        ]
        if missing_ids:
            self._start_cover_download(missing_ids)

    def _start_cover_download(self, song_ids: list[int]) -> None:
        self._cover_worker = BulkCoverWorker(song_ids)
        self._cover_worker.cover_done.connect(self._on_cover_ready)
        self._cover_worker.start()

    def _on_cover_ready(self, song_id: int, path: str) -> None:
        for grid in (self.b35_grid, self.b15_grid):
            for card in grid.cards():
                if card._record.get("song_id") == song_id and not card._cover_loaded:
                    card.load_cover(scan_only=True)

    # ── resize: re-layout cards ────────────────────────────────

    def resizeEvent(self, event) -> None:
        super().resizeEvent(event)
        if self._data:
            charts = self._data.get("charts", {})
            sd = charts.get("sd", [])
            dx = charts.get("dx", [])

            available_w = self.width() - 56
            cols = max(1, (available_w + GAP) // (CARD_WIDTH + GAP))

            for records, grid in ((sd, self.b35_grid), (dx, self.b15_grid)):
                for i, card in enumerate(grid.cards()):
                    col = i % cols
                    row = i // cols
                    x = col * (CARD_WIDTH + GAP)
                    y = row * (CARD_HEIGHT + GAP)
                    card._target_pos = QPoint(x, y)
                    card.move(x, y)

                total_h = ((len(records) - 1) // cols + 1) * (CARD_HEIGHT + GAP)
                grid.setMinimumHeight(total_h)

    def load_data(self, data: dict) -> None:
        self._on_data_arrived(data)
