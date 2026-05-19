import argparse
import subprocess
from typing import List

from src.radio.shell import InteractiveShell
from src.radio.services.station_service import StationService
from src.radio.services.radio_browser_service import RadioBrowserService, OnlineStation
from src.radio.services.notification_service import NotificationService
from src.radio.player import AudioPlayer
from src.radio.models import RadioStation
from src.radio import ui

class ManagementCommands:
    def __init__(self, shell: InteractiveShell, station_service: StationService, radio_browser: RadioBrowserService, notification_service: NotificationService, player: AudioPlayer):
        self.station_service = station_service
        self.radio_browser = radio_browser
        self.notification_service = notification_service
        self.player = player
        self.last_online_search: List[OnlineStation] = []

        shell.register("favori", self.cmd_favori, "İstasyonu favorilere ekler/çıkarır")
        shell.register("favoriler", self.cmd_favoriler, "Favori istasyonları listeler")
        shell.register("kaydet", self.cmd_kaydet, "Çalan yayını kaydetmeye başlar")
        shell.register("kayitdur", self.cmd_kayitdur, "Kaydı durdurur")
        shell.register("tema", self.cmd_tema, "Renk temasını değiştirir")
        shell.register("kontrol", self.cmd_kontrol, "İstasyon akış URL'lerini kontrol eder")
        shell.register("ekle", self.cmd_ekle, "Yeni özel istasyon ekler")
        shell.register("duzenle", self.cmd_duzenle, "Özel istasyonu düzenler")
        shell.register("sil", self.cmd_sil, "Özel istasyonu siler")
        shell.register("iceaktar", self.cmd_iceaktar, "Playlist dosyasından istasyon ekler")
        shell.register("bildirim", self.cmd_bildirim, "Bildirimleri açar/kapatır")
        shell.register("online-ara", self.cmd_online_ara, "RadioBrowser üzerinden istasyon arar")
        shell.register("online-ekle", self.cmd_online_ekle, "Arama sonucundan istasyon ekler")

    def cmd_favori(self, args: List[str]):
        parser = argparse.ArgumentParser(prog="favori")
        parser.add_argument("-i", "--id", required=True)
        try:
            parsed = parser.parse_args(args)
            station = self.station_service.get_station(parsed.id)
            if not station:
                ui.print_error("İstasyon bulunamadı.")
                return
            added = self.station_service.toggle_favorite(station.id)
            if added:
                ui.print_success(f"Favorilere eklendi: {station.name}")
            else:
                ui.print_info(f"Favorilerden çıkarıldı: {station.name}")
        except SystemExit:
            ui.print_error("Kullanım: favori -i <id>")

    def cmd_favoriler(self, args: List[str]):
        favs = self.station_service.get_favorites()
        ui.print_station_table("Favori İstasyonlar", favs)

    def cmd_kaydet(self, args: List[str]):
        msg = self.player.start_recording()
        if "başladı" in msg:
            ui.print_success(msg)
        else:
            ui.print_error(msg)

    def cmd_kayitdur(self, args: List[str]):
        msg = self.player.stop_recording()
        ui.print_info(msg)

    def cmd_tema(self, args: List[str]):
        parser = argparse.ArgumentParser(prog="tema")
        parser.add_argument("-i", "--isim")
        try:
            parsed, _ = parser.parse_known_args(args)
            if parsed.isim:
                if ui.set_theme(parsed.isim):
                    ui.print_success(f"Tema '{parsed.isim}' olarak ayarlandı.")
                else:
                    ui.print_error(f"Geçersiz tema. Mevcut: {', '.join(ui.get_themes())}")
            else:
                ui.console.print(f"[cyan]Mevcut Temalar:[/] {', '.join(ui.get_themes())}")
        except SystemExit:
            pass

    def cmd_kontrol(self, args: List[str]):
        parser = argparse.ArgumentParser(prog="kontrol")
        parser.add_argument("-i", "--id")
        try:
            parsed, _ = parser.parse_known_args(args)
            stations = [self.station_service.get_station(parsed.id)] if parsed.id else self.station_service.get_all_stations()
            stations = [s for s in stations if s]

            if not stations:
                ui.print_error("Kontrol edilecek istasyon bulunamadı.")
                return

            ui.print_info(f"{len(stations)} istasyon kontrol ediliyor... (Bu işlem zaman alabilir)")

            success = 0
            for s in stations:
                try:
                    # Using curl like the java app suggests in its tests/logic often
                    cmd = ["curl", "-s", "-I", "-m", "5", "-A", "VLC/3.0.16 LibVLC/3.0.16", s.url]
                    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
                    if "HTTP/" in res.stdout and ("200" in res.stdout or "302" in res.stdout):
                        success += 1
                        if parsed.id:
                            ui.print_success(f"{s.name}: AKTİF")
                    else:
                        if parsed.id:
                            ui.print_error(f"{s.name}: BAŞARISIZ")
                except Exception:
                    if parsed.id:
                        ui.print_error(f"{s.name}: HATA")

            if not parsed.id:
                ui.print_info(f"Kontrol tamamlandı: {success}/{len(stations)} aktif.")
        except SystemExit:
            pass

    def cmd_ekle(self, args: List[str]):
        parser = argparse.ArgumentParser(prog="ekle")
        parser.add_argument("--id", required=True)
        parser.add_argument("--isim", required=True)
        parser.add_argument("--ulke", default="Türkiye")
        parser.add_argument("--tur", default="Çeşitli")
        parser.add_argument("--url", required=True)
        try:
            parsed = parser.parse_args(args)
            s = RadioStation(parsed.id, parsed.isim, parsed.ulke, parsed.tur, parsed.url)
            self.station_service.add_custom_station(s)
            ui.print_success(f"Özel istasyon eklendi: {s.name}")
        except SystemExit:
            ui.print_error("Kullanım: ekle --id <id> --isim <isim> --ulke <ulke> --tur <tur> --url <url>")

    def cmd_duzenle(self, args: List[str]):
        parser = argparse.ArgumentParser(prog="duzenle")
        parser.add_argument("--id", required=True)
        parser.add_argument("--isim")
        parser.add_argument("--ulke")
        parser.add_argument("--tur")
        parser.add_argument("--url")
        try:
            parsed = parser.parse_args(args)
            existing = self.station_service.get_station(parsed.id)
            if not existing or existing not in self.station_service.custom_stations:
                ui.print_error("Yalnızca özel istasyonlar düzenlenebilir.")
                return
            s = RadioStation(
                existing.id,
                parsed.isim or existing.name,
                parsed.ulke or existing.country,
                parsed.tur or existing.genre,
                parsed.url or existing.url
            )
            self.station_service.add_custom_station(s)
            ui.print_success("İstasyon güncellendi.")
        except SystemExit:
            ui.print_error("Kullanım: duzenle --id <id> [--isim ..] [--url ..] vs.")

    def cmd_sil(self, args: List[str]):
        parser = argparse.ArgumentParser(prog="sil")
        parser.add_argument("--id", required=True)
        try:
            parsed = parser.parse_args(args)
            if self.station_service.remove_custom_station(parsed.id):
                ui.print_success(f"İstasyon silindi: {parsed.id}")
            else:
                ui.print_error("Özel istasyon bulunamadı.")
        except SystemExit:
            pass

    def cmd_iceaktar(self, args: List[str]):
        parser = argparse.ArgumentParser(prog="iceaktar")
        parser.add_argument("-d", "--dosya", required=True)
        parser.add_argument("-u", "--ulke", default="İçe Aktarılan")
        parser.add_argument("-t", "--tur", default="Karma")
        parser.add_argument("-p", "--prefix", default="")
        try:
            parsed = parser.parse_args(args)
            count = self.station_service.import_playlist(parsed.dosya, parsed.ulke, parsed.tur, parsed.prefix)
            if count > 0:
                ui.print_success(f"{count} istasyon içe aktarıldı.")
            else:
                ui.print_error("İstasyon okunamadı veya dosya bulunamadı.")
        except SystemExit:
            pass

    def cmd_bildirim(self, args: List[str]):
        current = self.notification_service.is_enabled()
        self.notification_service.set_enabled(not current)
        state = "açık" if not current else "kapalı"
        ui.print_success(f"Masaüstü bildirimleri {state}.")

    def cmd_online_ara(self, args: List[str]):
        parser = argparse.ArgumentParser(prog="online-ara")
        parser.add_argument("-s", "--sorgu", default="")
        parser.add_argument("-u", "--ulke", default="")
        parser.add_argument("-t", "--tur", default="")
        parser.add_argument("-l", "--limit", type=int, default=15)
        try:
            parsed = parser.parse_args(args)
            if not parsed.sorgu and not parsed.ulke and not parsed.tur:
                ui.print_error("En az bir arama kriteri (-s, -u, -t) girin.")
                return

            ui.print_info("Aranıyor...")
            res = self.radio_browser.search(parsed.sorgu, parsed.ulke, parsed.tur, parsed.limit)
            self.last_online_search = res

            if not res:
                ui.print_error("İstasyon bulunamadı.")
                return

            from rich.table import Table
            table = Table(title="Online Arama Sonuçları")
            table.add_column("No")
            table.add_column("İsim")
            table.add_column("Ülke")
            table.add_column("Tür")

            for i, s in enumerate(res):
                table.add_row(str(i+1), s.name, s.country_display(), s.genre_display())
            ui.console.print(table)
            ui.print_info("Eklemek için: online-ekle -n <no>")
        except SystemExit:
            pass

    def cmd_online_ekle(self, args: List[str]):
        parser = argparse.ArgumentParser(prog="online-ekle")
        parser.add_argument("-n", "--no", type=int, required=True)
        try:
            parsed = parser.parse_args(args)
            idx = parsed.no - 1
            if idx < 0 or idx >= len(self.last_online_search):
                ui.print_error("Geçersiz numara. Önce 'online-ara' kullanın.")
                return

            os_station = self.last_online_search[idx]
            safe_id = f"rb-{os_station.uuid[:8]}"
            s = RadioStation(
                id=safe_id,
                name=os_station.name.strip() or "Bilinmeyen Radyo",
                country=os_station.country_display(),
                genre=os_station.genre_display(),
                url=os_station.url
            )
            self.station_service.add_custom_station(s)
            ui.print_success(f"İstasyon eklendi: {s.name} (ID: {s.id})")
        except SystemExit:
            ui.print_error("Kullanım: online-ekle -n <no>")
