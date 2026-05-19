from src.radio.models import RadioStation, UserSettings

def test_radio_station():
    station = RadioStation("id1", "Test", "TR", "Pop", "http://test")
    assert station.favorite is False
    fav = station.with_favorite(True)
    assert fav.favorite is True
    assert fav.id == "id1"

def test_user_settings():
    settings = UserSettings.defaults()
    assert settings.volume == 100
    assert settings.lastStationId is None

    updated = settings.with_volume(50).with_last_station_id("id1")
    assert updated.volume == 50
    assert updated.lastStationId == "id1"
