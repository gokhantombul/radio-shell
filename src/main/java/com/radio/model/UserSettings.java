package com.radio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserSettings(
        Integer volume,
        String lastStationId
) {
    public static UserSettings defaults() {
        return new UserSettings(100, null);
    }

    public UserSettings withVolume(int newVolume) {
        return new UserSettings(newVolume, lastStationId);
    }

    public UserSettings withLastStationId(String stationId) {
        return new UserSettings(volume, stationId);
    }
}
