import argparse
import random
import threading
import time
from typing import List, Optional
from datetime import datetime, timedelta

from src.radio.shell import InteractiveShell
from src.radio.services.station_service import StationService
from src.radio.services.settings_service import SettingsService
from src.radio.services.statistics_service import StatisticsService
from src.radio.player import AudioPlayer
from src.radio import ui

class PlaybackCommands:
    def __init__(self, shell: InteractiveShell, station_service: StationService, settings_service: SettingsService, stats_service: StatisticsService, player: AudioPlayer, basic_cmds):
        self.station_service = station_service
        self.settings_service = settings_service
        self.stats_service = stats_service
        self.player = player
        self.basic_cmds = basic_cmds # To access last_list for next/prev

        self.sleep_timer: Optional[threading.Timer] = None
        self.session_start: Optional[datetime] = None
        self.song_history: List[str] = []

        # Intercept player song changes to record history
        def on_song(title):
            if not self.song_history or self.song_history[-1] != title:
                self.song_history.append(title)
                if len(self.song_history) > 50:
                    self.song_history.pop(0)
        self.player.on_song_change = on_song

        shell.register("cal", self.cmd_cal, "Bir radyo istasyonunu çalar (cal -i <id>)")
        shell.register("son", self.cmd_son, "Son çalınan istasyonu çalar")
        shell.register("dur", self.cmd_dur, "Çalan radyoyu durdurur")
        shell.register("ses", self.cmd_ses, "Ses seviyesini ayarlar (0-100) (ses -s <değer>)")
        shell.register("sonraki", self.cmd_sonraki, "Listedeki bir sonraki istasyona geçer")
        shell.register("onceki", self.cmd_onceki, "Listedeki bir önceki istasyona geçer")
        shell.register("karistir", self.cmd_karistir, "Rastgele bir istasyon çalar")
        shell.register("uyku", self.cmd_uyku, "Uyku zamanlayıcısını başlatır (uyku -d <dakika> | uyku iptal)")
        shell.register("gecmis", self.cmd_gecmis, "Son görülen şarkı bilgilerini listeler")

    def _record_session(self):
        if self.session_start and self.player.current_station:
            duration = datetime.now() - self.session_start
            self.stats_service.record_session(self.player.current_station, duration)
        self.session_start = None

    def play_station(self, station):
        self._record_session()
        self.player.play(station, self.settings_service.get_volume())
        self.settings_service.set_last_station_id(station.id)
        self.session_start = datetime.now()
        ui.print_success(f"Çalınıyor: {station.name}")

    def cmd_cal(self, args: List[str]):
        parser = argparse.ArgumentParser(prog="cal")
        parser.add_argument("-i", "--id", required=True)
        try:
            parsed = parser.parse_args(args)
            station = self.station_service.get_station(parsed.id)
            if not station:
                ui.print_error(f"İstasyon bulunamadı: {parsed.id}")
                return
            self.play_station(station)
        except SystemExit:
            ui.print_error("Kullanım: cal -i <id>")

    def cmd_son(self, args: List[str]):
        last_id = self.settings_service.get_last_station_id()
        if not last_id:
            ui.print_error("Son çalınan istasyon kaydı yok.")
            return
        station = self.station_service.get_station(last_id)
        if not station:
            ui.print_error("Son çalınan istasyon artık bulunmuyor.")
            return
        self.play_station(station)

    def cmd_dur(self, args: List[str]):
        self._record_session()
        self.player.stop()
        ui.print_success("Oynatma durduruldu.")

    def cmd_ses(self, args: List[str]):
        parser = argparse.ArgumentParser(prog="ses")
        parser.add_argument("-s", "--seviye", type=int, required=True)
        try:
            parsed = parser.parse_args(args)
            vol = max(0, min(100, parsed.seviye))
            self.settings_service.set_volume(vol)
            self.player.set_volume(vol)
            ui.print_success(f"Ses seviyesi ayarlandı: %{vol}")
        except SystemExit:
            ui.print_error("Kullanım: ses -s <0-100>")

    def _get_adjacent(self, offset: int):
        last_list = self.basic_cmds.last_list
        if not last_list:
            ui.print_error("Önce bir liste oluşturun (örn: listele, turkiye).")
            return
        if not self.player.current_station:
            ui.print_error("Şu an çalan bir istasyon yok.")
            return

        try:
            idx = next(i for i, s in enumerate(last_list) if s.id == self.player.current_station.id)
            next_idx = (idx + offset) % len(last_list)
            self.play_station(last_list[next_idx])
        except StopIteration:
            ui.print_error("Mevcut istasyon son listede bulunamadı.")

    def cmd_sonraki(self, args: List[str]):
        self._get_adjacent(1)

    def cmd_onceki(self, args: List[str]):
        self._get_adjacent(-1)

    def cmd_karistir(self, args: List[str]):
        parser = argparse.ArgumentParser(prog="karistir")
        parser.add_argument("-u", "--ulke", type=str)
        parser.add_argument("-t", "--tur", type=str)
        try:
            parsed, _ = parser.parse_known_args(args)
            stations = self.station_service.get_all_stations()
            if parsed.ulke:
                stations = [s for s in stations if s.country and parsed.ulke.lower() in s.country.lower()]
            if parsed.tur:
                stations = [s for s in stations if s.genre and parsed.tur.lower() in s.genre.lower()]

            if not stations:
                ui.print_error("Kriterlere uygun istasyon bulunamadı.")
                return

            station = random.choice(stations)
            self.play_station(station)
        except SystemExit:
            pass

    def cmd_uyku(self, args: List[str]):
        if args and args[0] == "iptal":
            if self.sleep_timer:
                self.sleep_timer.cancel()
                self.sleep_timer = None
                ui.print_success("Uyku zamanlayıcısı iptal edildi.")
            else:
                ui.print_info("Aktif uyku zamanlayıcısı yok.")
            return

        parser = argparse.ArgumentParser(prog="uyku")
        parser.add_argument("-d", "--dakika", type=int, required=True)
        try:
            parsed = parser.parse_args(args)
            if self.sleep_timer:
                self.sleep_timer.cancel()

            def stop_playback():
                self.cmd_dur([])
                ui.print_info("Uyku zamanlayıcısı süresi doldu, oynatma durduruldu.")

            self.sleep_timer = threading.Timer(parsed.dakika * 60.0, stop_playback)
            self.sleep_timer.start()
            ui.print_success(f"Uyku zamanlayıcısı ayarlandı: {parsed.dakika} dakika sonra duracak.")
        except SystemExit:
            ui.print_error("Kullanım: uyku -d <dakika> VEYA uyku iptal")

    def cmd_gecmis(self, args: List[str]):
        if not self.song_history:
            ui.print_info("Henüz şarkı geçmişi yok.")
            return
        ui.console.print("[cyan]Son çalınan şarkılar:[/]")
        for i, s in enumerate(reversed(self.song_history[-10:]), 1):
            ui.console.print(f"  {i}. {s}")
