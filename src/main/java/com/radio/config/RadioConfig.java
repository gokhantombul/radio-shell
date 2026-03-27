package com.radio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "radio")
public class RadioConfig {

    private PlayerConfig player = new PlayerConfig();
    private StationsConfig stations = new StationsConfig();
    private String favoritesFile;
    private String customStationsFile;

    public PlayerConfig getPlayer() { return player; }
    public void setPlayer(PlayerConfig player) { this.player = player; }
    public StationsConfig getStations() { return stations; }
    public void setStations(StationsConfig stations) { this.stations = stations; }
    public String getFavoritesFile() { return favoritesFile; }
    public void setFavoritesFile(String f) { this.favoritesFile = f; }
    public String getCustomStationsFile() { return customStationsFile; }
    public void setCustomStationsFile(String f) { this.customStationsFile = f; }

    public static class PlayerConfig {
        private String command = "ffplay";
        private List<String> args = List.of("-nodisp", "-hide_banner", "-loglevel", "quiet", "-autoexit");

        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public List<String> getArgs() { return args; }
        public void setArgs(List<String> args) { this.args = args; }
    }

    public static class StationsConfig {
        private String file = "classpath:stations.json";

        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
    }
}
