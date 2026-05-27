import sys
import os

from PyQt6.QtCore import Qt
from PyQt6.QtGui import QIcon
from PyQt6.QtWidgets import QApplication

if getattr(sys, 'frozen', False):
    data_dir = sys._MEIPASS
else:
    data_dir = os.path.dirname(os.path.abspath(__file__))

from ui_main import MainWindow


if __name__ == '__main__':
    try:
        import ctypes
        ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID(
            'daozhu.fluentmai.v1'
        )
    except Exception:
        pass

    QApplication.setHighDpiScaleFactorRoundingPolicy(
        Qt.HighDpiScaleFactorRoundingPolicy.PassThrough
    )
    QApplication.setAttribute(Qt.ApplicationAttribute.AA_EnableHighDpiScaling)
    QApplication.setAttribute(Qt.ApplicationAttribute.AA_UseHighDpiPixmaps)

    app = QApplication(sys.argv)
    app.setWindowIcon(QIcon(os.path.join(data_dir, "assets", "logo.ico")))
    window = MainWindow()
    window.show()
    sys.exit(app.exec())
