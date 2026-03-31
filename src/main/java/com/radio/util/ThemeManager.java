package com.radio.util;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ThemeManager {

    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);
    private static final Path THEME_FILE = Path.of(System.getProperty("user.home"), ".radio-shell", "theme");

    private Theme current = Theme.VARSAYILAN;

    @PostConstruct
    public void init() {
        load();
    }

    public Theme getCurrent() {
        return current;
    }

    public boolean setTheme(String name) {
        var theme = Theme.byName(name);
        if (theme == null) return false;
        this.current = theme;
        save();
        return true;
    }

    private void load() {
        try {
            if (Files.exists(THEME_FILE)) {
                String name = Files.readString(THEME_FILE).trim();
                var theme = Theme.byName(name);
                if (theme != null) {
                    current = theme;
                }
            }
        } catch (IOException e) {
            log.warn("Tema dosyası okunamadı: {}", e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(THEME_FILE.getParent());
            Files.writeString(THEME_FILE, current.name());
        } catch (IOException e) {
            log.error("Tema kaydedilemedi: {}", e.getMessage());
        }
    }
}
