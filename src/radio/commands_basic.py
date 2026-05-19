import argparse
from typing import List

from src.radio.shell import InteractiveShell
from src.radio.services.station_service import StationService
from src.radio.services.statistics_service import StatisticsService
from src.radio.player import AudioPlayer
from src.radio import ui

class BasicCommands:
    def __init__(self, shell: InteractiveShell, station_service: StationService, stats_service: StatisticsService, player: AudioPlayer):
        self.station_service = station_service
        self.stats_service = stats_service
        self.player = player
        self.last_list = []

        shell.register("listele", self.cmd_listele, "Tüm radyo istasyonlarını listeler")
        shell.register("turkiye", self.cmd_turkiye, "Türkiye radyo istasyonlarını listeler")
        shell.register("ulkeler", self.cmd_ulkeler, "Mevcut ülkeleri listeler")
        shell.register("ulke", self.cmd_ulke, "Belirli bir ülkenin istasyonlarını listeler")
        shell.register("turler", self.cmd_turler, "Mevcut müzik türlerini listeler")
        shell.register("tur", self.cmd_tur, "Belirli bir müzik türündeki istasyonları listeler")
        shell.register("ara", self.cmd_ara, "İstasyon arar (isim, ülke veya tür)")
        shell.register("istatistik", self.cmd_istatistik, "Dinleme istatistiklerini gösterir")
        shell.register("durum", self.cmd_durum, "Şu anki çalma durumunu gösterir")

    def _update_last(self, stations: list):
        self.last_list = stations

    def cmd_listele(self, args: List[str]):
        stations = self.station_service.get_all_stations()
        self._update_last(stations)
        ui.print_station_table("Tüm İstasyonlar", stations)

    def cmd_turkiye(self, args: List[str]):
        stations = [s for s in self.station_service.get_all_stations() if s.country and s.country.lower() in ("türkiye", "turkey")]
        self._update_last(stations)
        ui.print_station_table("Türkiye İstasyonları", stations)

    def cmd_ulkeler(self, args: List[str]):
        countries = self.station_service.get_countries()
        if not countries:
            ui.print_info("Ülke bulunamadı.")
            return
        ui.console.print("[cyan]Mevcut Ülkeler:[/]")
        for c in countries:
            count = len([s for s in self.station_service.get_all_stations() if s.country == c])
            ui.console.print(f"  - {c} ({count} istasyon)")

    def cmd_ulke(self, args: List[str]):
        parser = argparse.ArgumentParser(prog="ulke")
        parser.add_argument("-i", "--isim", required=True)
        try:
            parsed = parser.parse_args(args)
            stations = [s for s in self.station_service.get_all_stations() if s.country and s.country.lower() == parsed.isim.lower()]
            self._update_last(stations)
            ui.print_station_table(f"Ülke: {parsed.isim}", stations)
        except SystemExit:
            ui.print_error("Kullanım: ulke -i <ülke>")

    def cmd_turler(self, args: List[str]):
        genres = self.station_service.get_genres()
        if not genres:
            ui.print_info("Tür bulunamadı.")
            return
        ui.console.print("[cyan]Mevcut Türler:[/]")
        for g in genres:
            count = len([s for s in self.station_service.get_all_stations() if s.genre and g.lower() in s.genre.lower()])
            ui.console.print(f"  - {g} ({count} istasyon)")

    def cmd_tur(self, args: List[str]):
        parser = argparse.ArgumentParser(prog="tur")
        parser.add_argument("-i", "--isim", required=True)
        try:
            parsed = parser.parse_args(args)
            stations = [s for s in self.station_service.get_all_stations() if s.genre and parsed.isim.lower() in s.genre.lower()]
            self._update_last(stations)
            ui.print_station_table(f"Tür: {parsed.isim}", stations)
        except SystemExit:
            ui.print_error("Kullanım: tur -i <tür>")

    def cmd_ara(self, args: List[str]):
        parser = argparse.ArgumentParser(prog="ara")
        parser.add_argument("-s", "--sorgu", required=True)
        try:
            parsed = parser.parse_args(args)
            stations = self.station_service.search(parsed.sorgu)
            self._update_last(stations)
            ui.print_station_table(f"Arama: {parsed.sorgu}", stations)
        except SystemExit:
            ui.print_error("Kullanım: ara -s <kelime>")

    def cmd_istatistik(self, args: List[str]):
        top = self.stats_service.get_top_stations(10)
        total_time = self.stats_service.get_total_listen_time()
        sessions = self.stats_service.get_total_sessions()

        ui.console.print(f"[magenta]Toplam Dinleme:[/] {total_time}")
        ui.console.print(f"[magenta]Toplam Oturum:[/] {sessions}")

        if not top:
            ui.print_info("Henüz istatistik yok.")
            return

        from rich.table import Table
        table = Table(title="Top 10 İstasyon")
        table.add_column("İstasyon")
        table.add_column("Süre (sn)")
        table.add_column("Oturum")
        for s in top:
            table.add_row(s.stationName or s.stationId, str(s.totalSeconds), str(s.sessionCount))
        ui.console.print(table)

    def cmd_durum(self, args: List[str]):
        if not self.player.is_playing() or not self.player.current_station:
            ui.print_info("Şu an çalan bir radyo yok.")
            return
        ui.print_now_playing(
            self.player.current_station,
            self.player.current_song,
            self.player.volume,
            self.player.is_recording()
        )
