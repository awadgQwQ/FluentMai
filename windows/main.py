import sys
import os

from PyQt6.QtCore import Qt, QTimer
from PyQt6.QtGui import QIcon
from PyQt6.QtWidgets import QApplication

if getattr(sys, 'frozen', False):
    data_dir = sys._MEIPASS
else:
    data_dir = os.path.dirname(os.path.abspath(__file__))

from ui_main import MainWindow
from ui_tokens import apply_app_style


if __name__ == '__main__':
    smoke_test = "--smoke-test" in sys.argv or os.environ.get("FLUENTMAI_SMOKE_TEST") == "1"
    qt_args = [arg for arg in sys.argv if arg != "--smoke-test"]
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

    app = QApplication(qt_args)
    apply_app_style(app)
    app.setWindowIcon(QIcon(os.path.join(data_dir, "assets", "logo.ico")))
    window = MainWindow()
    window.show()
    if smoke_test:
        QTimer.singleShot(3000, app.quit)
    sys.exit(app.exec())
