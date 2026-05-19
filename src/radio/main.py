import sys
import threading
from src.radio.config import RadioConfig
from src.radio.services.settings_service import SettingsService
from src.radio.services.station_service import StationService
from src.radio.services.statistics_service import StatisticsService
from src.radio.services.radio_browser_service import RadioBrowserService
from src.radio.services.notification_service import NotificationService
from src.radio.player import AudioPlayer
from src.radio.shell import InteractiveShell
from src.radio.commands_basic import BasicCommands
from src.radio.commands_playback import PlaybackCommands
from src.radio.commands_management import ManagementCommands
from src.radio import ui

def main():
    # Load user theme
    ui.load_theme()

    # 1. Initialize Configuration
    config = RadioConfig()

    # 2. Initialize Services
    settings_service = SettingsService(config)
    station_service = StationService(config)
    station_service.init()
    stats_service = StatisticsService(config)
    radio_browser = RadioBrowserService()
    notification_service = NotificationService(settings_service)

    # 3. Initialize Player
    player = AudioPlayer(config, notification_service)

    # 4. Initialize Interactive Shell
    shell = InteractiveShell(station_service)

    # 5. Register Commands
    basic_cmds = BasicCommands(shell, station_service, stats_service, player)
    playback_cmds = PlaybackCommands(shell, station_service, settings_service, stats_service, player, basic_cmds)
    ManagementCommands(shell, station_service, radio_browser, notification_service, player)

    # 6. Run the Shell Loop
    try:
        shell.run()
    finally:
        # 7. Cleanup
        player.stop()

if __name__ == "__main__":
    main()
