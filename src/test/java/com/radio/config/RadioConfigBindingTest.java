package com.radio.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RadioConfigBindingTest {

    @Test
    void kebabCaseFilePathsBindToConfig() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("radio.favorites-file", "/tmp/favs.json");
        props.put("radio.custom-stations-file", "/tmp/custom.json");
        props.put("radio.settings-file", "/tmp/settings.json");
        props.put("radio.recordings-dir", "/tmp/recs");
        props.put("radio.stats-file", "/tmp/stats.json");

        var env = new AbstractEnvironment() {};
        env.getPropertySources().addFirst(new MapPropertySource("test", props));

        RadioConfig config = Binder.get(env)
                .bind("radio", Bindable.of(RadioConfig.class))
                .orElseGet(RadioConfig::new);

        assertEquals("/tmp/favs.json", config.getFavoritesFile());
        assertEquals("/tmp/custom.json", config.getCustomStationsFile());
        assertEquals("/tmp/settings.json", config.getSettingsFile());
        assertEquals("/tmp/recs", config.getRecordingsDir());
        assertEquals("/tmp/stats.json", config.getStatsFile());
    }
}
