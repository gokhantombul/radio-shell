package com.radio.util;

import java.util.LinkedHashMap;
import java.util.Map;

public record Theme(String name, String description, String primary, String secondary, String accent, String bold, String reset) {

    private static final String ANSI_RESET = "\033[0m";
    private static final String ANSI_BOLD  = "\033[1m";

    public static final Theme VARSAYILAN = new Theme(
            "varsayilan", "Klasik cyan-yeşil tema",
            "\033[36m", "\033[32m", "\033[33m", ANSI_BOLD, ANSI_RESET);

    public static final Theme OKYANUS = new Theme(
            "okyanus", "Mavi tonları",
            "\033[34m", "\033[96m", "\033[94m", ANSI_BOLD, ANSI_RESET);

    public static final Theme GECE = new Theme(
            "gece", "Koyu mor-magenta tema",
            "\033[35m", "\033[95m", "\033[33m", ANSI_BOLD, ANSI_RESET);

    public static final Theme RETRO = new Theme(
            "retro", "Yeşil fosforlu terminal",
            "\033[32m", "\033[92m", "\033[33m", ANSI_BOLD, ANSI_RESET);

    public static final Theme ATES = new Theme(
            "ates", "Kırmızı-turuncu sıcak tonlar",
            "\033[31m", "\033[91m", "\033[33m", ANSI_BOLD, ANSI_RESET);

    public static final Theme MONO = new Theme(
            "mono", "Renksiz sade tema",
            "\033[37m", "\033[97m", "\033[37m", ANSI_BOLD, ANSI_RESET);

    private static final Map<String, Theme> THEMES = new LinkedHashMap<>();

    static {
        THEMES.put(VARSAYILAN.name(), VARSAYILAN);
        THEMES.put(OKYANUS.name(), OKYANUS);
        THEMES.put(GECE.name(), GECE);
        THEMES.put(RETRO.name(), RETRO);
        THEMES.put(ATES.name(), ATES);
        THEMES.put(MONO.name(), MONO);
    }

    public static Map<String, Theme> all() {
        return THEMES;
    }

    public static Theme byName(String name) {
        return THEMES.get(name);
    }
}
