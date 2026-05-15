package com.radio.service;

import com.radio.config.RadioConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsServiceTest {

    @TempDir
    Path tempDir;

    private RadioConfig config;
    private SettingsService service;

    @BeforeEach
    void setUp() {
        config = new RadioConfig();
        config.setSettingsFile(tempDir.resolve("settings.json").toString());
        service = new SettingsService(config);
        service.init();
    }

    @Test
    void defaultVolume_is100() {
        assertThat(service.getVolume()).isEqualTo(100);
    }

    @Test
    void setVolume_clampsAndPersists() {
        service.setVolume(140);
        assertThat(service.getVolume()).isEqualTo(100);

        service.setVolume(35);
        var reloaded = new SettingsService(config);
        reloaded.init();

        assertThat(reloaded.getVolume()).isEqualTo(35);
    }

    @Test
    void lastStation_persistsAcrossReload() {
        service.setLastStationId("tr-powerfm");

        var reloaded = new SettingsService(config);
        reloaded.init();

        assertThat(reloaded.getLastStationId()).isEqualTo("tr-powerfm");
    }
}
