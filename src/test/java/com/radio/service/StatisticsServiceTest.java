package com.radio.service;

import com.radio.config.RadioConfig;
import com.radio.model.RadioStation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticsServiceTest {

    @TempDir
    Path tempDir;

    private RadioConfig config;
    private StatisticsService service;

    private static final RadioStation STATION = new RadioStation(
            "test-fm", "Test FM", "Test", "Pop", "http://example.com/stream", false);

    @BeforeEach
    void setUp() {
        config = new RadioConfig();
        config.setStatsFile(tempDir.resolve("stats.json").toString());
        service = new StatisticsService(config);
        service.init();
    }

    @Test
    void recordSession_ignoresVeryShortSessions() {
        service.recordSession(STATION, Duration.ofSeconds(29));

        assertThat(service.getTotalSessions()).isZero();
        assertThat(service.getTotalListenTime()).isEqualTo(Duration.ZERO);
        assertThat(service.getTopStations(10)).isEmpty();
    }

    @Test
    void recordSession_accumulatesAndPersists() {
        service.recordSession(STATION, Duration.ofSeconds(40));
        service.recordSession(STATION, Duration.ofSeconds(50));

        assertThat(service.getTotalSessions()).isEqualTo(2);
        assertThat(service.getTotalListenTime()).isEqualTo(Duration.ofSeconds(90));
        assertThat(service.getStationStat(STATION.id()))
                .hasValueSatisfying(stat -> assertThat(stat.totalSeconds()).isEqualTo(90));

        var reloaded = new StatisticsService(config);
        reloaded.init();

        assertThat(reloaded.getTotalSessions()).isEqualTo(2);
        assertThat(reloaded.getStationStat(STATION.id()))
                .hasValueSatisfying(stat -> assertThat(stat.totalSeconds()).isEqualTo(90));
    }

    @Test
    void getTopStations_withNonPositiveLimitReturnsEmptyList() {
        service.recordSession(STATION, Duration.ofSeconds(40));

        assertThat(service.getTopStations(0)).isEmpty();
        assertThat(service.getTopStations(-1)).isEmpty();
    }

    @Test
    void recordSession_withRelativeStatsFileDoesNotThrow() throws Exception {
        Path relative = Path.of("stats-relative-test-" + UUID.randomUUID() + ".json");
        try {
            var relativeConfig = new RadioConfig();
            relativeConfig.setStatsFile(relative.toString());
            var relativeService = new StatisticsService(relativeConfig);
            relativeService.init();

            relativeService.recordSession(STATION, Duration.ofSeconds(40));

            assertThat(Files.exists(relative)).isTrue();
        } finally {
            Files.deleteIfExists(relative);
        }
    }
}
