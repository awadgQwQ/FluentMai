from PyQt6.QtCore import Qt, QUrl
from PyQt6.QtGui import QPixmap, QIcon, QDesktopServices
from PyQt6.QtWidgets import QWidget, QVBoxLayout, QHBoxLayout, QLabel
from qfluentwidgets import (SubtitleLabel, BodyLabel, CardWidget,
                            PushButton, FluentIcon as FIF, ScrollArea)

from i18n import i18n
import os, sys

if getattr(sys, 'frozen', False):
    data_dir = sys._MEIPASS
else:
    data_dir = os.path.dirname(os.path.abspath(__file__))


def _scale_pixmap_to_height(pixmap, target_height, widget):
    return pixmap.scaledToHeight(target_height, Qt.TransformationMode.SmoothTransformation)


class AboutInterface(ScrollArea):
    """About page — FluentMai info and contact links."""

    def __init__(self, parent=None):
        super().__init__(parent=parent)
        self.setObjectName("AboutInterface")
        self.view = QWidget(self)
        self.layout = QVBoxLayout(self.view)
        self.layout.setContentsMargins(24, 24, 24, 24)
        self.layout.setSpacing(20)
        self.setWidget(self.view)
        self.setWidgetResizable(True)
        self.setStyleSheet("QScrollArea{background: transparent; border: none}")

        # ---- Top card ----
        top_card = CardWidget()
        top_layout = QHBoxLayout(top_card)
        top_layout.setContentsMargins(20, 20, 20, 20)
        top_layout.setSpacing(15)

        logo_label = QLabel()
        logo_label.setStyleSheet("background: transparent;")
        logo_path = os.path.join(data_dir, "assets", "logo.png")
        if os.path.exists(logo_path):
            pixmap = QPixmap(logo_path)
            if not pixmap.isNull():
                logo_label.setPixmap(_scale_pixmap_to_height(pixmap, 60, self))
        top_layout.addWidget(logo_label)

        info_layout = QVBoxLayout()
        info_layout.setSpacing(5)
        name_lbl = SubtitleLabel("FluentMai")
        name_lbl.setStyleSheet("font-size: 20px; font-weight: bold;")
        ver_lbl = BodyLabel(i18n.tr("about_ver"))
        ver_lbl.setStyleSheet("color: #a0a0a0;")
        info_layout.addWidget(name_lbl)
        info_layout.addWidget(ver_lbl)
        info_layout.addStretch(1)
        top_layout.addLayout(info_layout)
        top_layout.addStretch(1)

        def _make_btn(icon_name, text_key, fallback_icon, url):
            btn = PushButton(i18n.tr(text_key))
            icon_path = os.path.join(data_dir, "assets", icon_name)
            if os.path.exists(icon_path):
                pix = QPixmap(icon_path)
                if not pix.isNull():
                    btn.setIcon(QIcon(_scale_pixmap_to_height(pix, 18, self)))
                else:
                    btn.setIcon(fallback_icon)
            else:
                btn.setIcon(fallback_icon)
            btn.clicked.connect(
                lambda checked, u=url: QDesktopServices.openUrl(QUrl(u))
            )
            return btn

        top_layout.addWidget(_make_btn("github.png", "btn_github", FIF.SHARE,
                                        "https://github.com/Daozhu1007/FluentMai"))
        top_layout.addWidget(_make_btn("bilibili.png", "btn_bilibili", FIF.SHARE,
                                        "https://space.bilibili.com/477852567"))
        top_layout.addWidget(_make_btn("", "btn_qq", FIF.CHAT,
                                        "https://qm.qq.com/your-group-link"))
        top_layout.addWidget(_make_btn("", "btn_donate", FIF.HEART,
                                        "https://afdian.com/a/Limitime"))
        self.layout.addWidget(top_card)

        # ---- Author card ----
        author_title = SubtitleLabel(i18n.tr("about_author_title"))
        author_title.setStyleSheet("font-size: 22px; font-weight: bold; margin-top: 10px;")
        self.layout.addWidget(author_title)

        author_card = CardWidget()
        author_layout = QVBoxLayout(author_card)
        author_layout.setContentsMargins(20, 20, 20, 20)
        author_layout.setSpacing(10)

        for key in ("about_author", "about_desc", "about_email", "about_qq"):
            lbl = BodyLabel(i18n.tr(key))
            if key == "about_author":
                lbl.setStyleSheet("font-size: 16px; font-weight: bold;")
            else:
                lbl.setStyleSheet("color: #a0a0a0; font-size: 14px;")
            lbl.setWordWrap(True)
            author_layout.addWidget(lbl)
        self.layout.addWidget(author_card)

        self.layout.addStretch(1)

        # ---- Warnings ----
        warn_container = QVBoxLayout()
        warn_container.setSpacing(6)
        warn_container.setContentsMargins(0, 0, 0, 0)
        for key in ("about_warn1", "about_warn2"):
            warn = BodyLabel(i18n.tr(key))
            warn.setStyleSheet("color: #ff5252; font-weight: bold; font-size: 14px;")
            warn.setWordWrap(True)
            warn_container.addWidget(warn)
        self.layout.addLayout(warn_container)
