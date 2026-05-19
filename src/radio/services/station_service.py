import json
import os
import shutil
from pathlib import Path
from typing import List, Optional, Set
import uuid

from src.radio.config import RadioConfig
from src.radio.models import RadioStation

class StationService:
    def __init__(self, config: RadioConfig):
        self.config = config
        self.stations: List[RadioStation] = []
        self.custom_stations: List[RadioStation] = []
        self.favorites: Set[str] = set()

    def init(self):
        self._load_favorites()
        self._load_internal_stations()
        self._load_custom_stations()

    def get_all_stations(self) -> List[RadioStation]:
        all_stations = self.stations + self.custom_stations
        return [s.with_favorite(s.id in self.favorites) for s in all_stations]

    def get_station(self, id_or_name: str) -> Optional[RadioStation]:
        if not id_or_name:
            return None

        # Exact match ID
        for s in self.get_all_stations():
            if s.id.lower() == id_or_name.lower():
                return s

        # Name match
        for s in self.get_all_stations():
            if s.name.lower() == id_or_name.lower():
                return s

        # Partial match name
        for s in self.get_all_stations():
            if id_or_name.lower() in s.name.lower():
                return s
        return None

    def toggle_favorite(self, station_id: str) -> bool:
        station = self.get_station(station_id)
        if not station:
            return False

        real_id = station.id
        added = False
        if real_id in self.favorites:
            self.favorites.remove(real_id)
        else:
            self.favorites.add(real_id)
            added = True

        self._save_favorites()
        return added

    def get_favorites(self) -> List[RadioStation]:
        return [s for s in self.get_all_stations() if s.favorite]

    def add_custom_station(self, station: RadioStation):
        existing = next((s for s in self.custom_stations if s.id == station.id), None)
        if existing:
            self.custom_stations.remove(existing)
        self.custom_stations.append(station)
        self._save_custom_stations()

    def remove_custom_station(self, station_id: str) -> bool:
        initial_len = len(self.custom_stations)
        self.custom_stations = [s for s in self.custom_stations if s.id != station_id]
        if len(self.custom_stations) < initial_len:
            self._save_custom_stations()
            if station_id in self.favorites:
                self.favorites.remove(station_id)
                self._save_favorites()
            return True
        return False

    def import_playlist(self, file_path: str, country: str, genre: str, prefix: str) -> int:
        if not os.path.exists(file_path):
            return 0

        count = 0
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                lines = f.readlines()

            name = ""
            for line in lines:
                line = line.strip()
                if not line:
                    continue
                if line.startswith("#EXTINF:"):
                    parts = line.split(",", 1)
                    if len(parts) > 1:
                        name = parts[1].strip()
                elif line.startswith("http"):
                    if not name:
                        name = f"Imported {uuid.uuid4().hex[:6]}"
                    if prefix:
                        name = f"{prefix} {name}"

                    safe_id = "".join(c if c.isalnum() else "-" for c in name.lower())
                    station = RadioStation(safe_id, name, country, genre, line)
                    self.add_custom_station(station)
                    count += 1
                    name = ""
        except Exception:
            pass
        return count

    def get_countries(self) -> List[str]:
        countries = {s.country for s in self.get_all_stations() if s.country}
        return sorted(list(countries))

    def get_genres(self) -> List[str]:
        genres = set()
        for s in self.get_all_stations():
            if s.genre:
                for g in s.genre.split("/"):
                    genres.add(g.strip())
        return sorted(list(genres))

    def search(self, query: str) -> List[RadioStation]:
        if not query:
            return self.get_all_stations()
        q = query.lower()
        return [s for s in self.get_all_stations() if q in s.name.lower() or q in s.country.lower() or q in s.genre.lower()]

    def _load_internal_stations(self):
        # Look in resources or current dir
        paths_to_check = [
            Path('src/main/resources/stations.json'),
            Path('stations.json')
        ]

        target_path = None
        for p in paths_to_check:
            if p.exists():
                target_path = p
                break

        if not target_path:
            return

        try:
            with open(target_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
                stations_data = data.get('stations', [])
                for s in stations_data:
                    self.stations.append(RadioStation(
                        id=s.get('id'),
                        name=s.get('name'),
                        country=s.get('country'),
                        genre=s.get('genre'),
                        url=s.get('url')
                    ))
        except Exception:
            pass

    def _load_favorites(self):
        path = self.config.favorites_file
        if not path or not os.path.exists(path):
            return
        try:
            with open(path, 'r', encoding='utf-8') as f:
                data = json.load(f)
                self.favorites = set(data)
        except Exception:
            pass

    def _save_favorites(self):
        path = self.config.favorites_file
        if not path:
            return
        try:
            p = Path(path)
            p.parent.mkdir(parents=True, exist_ok=True)
            with open(p, 'w', encoding='utf-8') as f:
                json.dump(list(self.favorites), f, indent=2)
        except Exception:
            pass

    def _load_custom_stations(self):
        path = self.config.custom_stations_file
        if not path or not os.path.exists(path):
            return
        try:
            with open(path, 'r', encoding='utf-8') as f:
                data = json.load(f)
                stations_data = data.get('stations', [])
                for s in stations_data:
                    self.custom_stations.append(RadioStation(
                        id=s.get('id'),
                        name=s.get('name'),
                        country=s.get('country'),
                        genre=s.get('genre'),
                        url=s.get('url')
                    ))
        except Exception:
            pass

    def _save_custom_stations(self):
        path = self.config.custom_stations_file
        if not path:
            return
        try:
            p = Path(path)
            p.parent.mkdir(parents=True, exist_ok=True)
            stations_dict = [
                {
                    "id": s.id, "name": s.name, "country": s.country,
                    "genre": s.genre, "url": s.url, "favorite": s.favorite
                }
                for s in self.custom_stations
            ]
            with open(p, 'w', encoding='utf-8') as f:
                json.dump({"stations": stations_dict}, f, indent=2, ensure_ascii=False)
        except Exception:
            pass
