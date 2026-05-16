package com.radio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.radio.config.RadioConfig;
import com.radio.model.UserSettings;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final RadioConfig config;
    private final ObjectMapper mapper;

    private UserSettings settings = UserSettings.defaults();

    public SettingsService(RadioConfig config) {
        this.config = config;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void init() {
        load();
    }

    public synchronized int getVolume() {
        Integer volume = settings.volume();
        if (volume == null || volume < 0 || volume > 100) {
            return 100;
        }
        return volume;
    }

    public synchronized void setVolume(int volume) {
        int clamped = Math.max(0, Math.min(100, volume));
        settings = settings.withVolume(clamped);
        save();
    }

    public synchronized String getLastStationId() {
        return settings.lastStationId();
    }

    public synchronized void setLastStationId(String stationId) {
        if (stationId == null || stationId.isBlank()) {
            return;
        }
        settings = settings.withLastStationId(stationId);
        save();
    }

    public synchronized boolean isNotificationsEnabled() {
        return settings.notificationsEnabled() == null || settings.notificationsEnabled();
    }

    public synchronized void setNotificationsEnabled(boolean enabled) {
        settings = settings.withNotificationsEnabled(enabled);
        save();
    }

    private void load() {
        String path = config.getSettingsFile();
        if (path == null) return;
        File file = new File(path);
        if (!file.exists()) return;
        try {
            settings = mapper.readValue(file, UserSettings.class);
        } catch (IOException e) {
            log.error("Ayarlar yüklenemedi: {}", e.getMessage());
            settings = UserSettings.defaults();
        }
    }

    private void save() {
        String path = config.getSettingsFile();
        if (path == null) return;
        try {
            Path p = Path.of(path);
            Files.createDirectories(p.getParent());
            mapper.writeValue(p.toFile(), settings);
        } catch (IOException e) {
            log.error("Ayarlar kaydedilemedi: {}", e.getMessage());
        }
    }
}
