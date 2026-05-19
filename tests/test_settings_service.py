import os
import json
import tempfile
import pytest
from src.radio.config import RadioConfig
from src.radio.services.settings_service import SettingsService

@pytest.fixture
def temp_config():
    with tempfile.TemporaryDirectory() as d:
        config = RadioConfig()
        config.settings_file = os.path.join(d, "settings.json")
        yield config

def test_settings_persistence(temp_config):
    service = SettingsService(temp_config)
    assert service.get_volume() == 100 # default
    assert service.is_notifications_enabled() is True # default
    assert service.get_last_station_id() is None # default

    service.set_volume(75)
    service.set_last_station_id("tr-powerfm")
    service.set_notifications_enabled(False)

    assert service.get_volume() == 75
    assert service.get_last_station_id() == "tr-powerfm"
    assert service.is_notifications_enabled() is False

    # Reload from disk
    service2 = SettingsService(temp_config)
    assert service2.get_volume() == 75
    assert service2.get_last_station_id() == "tr-powerfm"
    assert service2.is_notifications_enabled() is False
