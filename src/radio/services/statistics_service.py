import json
import os
from pathlib import Path
from dataclasses import dataclass
from typing import Dict, List, Optional
from datetime import timedelta

from src.radio.config import RadioConfig
from src.radio.models import RadioStation

@dataclass
class StationStat:
    stationId: str
    stationName: str
    country: str
    genre: str
    totalSeconds: int
    sessionCount: int

class StatisticsService:
    MIN_SESSION_SECONDS = 30

    def __init__(self, config: RadioConfig):
        self.config = config
        self.stats: Dict[str, StationStat] = {}
        self.load()

    def record_session(self, station: RadioStation, duration: timedelta):
        if not station or not duration:
            return
        seconds = int(duration.total_seconds())
        if seconds < self.MIN_SESSION_SECONDS:
            return

        sid = station.id
        if sid in self.stats:
            existing = self.stats[sid]
            existing.totalSeconds += seconds
            existing.sessionCount += 1
        else:
            self.stats[sid] = StationStat(
                stationId=sid,
                stationName=station.name,
                country=station.country,
                genre=station.genre,
                totalSeconds=seconds,
                sessionCount=1
            )
        self.save()

    def get_top_stations(self, limit: int) -> List[StationStat]:
        if limit <= 0:
            return []

        sorted_stats = sorted(
            self.stats.values(),
            key=lambda s: (-s.totalSeconds, s.stationName.lower() if s.stationName else "")
        )
        return sorted_stats[:limit]

    def get_station_stat(self, station_id: str) -> Optional[StationStat]:
        if not station_id or not station_id.strip():
            return None
        return self.stats.get(station_id)

    def get_total_listen_time(self) -> timedelta:
        total = sum(s.totalSeconds for s in self.stats.values())
        return timedelta(seconds=total)

    def get_total_sessions(self) -> int:
        return sum(s.sessionCount for s in self.stats.values())

    def load(self):
        path = self.config.stats_file
        if not path or not os.path.exists(path):
            return
        try:
            with open(path, 'r', encoding='utf-8') as f:
                data = json.load(f)
                for item in data:
                    sid = item.get("stationId")
                    if sid:
                        self.stats[sid] = StationStat(
                            stationId=sid,
                            stationName=item.get("stationName", ""),
                            country=item.get("country", ""),
                            genre=item.get("genre", ""),
                            totalSeconds=item.get("totalSeconds", 0),
                            sessionCount=item.get("sessionCount", 0)
                        )
        except Exception:
            pass

    def save(self):
        path = self.config.stats_file
        if not path:
            return
        try:
            p = Path(path)
            p.parent.mkdir(parents=True, exist_ok=True)

            sorted_stats = sorted(
                self.stats.values(),
                key=lambda s: (-s.totalSeconds, s.stationName.lower() if s.stationName else "")
            )

            data = [
                {
                    "stationId": s.stationId,
                    "stationName": s.stationName,
                    "country": s.country,
                    "genre": s.genre,
                    "totalSeconds": s.totalSeconds,
                    "sessionCount": s.sessionCount
                }
                for s in sorted_stats
            ]
            with open(p, 'w', encoding='utf-8') as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
        except Exception:
            pass
