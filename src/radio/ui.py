import os
import sys
from pathlib import Path
from typing import List, Optional
from rich.console import Console
from rich.table import Table
from rich.panel import Panel
from rich.text import Text
from rich import box

from src.radio.models import RadioStation

# Use global console
console = Console()

class Theme:
    def __init__(self, primary: str, secondary: str, highlight: str, success: str, error: str):
        self.primary = primary
        self.secondary = secondary
        self.highlight = highlight
        self.success = success
        self.error = error

THEMES = {
    "default": Theme("cyan", "blue", "magenta", "green", "red"),
    "hacker": Theme("green", "green", "bold green", "green", "red"),
    "ocean": Theme("blue", "cyan", "white", "bright_blue", "red"),
    "sunset": Theme("yellow", "red", "magenta", "yellow", "red")
}

current_theme = THEMES["default"]

def _get_theme_file_path() -> Path:
    return Path.home() / ".radio-shell" / "theme"

def load_theme():
    global current_theme
    theme_file = _get_theme_file_path()
    if theme_file.exists():
        try:
            saved_theme = theme_file.read_text(encoding="utf-8").strip()
            if saved_theme in THEMES:
                current_theme = THEMES[saved_theme]
        except Exception:
            pass

def set_theme(theme_name: str) -> bool:
    global current_theme
    if theme_name in THEMES:
        current_theme = THEMES[theme_name]
        try:
            theme_file = _get_theme_file_path()
            theme_file.parent.mkdir(parents=True, exist_ok=True)
            theme_file.write_text(theme_name, encoding="utf-8")
        except Exception:
            pass
        return True
    return False

def get_themes() -> List[str]:
    return list(THEMES.keys())

def print_banner():
    banner = f"""
  ╔══════════════════════════════════════════════════════════════╗
  ║   ♬  ░░░ RADIO SHELL ░░░  ♬                                  ║
  ║   Terminal FM Radio Player - Türkiye & Dünya                 ║
  ║   v2.0.0 (Python)                                            ║
  ╚══════════════════════════════════════════════════════════════╝
"""
    console.print(Text(banner, style=current_theme.primary))

def print_error(msg: str):
    console.print(f"[{current_theme.error}]⚠ {msg}[/]")

def print_success(msg: str):
    console.print(f"[{current_theme.success}]✓ {msg}[/]")

def print_info(msg: str):
    console.print(f"[{current_theme.highlight}]ℹ {msg}[/]")

def print_station_table(title: str, stations: List[RadioStation]):
    if not stations:
        print_info("Gösterilecek istasyon bulunamadı.")
        return

    table = Table(title=title, box=box.ROUNDED, style=current_theme.secondary, header_style=f"bold {current_theme.primary}")
    table.add_column("Fav", justify="center", width=3)
    table.add_column("ID", style=current_theme.highlight)
    table.add_column("İsim", style=current_theme.primary)
    table.add_column("Ülke")
    table.add_column("Tür")

    for s in stations:
        fav_star = f"[{current_theme.highlight}]★[/]" if s.favorite else "☆"
        table.add_row(
            fav_star,
            s.id,
            s.name,
            s.country or "-",
            s.genre or "-"
        )
    console.print(table)

def print_now_playing(station: RadioStation, song: Optional[str], volume: int, is_recording: bool):
    content = f"[{current_theme.primary}]Radyo:[/] {station.name}\n"
    content += f"[{current_theme.primary}]Ülke:[/] {station.country or '-'}\n"
    content += f"[{current_theme.primary}]Tür:[/] {station.genre or '-'}\n"

    if song:
        content += f"[{current_theme.highlight}]Şarkı:[/] {song}\n"

    content += f"[{current_theme.secondary}]Ses:[/] %{volume}"
    if is_recording:
        content += f" | [{current_theme.error}]● KAYIT YAPILIYOR[/]"

    panel = Panel(
        content,
        title=f"[{current_theme.highlight}]♬ Çalıyor[/]",
        border_style=current_theme.primary,
        box=box.ROUNDED,
        padding=(1, 2)
    )
    console.print(panel)
