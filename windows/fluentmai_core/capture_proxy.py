from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import os
import subprocess
from typing import Protocol


@dataclass(frozen=True)
class ProxySnapshot:
    proxy_enable: int | None
    proxy_server: str | None
    auto_config_url: str | None


class SystemProxyBackend(Protocol):
    def snapshot(self) -> ProxySnapshot: ...
    def apply(self, proxy_server: str) -> None: ...
    def restore(self, snapshot: ProxySnapshot) -> None: ...


class NoopSystemProxyBackend:
    def __init__(self):
        self.last_applied: str | None = None
        self.restored: ProxySnapshot | None = None

    def snapshot(self) -> ProxySnapshot:
        return ProxySnapshot(None, None, None)

    def apply(self, proxy_server: str) -> None:
        self.last_applied = proxy_server

    def restore(self, snapshot: ProxySnapshot) -> None:
        self.restored = snapshot


class WindowsRegistryProxyBackend:
    """Small, testable wrapper around Windows Internet Settings.

    The UI does not call this directly yet. It exists so the automatic capture
    path can be wired without mixing registry writes into parsing/import tests.
    """

    REG_PATH = r"Software\Microsoft\Windows\CurrentVersion\Internet Settings"

    def __init__(self, *, allow_system_changes: bool = False):
        self.allow_system_changes = allow_system_changes

    def snapshot(self) -> ProxySnapshot:
        import winreg

        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, self.REG_PATH, 0, winreg.KEY_READ) as key:
            return ProxySnapshot(
                proxy_enable=_read_reg_value(key, "ProxyEnable"),
                proxy_server=_read_reg_value(key, "ProxyServer"),
                auto_config_url=_read_reg_value(key, "AutoConfigURL"),
            )

    def apply(self, proxy_server: str) -> None:
        if not self.allow_system_changes:
            raise PermissionError("System proxy changes are disabled for this backend instance.")
        import winreg

        with winreg.CreateKeyEx(winreg.HKEY_CURRENT_USER, self.REG_PATH, 0, winreg.KEY_SET_VALUE) as key:
            winreg.SetValueEx(key, "ProxyEnable", 0, winreg.REG_DWORD, 1)
            winreg.SetValueEx(key, "ProxyServer", 0, winreg.REG_SZ, proxy_server)
            try:
                winreg.DeleteValue(key, "AutoConfigURL")
            except FileNotFoundError:
                pass

    def restore(self, snapshot: ProxySnapshot) -> None:
        if not self.allow_system_changes:
            raise PermissionError("System proxy changes are disabled for this backend instance.")
        import winreg

        with winreg.CreateKeyEx(winreg.HKEY_CURRENT_USER, self.REG_PATH, 0, winreg.KEY_SET_VALUE) as key:
            if snapshot.proxy_enable is not None:
                winreg.SetValueEx(key, "ProxyEnable", 0, winreg.REG_DWORD, int(snapshot.proxy_enable))
            if snapshot.proxy_server is not None:
                winreg.SetValueEx(key, "ProxyServer", 0, winreg.REG_SZ, snapshot.proxy_server)
            if snapshot.auto_config_url is not None:
                winreg.SetValueEx(key, "AutoConfigURL", 0, winreg.REG_SZ, snapshot.auto_config_url)


class CaptureHelperController:
    def __init__(
        self,
        helper_path: str | None = None,
        backend: SystemProxyBackend | None = None,
    ):
        self.helper_path = helper_path or os.environ.get("FLUENTMAI_PROXY_HELPER", "")
        self.backend = backend or NoopSystemProxyBackend()
        self.process: subprocess.Popen | None = None
        self.snapshot: ProxySnapshot | None = None

    def find_helper(self) -> str | None:
        if self.helper_path and Path(self.helper_path).exists():
            return self.helper_path
        cwd = Path.cwd()
        for candidate in cwd.glob("maimaidx-prober-proxy-windows*.exe"):
            return str(candidate)
        return None

    def start(
        self,
        *,
        config_path: str,
        addr: str = "127.0.0.1:8033",
        edit_system_proxy: bool = False,
    ) -> None:
        helper = self.find_helper()
        if not helper:
            raise FileNotFoundError("Official maimaidx-prober proxy helper executable was not found.")
        if self.process and self.process.poll() is None:
            raise RuntimeError("Capture helper is already running.")
        args = [
            helper,
            "-config",
            config_path,
            "-addr",
            addr,
        ]
        if not edit_system_proxy:
            args.append("-no-edit-global-proxy")
        else:
            self.snapshot = self.backend.snapshot()
            self.backend.apply(addr)
        self.process = subprocess.Popen(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)

    def stop(self) -> None:
        if self.process and self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=5)
        if self.snapshot is not None:
            self.backend.restore(self.snapshot)
            self.snapshot = None


def _read_reg_value(key, name: str):
    import winreg

    try:
        return winreg.QueryValueEx(key, name)[0]
    except FileNotFoundError:
        return None
