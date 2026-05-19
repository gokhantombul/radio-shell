import os
import re
import subprocess
import threading
import time
from datetime import datetime
from typing import Optional, Callable

from src.radio.config import RadioConfig
from src.radio.models import RadioStation
from src.radio.services.notification_service import NotificationService

class AudioPlayer:
    def __init__(self, config: RadioConfig, notification_service: NotificationService):
        self.config = config
        self.notification_service = notification_service
        self.process: Optional[subprocess.Popen] = None
        self.record_process: Optional[subprocess.Popen] = None
        self.current_station: Optional[RadioStation] = None
        self.current_song: Optional[str] = None
        self.volume: int = 100

        self.on_song_change: Optional[Callable[[str], None]] = None
        self._stop_event = threading.Event()
        self._monitor_thread: Optional[threading.Thread] = None
        self._watchdog_thread: Optional[threading.Thread] = None
        self.last_metadata_update: Optional[datetime] = None

    def play(self, station: RadioStation, initial_volume: int):
        self.stop()
        self.current_station = station
        self.volume = initial_volume
        self.current_song = None
        self.last_metadata_update = None
        self._stop_event.clear()

        self._start_ffplay()

        # Start watchdog thread
        self._watchdog_thread = threading.Thread(target=self._watchdog_loop, daemon=True)
        self._watchdog_thread.start()

    def _start_ffplay(self):
        if not self.current_station:
            return

        cmd = [self.config.player.command]
        cmd.extend(self.config.player.args)

        if "-loglevel" in cmd:
            idx = cmd.index("-loglevel")
            if idx + 1 < len(cmd):
                cmd[idx + 1] = "info"
        else:
            cmd.extend(["-loglevel", "info"])

        cmd.extend(["-volume", str(self.volume), self.current_station.url])

        try:
            self.process = subprocess.Popen(
                cmd,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1
            )

            self._monitor_thread = threading.Thread(target=self._monitor_output, daemon=True)
            self._monitor_thread.start()
        except Exception as e:
            print(f"Player start error: {e}")

    def _watchdog_loop(self):
        retries = 0
        max_retries = 3
        while not self._stop_event.is_set():
            time.sleep(2)
            if self._stop_event.is_set():
                break

            # If process ended unexpectedly
            if self.process and self.process.poll() is not None:
                if retries < max_retries:
                    retries += 1
                    # Give a small delay before reconnecting
                    time.sleep(1)
                    if not self._stop_event.is_set():
                        # Restart the process
                        self._start_ffplay()
                else:
                    # Too many retries, give up
                    break
            else:
                # If running fine, we can reset retries (e.g. after a long successful uptime)
                # But for simplicity, we just keep the retry count as is unless we want to reset it on successful play.
                pass

    def stop(self):
        self._stop_event.set()
        if self.process:
            try:
                self.process.terminate()
                self.process.wait(timeout=2)
            except Exception:
                self.process.kill()
            self.process = None

        if self._monitor_thread and self._monitor_thread.is_alive():
            self._monitor_thread.join(timeout=1)

        if self._watchdog_thread and self._watchdog_thread.is_alive():
            self._watchdog_thread.join(timeout=1)

        self.current_station = None
        self.current_song = None

        self.stop_recording()

    def set_volume(self, volume: int):
        self.volume = max(0, min(100, volume))

    def is_playing(self) -> bool:
        return self.process is not None and self.process.poll() is None

    def start_recording(self) -> str:
        if not self.is_playing() or not self.current_station:
            return "Radyo çalmıyor!"
        if self.is_recording():
            return "Zaten kayıt yapılıyor!"

        self.config.ensure_dirs()
        safe_name = "".join(c if c.isalnum() else "_" for c in self.current_station.name.lower())
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"{safe_name}_{timestamp}.mp3"
        filepath = os.path.join(self.config.recordings_dir, filename)

        cmd = [
            "ffmpeg", "-y", "-i", self.current_station.url,
            "-c", "copy", filepath
        ]

        try:
            self.record_process = subprocess.Popen(
                cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
            )
            return f"Kayıt başladı: {filename}"
        except Exception as e:
            return f"Kayıt başlatılamadı: {e}"

    def stop_recording(self) -> str:
        if not self.is_recording():
            return "Aktif kayıt yok."

        try:
            self.record_process.terminate()
            self.record_process.wait(timeout=2)
        except Exception:
            if self.record_process:
                self.record_process.kill()
        self.record_process = None
        return "Kayıt durduruldu ve kaydedildi."

    def is_recording(self) -> bool:
        return self.record_process is not None and self.record_process.poll() is None

    def _monitor_output(self):
        if not self.process or not self.process.stderr:
            return

        icy_pattern = re.compile(r"StreamTitle\s*:\s*([^;]+)")

        try:
            for line in iter(self.process.stderr.readline, ''):
                if self._stop_event.is_set():
                    break
                if not line:
                    continue

                match = icy_pattern.search(line)
                if match:
                    title = match.group(1).strip()
                    if title in ("-", "Unknown", "null", "no title"):
                        continue

                    if title != self.current_song:
                        self.current_song = title
                        self.last_metadata_update = datetime.now()
                        if self.current_station:
                            self.notification_service.notify(self.current_station.name, title)
                        if self.on_song_change:
                            self.on_song_change(title)
        except Exception:
            pass
