import sys
import os

if getattr(sys, 'frozen', False):
    data_dir = sys._MEIPASS
else:
    data_dir = os.path.dirname(os.path.abspath(__file__))

from PyQt6.QtGui import QPixmap, QIcon
from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtWidgets import QWidget, QVBoxLayout, QHBoxLayout, QLabel, QSizePolicy
from qfluentwidgets import (FluentWindow, FluentIcon as FIF,
                            NavigationItemPosition, Theme, setTheme, qconfig)

from i18n import i18n
from ui_home import HomeInterface
from ui_settings import SettingsInterface
from ui_about import AboutInterface


class BrandingWidget(QWidget):
    """Logo + title in the navigation sidebar."""

    clicked = pyqtSignal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setFixedHeight(48)
        self.layout = QHBoxLayout(self)
        self.layout.setContentsMargins(16, 12, 0, 0)
        self.layout.setAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignTop)

        self.icon_label = QLabel(self)
        self.icon_label.setStyleSheet("background: transparent; border: none;")
        self.icon_label.setSizePolicy(QSizePolicy.Policy.Fixed, QSizePolicy.Policy.Fixed)

        logo_path = os.path.join(data_dir, "assets", "logo.png")
        if not os.path.exists(logo_path):
            logo_path = os.path.join(data_dir, "assets", "logo.jpg")
        if os.path.exists(logo_path):
            pixmap = QPixmap(logo_path)
            dpr = self.devicePixelRatioF()
            scaled = pixmap.scaledToHeight(int(22 * dpr), Qt.TransformationMode.SmoothTransformation)
            scaled.setDevicePixelRatio(dpr)
            self.icon_label.setPixmap(scaled)

        self.title_label = QLabel("FluentMai", self)
        self.title_label.setStyleSheet(
            "font-size: 15px; font-weight: bold; color: white; "
            "background: transparent; margin-left: 10px;"
        )
        self.title_label.setWordWrap(False)
        self.title_label.setSizePolicy(QSizePolicy.Policy.Minimum, QSizePolicy.Policy.Preferred)

        self.layout.addWidget(self.icon_label)
        self.layout.addWidget(self.title_label)

    def setSelected(self, selected: bool):
        pass

    def setCompacted(self, compacted: bool):
        pass


class MainWindow(FluentWindow):
    """FluentMai main window — sidebar navigation + stacked pages."""

    def __init__(self):
        super().__init__()

        setTheme(Theme.DARK)
        qconfig.set(qconfig.themeMode, Theme.DARK)

        self.setWindowTitle("FluentMai")
        self.setWindowIcon(QIcon(os.path.join(data_dir, "assets", "logo.ico")))
        self.resize(1000, 660)
        self.setMinimumSize(900, 600)

        self.navigationInterface.setReturnButtonVisible(False)
        self.navigationInterface.setExpandWidth(207)

        if hasattr(self.titleBar, 'titleLabel'):
            self.titleBar.titleLabel.hide()
        if hasattr(self.titleBar, 'iconLabel'):
            self.titleBar.iconLabel.hide()

        try:
            nav_panel = self.navigationInterface.panel
            nav_panel.vBoxLayout.removeWidget(nav_panel.menuButton)
            nav_panel.menuButton.hide()
            nav_panel.menuButton.setParent(None)
        except Exception:
            pass

        self.navigationInterface.panel.setMinimumExpandWidth(800)

        self.branding_widget = BrandingWidget(self)
        self.navigationInterface.addWidget(
            routeKey='branding', widget=self.branding_widget,
            onClick=None, position=NavigationItemPosition.TOP
        )

        self.home_interface = HomeInterface(self)
        self.settings_interface = SettingsInterface(self)
        self.about_interface = AboutInterface(self)

        self.addSubInterface(self.home_interface, FIF.SYNC, i18n.tr("tab_home"))
        self.addSubInterface(
            self.settings_interface, FIF.SETTING, i18n.tr("tab_settings"),
            position=NavigationItemPosition.BOTTOM
        )
        self.addSubInterface(
            self.about_interface, FIF.HELP, i18n.tr("tab_about"),
            position=NavigationItemPosition.BOTTOM
        )

        self.navigationInterface.expand()
