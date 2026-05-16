package com.radio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserSettings(
        Integer volume,
        String lastStationId,
        Boolean notificationsEnabled
) {
    public static UserSettings defaults() {
        return new UserSettings(100, null, true);
    }

    public UserSettings withVolume(int newVolume) {
        return new UserSettings(newVolume, lastStationId, notificationsEnabled);
    }

    public UserSettings withLastStationId(String stationId) {
        return new UserSettings(volume, stationId, notificationsEnabled);
    }

    public UserSettings withNotificationsEnabled(boolean enabled) {
        return new UserSettings(volume, lastStationId, enabled);
    }
}
