package com.radio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final String os;
    private final SettingsService settingsService;

    public NotificationService(SettingsService settingsService) {
        this.settingsService = settingsService;
        this.os = System.getProperty("os.name", "").toLowerCase();
    }

    public boolean isEnabled() {
        return settingsService.isNotificationsEnabled();
    }

    public void setEnabled(boolean enabled) {
        settingsService.setNotificationsEnabled(enabled);
    }

    public void notify(String stationName, String songTitle) {
        if (!isEnabled() || songTitle == null || songTitle.isBlank()) return;
        if (os.contains("mac")) {
            sendMac(stationName, songTitle);
        } else if (os.contains("linux") || os.contains("nix")) {
            sendLinux(stationName, songTitle);
        }
    }

    private void sendMac(String stationName, String songTitle) {
        String safeStation = escape(stationName);
        String safeSong = escape(songTitle);
        String script = "display notification \"%s\" with title \"%s\"".formatted(safeSong, safeStation);
        try {
            new ProcessBuilder("osascript", "-e", script)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (Exception e) {
            log.debug("macOS bildirimi gönderilemedi: {}", e.getMessage());
        }
    }

    private void sendLinux(String stationName, String songTitle) {
        try {
            new ProcessBuilder("notify-send", stationName, songTitle)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (Exception e) {
            log.debug("Linux bildirimi gönderilemedi: {}", e.getMessage());
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
