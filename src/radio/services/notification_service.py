import platform
import subprocess
from src.radio.services.settings_service import SettingsService

class NotificationService:
    def __init__(self, settings_service: SettingsService):
        self.settings_service = settings_service
        self.os_name = platform.system().lower()

    def is_enabled(self) -> bool:
        return self.settings_service.is_notifications_enabled()

    def set_enabled(self, enabled: bool):
        self.settings_service.set_notifications_enabled(enabled)

    def notify(self, station_name: str, song_title: str):
        if not self.is_enabled() or not song_title or not song_title.strip():
            return

        if "darwin" in self.os_name:
            self._send_mac(station_name, song_title)
        elif "linux" in self.os_name:
            self._send_linux(station_name, song_title)

    def _send_mac(self, station_name: str, song_title: str):
        safe_station = self._escape(station_name)
        safe_song = self._escape(song_title)
        script = f'display notification "{safe_song}" with title "{safe_station}"'
        try:
            subprocess.Popen(
                ["osascript", "-e", script],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL
            )
        except Exception:
            pass

    def _send_linux(self, station_name: str, song_title: str):
        try:
            subprocess.Popen(
                ["notify-send", station_name, song_title],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL
            )
        except Exception:
            pass

    def _escape(self, value: str) -> str:
        if not value:
            return ""
        return value.replace("\\", "\\\\").replace('"', '\\"')
