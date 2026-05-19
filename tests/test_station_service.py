import os
import json
import tempfile
import pytest
from src.radio.config import RadioConfig
from src.radio.services.station_service import StationService
from src.radio.models import RadioStation

@pytest.fixture
def temp_config():
    with tempfile.TemporaryDirectory() as d:
        config = RadioConfig()
        config.favorites_file = os.path.join(d, "favs.json")
        config.custom_stations_file = os.path.join(d, "custom.json")
        yield config

def test_custom_stations(temp_config):
    service = StationService(temp_config)
    s = RadioStation("c1", "Custom1", "US", "Jazz", "http://c1")
    service.add_custom_station(s)

    assert len(service.custom_stations) == 1
    assert service.get_station("c1").name == "Custom1"

    # Reload to test saving/loading
    service2 = StationService(temp_config)
    service2.init()
    assert len(service2.custom_stations) == 1
    assert service2.custom_stations[0].id == "c1"

def test_favorites(temp_config):
    service = StationService(temp_config)
    s = RadioStation("s1", "Stat1", "TR", "Pop", "http://s1")
    service.custom_stations.append(s)

    added = service.toggle_favorite("s1")
    assert added is True
    assert "s1" in service.favorites

    favs = service.get_favorites()
    assert len(favs) == 1
    assert favs[0].id == "s1"

    # toggle again
    service.toggle_favorite("s1")
    assert "s1" not in service.favorites
