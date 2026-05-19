import json
import os
from pathlib import Path
from src.radio.config import RadioConfig
from src.radio.models import UserSettings

class SettingsService:
    def __init__(self, config: RadioConfig):
        self.config = config
        self.settings = UserSettings.defaults()
        self.load()

    def get_volume(self) -> int:
        return self.settings.volume

    def set_volume(self, volume: int):
        self.settings = self.settings.with_volume(volume)
        self.save()

    def get_last_station_id(self) -> str:
        return self.settings.lastStationId

    def set_last_station_id(self, station_id: str):
        self.settings = self.settings.with_last_station_id(station_id)
        self.save()

    def is_notifications_enabled(self) -> bool:
        return self.settings.notificationsEnabled

    def set_notifications_enabled(self, enabled: bool):
        self.settings = self.settings.with_notifications_enabled(enabled)
        self.save()

    def load(self):
        path = self.config.settings_file
        if not path or not os.path.exists(path):
            return
        try:
            with open(path, 'r', encoding='utf-8') as f:
                data = json.load(f)
                self.settings = UserSettings(
                    volume=data.get('volume', 100),
                    lastStationId=data.get('lastStationId'),
                    notificationsEnabled=data.get('notificationsEnabled', True)
                )
        except Exception:
            pass

    def save(self):
        path = self.config.settings_file
        if not path:
            return
        try:
            p = Path(path)
            p.parent.mkdir(parents=True, exist_ok=True)
            with open(p, 'w', encoding='utf-8') as f:
                data = {
                    'volume': self.settings.volume,
                    'lastStationId': self.settings.lastStationId,
                    'notificationsEnabled': self.settings.notificationsEnabled
                }
                json.dump(data, f, indent=2)
        except Exception:
            pass
