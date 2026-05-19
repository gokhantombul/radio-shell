import requests
import urllib.parse
from dataclasses import dataclass
from typing import List

@dataclass
class OnlineStation:
    uuid: str
    name: str
    country: str
    tags: str
    url: str
    bitrate: int
    votes: int
    codec: str

    def genre_display(self) -> str:
        if not self.tags or not self.tags.strip():
            return "Çeşitli"
        first = self.tags.split(",")[0].strip()
        return first if first else "Çeşitli"

    def country_display(self) -> str:
        return self.country if self.country and self.country.strip() else "Bilinmiyor"

class RadioBrowserService:
    API_BASE = "https://de1.api.radio-browser.info/json"

    def search(self, query: str, country: str, tag: str, limit: int) -> List[OnlineStation]:
        url = f"{self.API_BASE}/stations/search?hidebroken=true&order=votes&reverse=true"
        if limit > 0:
            url += f"&limit={limit}"
        if query and query.strip():
            url += f"&name={urllib.parse.quote(query)}"
        if country and country.strip():
            url += f"&country={urllib.parse.quote(country)}"
        if tag and tag.strip():
            url += f"&tag={urllib.parse.quote(tag)}"

        try:
            headers = {"User-Agent": "Radio Shell/2.0 (Python)"}
            response = requests.get(url, headers=headers, timeout=10)
            if response.status_code != 200:
                return []

            data = response.json()
            stations = []
            for s in data:
                url_resolved = s.get("url_resolved")
                if not url_resolved or not url_resolved.startswith("http"):
                    continue
                stations.append(OnlineStation(
                    uuid=s.get("stationuuid", ""),
                    name=s.get("name", ""),
                    country=s.get("country", ""),
                    tags=s.get("tags", ""),
                    url=url_resolved,
                    bitrate=s.get("bitrate", 0),
                    votes=s.get("votes", 0),
                    codec=s.get("codec", "")
                ))
            return stations
        except Exception:
            return []
