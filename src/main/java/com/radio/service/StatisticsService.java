package com.radio.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.radio.config.RadioConfig;
import com.radio.model.RadioStation;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);
    private static final long MIN_SESSION_SECONDS = 30;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StationStat(
            String stationId,
            String stationName,
            String country,
            String genre,
            long totalSeconds,
            int sessionCount
    ) {
        StationStat add(long seconds) {
            return new StationStat(stationId, stationName, country, genre,
                    totalSeconds + seconds, sessionCount + 1);
        }
    }

    private final RadioConfig config;
    private final ObjectMapper mapper;
    private final Map<String, StationStat> stats = new ConcurrentHashMap<>();

    public StatisticsService(RadioConfig config) {
        this.config = config;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void init() {
        load();
    }

    public synchronized void recordSession(RadioStation station, Duration duration) {
        if (station == null || duration == null || duration.toSeconds() < MIN_SESSION_SECONDS) return;
        long seconds = duration.toSeconds();
        stats.merge(station.id(),
                new StationStat(station.id(), station.name(), station.country(), station.genre(), seconds, 1),
                (existing, incoming) -> existing.add(seconds));
        save();
    }

    public synchronized List<StationStat> getTopStations(int limit) {
        return stats.values().stream()
                .sorted(Comparator.comparingLong(StationStat::totalSeconds).reversed())
                .limit(limit)
                .toList();
    }

    public synchronized Duration getTotalListenTime() {
        long total = stats.values().stream().mapToLong(StationStat::totalSeconds).sum();
        return Duration.ofSeconds(total);
    }

    public synchronized int getTotalSessions() {
        return stats.values().stream().mapToInt(StationStat::sessionCount).sum();
    }

    private void load() {
        String path = config.getStatsFile();
        if (path == null) return;
        File file = new File(path);
        if (!file.exists()) return;
        try {
            StationStat[] loaded = mapper.readValue(file, StationStat[].class);
            for (StationStat s : loaded) {
                stats.put(s.stationId(), s);
            }
        } catch (IOException e) {
            log.error("İstatistikler yüklenemedi: {}", e.getMessage());
        }
    }

    private void save() {
        String path = config.getStatsFile();
        if (path == null) return;
        try {
            Path p = Path.of(path);
            Files.createDirectories(p.getParent());
            mapper.writeValue(p.toFile(), stats.values().toArray());
        } catch (IOException e) {
            log.error("İstatistikler kaydedilemedi: {}", e.getMessage());
        }
    }
}
