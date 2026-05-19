import os
from pathlib import Path
from dataclasses import dataclass, field
from typing import List

@dataclass
class PlayerConfig:
    command: str = "ffplay"
    args: List[str] = field(default_factory=lambda: ["-nodisp", "-hide_banner", "-loglevel", "quiet", "-autoexit"])

@dataclass
class StationsConfig:
    file: str = "stations.json" # Relative to application if bundled, or absolute

class RadioConfig:
    def __init__(self):
        home_dir = Path.home()
        app_dir = home_dir / ".radio-shell"

        self.player = PlayerConfig()
        self.stations = StationsConfig()
        self.favorites_file = str(app_dir / "favorites.json")
        self.custom_stations_file = str(app_dir / "custom-stations.json")
        self.settings_file = str(app_dir / "settings.json")
        self.recordings_dir = str(app_dir / "recordings")
        self.stats_file = str(app_dir / "stats.json")

    def ensure_dirs(self):
        Path(self.recordings_dir).mkdir(parents=True, exist_ok=True)
