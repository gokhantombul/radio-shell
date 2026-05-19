import sys
import shlex
from typing import Dict, Callable, List, Iterable
from prompt_toolkit import PromptSession
from prompt_toolkit.completion import Completer, Completion, WordCompleter
from prompt_toolkit.history import InMemoryHistory
from prompt_toolkit.document import Document

from src.radio import ui
from src.radio.services.station_service import StationService

class ShellCommand:
    def __init__(self, name: str, func: Callable, desc: str):
        self.name = name
        self.func = func
        self.desc = desc

class RadioCompleter(Completer):
    def __init__(self, shell: 'InteractiveShell', station_service: StationService):
        self.shell = shell
        self.station_service = station_service

    def get_completions(self, document: Document, complete_event) -> Iterable[Completion]:
        text = document.text_before_cursor
        words = text.split(' ')

        # If empty or first word, complete command names
        if len(words) <= 1:
            word = words[0] if words else ''
            commands = list(self.shell.commands.keys()) + ["help", "exit", "q", "?", "listele"]
            for cmd in commands:
                if cmd.startswith(word):
                    yield Completion(cmd, start_position=-len(word))
            return

        cmd = words[0].lower()
        last_word = words[-1]

        # Context-aware completion based on command
        if cmd in ('cal', 'kontrol', 'favori', 'sil'):
            # These commands often take a station ID. We complete IDs if after -i or directly
            # For simplicity, if not completing a flag like -i, provide station IDs.
            if last_word.startswith('-'):
                yield Completion('-i', start_position=-len(last_word))
            else:
                for s in self.station_service.get_all_stations():
                    if s.id.startswith(last_word):
                        yield Completion(s.id, start_position=-len(last_word), display_meta=s.name)

        elif cmd == 'ulke':
            if last_word.startswith('-'):
                yield Completion('-i', start_position=-len(last_word))
            else:
                for c in self.station_service.get_countries():
                    if c.lower().startswith(last_word.lower()):
                        yield Completion(c, start_position=-len(last_word))

        elif cmd == 'tur':
            if last_word.startswith('-'):
                yield Completion('-i', start_position=-len(last_word))
            else:
                for g in self.station_service.get_genres():
                    if g.lower().startswith(last_word.lower()):
                        yield Completion(g, start_position=-len(last_word))

        elif cmd == 'tema':
            for t in ui.get_themes():
                if t.startswith(last_word):
                    yield Completion(t, start_position=-len(last_word))

class InteractiveShell:
    def __init__(self, station_service: StationService):
        self.commands: Dict[str, ShellCommand] = {}
        self.station_service = station_service
        self.completer = RadioCompleter(self, station_service)
        self.session = PromptSession(history=InMemoryHistory(), completer=self.completer)
        self.running = True

    def register(self, name: str, func: Callable, desc: str):
        self.commands[name] = ShellCommand(name, func, desc)

    def print_help(self):
        ui.print_info("Mevcut Komutlar:")
        for name, cmd in sorted(self.commands.items()):
            ui.console.print(f"  [cyan]{name}[/] - {cmd.desc}")
        ui.console.print("  [cyan]help / ?[/] - Bu yardım menüsünü gösterir")
        ui.console.print("  [cyan]exit / q[/] - Çıkış")

    def run(self):
        ui.print_banner()
        while self.running:
            try:
                text = self.session.prompt("radio> ").strip()
                if not text:
                    continue

                parts = shlex.split(text)
                cmd_name = parts[0].lower()
                args = parts[1:]

                if cmd_name in ("exit", "q", "quit"):
                    break
                elif cmd_name in ("help", "?"):
                    self.print_help()
                elif cmd_name in self.commands:
                    try:
                        self.commands[cmd_name].func(args)
                    except Exception as e:
                        ui.print_error(f"Komut çalıştırılırken hata: {e}")
                else:
                    ui.print_error(f"Bilinmeyen komut: '{cmd_name}'. Yardım için 'help' yazın.")

            except KeyboardInterrupt:
                continue
            except EOFError:
                break
            except Exception as e:
                ui.print_error(f"Shell hatası: {e}")
