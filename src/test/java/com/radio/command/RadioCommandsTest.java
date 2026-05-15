package com.radio.command;

import com.radio.config.RadioConfig;
import com.radio.model.RadioStation;
import com.radio.player.AudioPlayer;
import com.radio.service.SettingsService;
import com.radio.service.StationService;
import com.radio.util.ThemeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RadioCommandsTest {

    @TempDir
    Path tempDir;

    private RadioCommands commands;
    private StationService stationService;
    private FakeAudioPlayer player;
    private RadioConfig config;

    @BeforeEach
    void setUp() {
        config = new RadioConfig();
        config.setFavoritesFile(tempDir.resolve("favorites.json").toString());
        config.setCustomStationsFile(tempDir.resolve("custom-stations.json").toString());
        config.setSettingsFile(tempDir.resolve("settings.json").toString());

        stationService = new StationService(config);
        stationService.init();

        player = new FakeAudioPlayer();
        ThemeManager themeManager = new ThemeManager();

        commands = new RadioCommands(stationService, player, themeManager);
    }

    // --- navigate() NPE guard ---

    @Test
    void next_whenNotPlaying_returnsWarning() {
        player.playing = false;
        String result = commands.next();
        assertThat(result).contains("⚠");
    }

    @Test
    void next_whenPlayingButCurrentStationNull_returnsWarning() {
        player.playing = true;
        player.currentStation = null;

        String result = commands.next();
        assertThat(result).contains("⚠");
    }

    @Test
    void previous_whenNotPlaying_returnsWarning() {
        player.playing = false;
        String result = commands.previous();
        assertThat(result).contains("⚠");
    }

    @Test
    void next_navigatesToNextStation() {
        var stations = stationService.getAllStations();
        var first = stations.get(0);
        var second = stations.get(1);

        // Simulate first station playing, listele was called to set navigationList
        commands.listAll();
        player.playing = true;
        player.currentStation = first;
        player.volume = 80;
        player.playResult = true;

        String result = commands.next();
        assertThat(result).contains(second.name());
        assertThat(player.playedStation).isEqualTo(second);
    }

    // --- Volume validation ---

    @Test
    void volume_outOfRangeLow_returnsError() {
        String result = commands.volume(-1);
        assertThat(result).contains("⚠");
        assertThat(player.lastSetVolume).isNull();
    }

    @Test
    void volume_outOfRangeHigh_returnsError() {
        String result = commands.volume(101);
        assertThat(result).contains("⚠");
        assertThat(player.lastSetVolume).isNull();
    }

    @Test
    void volume_validRange_setsVolume() {
        player.volumeChangePending = false;

        String result = commands.volume(50);
        assertThat(player.lastSetVolume).isEqualTo(50);
        assertThat(result).contains("50");
    }

    @Test
    void volume_whilePlaying_showsPendingNote() {
        player.volume = 70;
        player.volumeChangePending = true;

        String result = commands.volume(70);
        assertThat(result).contains("sonraki 'cal'");
    }

    // --- URL validation in addStation ---

    @Test
    void addStation_invalidUrl_returnsError() {
        String result = commands.addStation("test-id", "Test FM", "Test", "Pop", "not-a-url");
        assertThat(result).contains("⚠");
        assertThat(stationService.findStation("test-id")).isEmpty();
    }

    @Test
    void addStation_ftpUrl_returnsError() {
        String result = commands.addStation("test-ftp", "Test FM", "Test", "Pop", "ftp://example.com/stream");
        assertThat(result).contains("⚠");
        assertThat(stationService.findStation("test-ftp")).isEmpty();
    }

    @Test
    void addStation_validHttpUrl_addsStation() {
        String result = commands.addStation("test-valid", "Valid FM", "Test", "Pop", "http://example.com/stream");
        assertThat(result).contains("✓");
        assertThat(stationService.findStation("test-valid")).isPresent();
    }

    @Test
    void addStation_validHttpsUrl_addsStation() {
        String result = commands.addStation("test-https", "Secure FM", "Test", "Jazz", "https://example.com/stream");
        assertThat(result).contains("✓");
        assertThat(stationService.findStation("test-https")).isPresent();
    }

    @Test
    void addStation_duplicateId_returnsError() {
        commands.addStation("dup-id", "First FM", "Test", "Pop", "http://example.com/stream");
        String result = commands.addStation("dup-id", "Second FM", "Test", "Rock", "http://example.com/rock");
        assertThat(result).contains("⚠");
    }

    // --- stop() null-safety ---

    @Test
    void stop_whenNotPlaying_returnsWarning() {
        player.playing = false;
        String result = commands.stop();
        assertThat(result).contains("⚠");
    }

    @Test
    void stop_whenPlaying_stopsPlayer() {
        var station = stationService.getAllStations().getFirst();
        player.playing = true;
        player.currentStation = station;
        player.recording = false;

        String result = commands.stop();
        assertThat(player.stopped).isTrue();
        assertThat(result).contains(station.name());
    }

    @Test
    void status_whenSongInfoAvailableShowsArtistAndTitle() {
        var station = stationService.getAllStations().getFirst();
        player.playing = true;
        player.currentStation = station;
        player.currentSongInfo = new AudioPlayer.SongInfo(
                "Artist - Song", "Artist", "Song", LocalDateTime.of(2026, 5, 15, 12, 0));

        String result = commands.status();

        assertThat(result).contains("Sanatçı: Artist");
        assertThat(result).contains("Şarkı: Song");
    }

    @Test
    void status_whenSongInfoMissingShowsPendingMetadataMessage() {
        var station = stationService.getAllStations().getFirst();
        player.playing = true;
        player.currentStation = station;
        player.showPendingSongInfo = true;

        String result = commands.status();

        assertThat(result).contains("Bilgi bekleniyor");
    }

    @Test
    void status_whenSongInfoMissingAfterPendingWindowHidesMetadataMessage() {
        var station = stationService.getAllStations().getFirst();
        player.playing = true;
        player.currentStation = station;
        player.showPendingSongInfo = false;

        String result = commands.status();

        assertThat(result).doesNotContain("Bilgi bekleniyor");
    }

    // --- listele / ara ---

    @Test
    void listAll_returnsNonEmptyTable() {
        String result = commands.listAll();
        assertThat(result).contains("│");
        assertThat(result).contains("Toplam:");
    }

    @Test
    void search_noResults_returnsWarning() {
        String result = commands.search("xyzzy_no_match_99999");
        assertThat(result).contains("⚠");
    }

    @Test
    void search_withResults_returnsTable() {
        String result = commands.search("Türkiye");
        assertThat(result).contains("│");
    }

    @Test
    void removeStation_builtInCannotBeRemoved() {
        var builtIn = stationService.getAllStations().getFirst();
        String result = commands.removeStation(builtIn.id());
        assertThat(result).contains("⚠");
        assertThat(stationService.findStation(builtIn.id())).isPresent();
    }

    @Test
    void removeStation_customStationRemovedSuccessfully() {
        commands.addStation("rm-test", "Remove FM", "Test", "Pop", "http://example.com/stream");
        String result = commands.removeStation("rm-test");
        assertThat(result).contains("✓");
        assertThat(stationService.findStation("rm-test")).isEmpty();
    }

    @Test
    void editStation_customStationUpdatesFields() {
        commands.addStation("edit-test", "Old FM", "Test", "Pop", "http://example.com/old");

        String result = commands.editStation(
                "edit-test", "New FM", null, "Jazz", "https://example.com/new");

        assertThat(result).contains("✓");
        var updated = stationService.findStation("edit-test");
        assertThat(updated).isPresent();
        assertThat(updated.get().name()).isEqualTo("New FM");
        assertThat(updated.get().country()).isEqualTo("Test");
        assertThat(updated.get().genre()).isEqualTo("Jazz");
        assertThat(updated.get().url()).isEqualTo("https://example.com/new");
    }

    @Test
    void editStation_builtInStationReturnsWarning() {
        var builtIn = stationService.getAllStations().getFirst();
        String result = commands.editStation(builtIn.id(), "New", null, null, null);
        assertThat(result).contains("⚠");
    }

    @Test
    void importPlaylist_m3uAddsCustomStations() throws IOException {
        Path playlist = tempDir.resolve("stations.m3u");
        Files.writeString(playlist, """
                #EXTM3U
                #EXTINF:-1,Alpha FM
                https://example.com/alpha
                #EXTINF:-1,Beta FM
                ftp://example.com/beta
                """);

        String result = commands.importPlaylist(playlist.toString(), "Test", "Mix", "test");

        assertThat(result).contains("1 istasyon eklendi, 1 atlandı");
        assertThat(stationService.findStation("test-alpha-fm")).isPresent();
    }

    @Test
    void playLast_usesPersistedLastStation() {
        var settings = new SettingsService(config);
        settings.init();
        var station = stationService.getAllStations().getFirst();
        settings.setLastStationId(station.id());
        commands = new RadioCommands(stationService, player, new ThemeManager(), settings);

        String result = commands.playLast();

        assertThat(result).contains(station.name());
        assertThat(player.playedStation).isEqualTo(station);
    }

    @Test
    void sleepTimer_validMinutesSchedulesTimer() {
        String result = commands.sleepTimer(15);

        assertThat(result).contains("15 dakika");
        assertThat(player.sleepDuration).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void cancelSleepTimer_withoutActiveTimerReturnsWarning() {
        String result = commands.cancelSleepTimer();
        assertThat(result).contains("⚠");
    }

    @Test
    void songHistory_withEntriesReturnsTable() {
        var station = stationService.getAllStations().getFirst();
        player.history = List.of(new AudioPlayer.SongHistoryEntry(
                station.id(), station.name(), "Artist - Song", LocalDateTime.of(2026, 5, 15, 12, 0)));

        String result = commands.songHistory(10);

        assertThat(result).contains("Artist - Song");
        assertThat(result).contains(station.name());
    }

    static class FakeAudioPlayer extends AudioPlayer {
        boolean playing;
        boolean recording;
        boolean volumeChangePending;
        boolean stopped;
        boolean sleepTimerActive;
        boolean showPendingSongInfo;
        boolean playResult = true;
        int volume = 100;
        Integer lastSetVolume;
        RadioStation currentStation;
        RadioStation playedStation;
        String currentSongTitle;
        SongInfo currentSongInfo;
        Path recordFile;
        Duration sleepDuration;
        LocalDateTime sleepEndsAt;
        List<SongHistoryEntry> history = List.of();

        FakeAudioPlayer() {
            super(new RadioConfig());
        }

        @Override
        public synchronized boolean play(RadioStation station, java.io.PrintWriter out) {
            playedStation = station;
            if (playResult) {
                playing = true;
                currentStation = station;
            }
            return playResult;
        }

        @Override
        public synchronized void stop() {
            stopped = true;
            playing = false;
            currentStation = null;
        }

        @Override
        public synchronized boolean isPlaying() {
            return playing;
        }

        @Override
        public synchronized RadioStation getCurrentStation() {
            return currentStation;
        }

        @Override
        public synchronized String getCurrentSongTitle() {
            return currentSongTitle;
        }

        @Override
        public synchronized SongInfo getCurrentSongInfo() {
            return currentSongInfo;
        }

        @Override
        public synchronized int getVolume() {
            return volume;
        }

        @Override
        public synchronized void setVolume(int vol) {
            lastSetVolume = vol;
            volume = Math.max(0, Math.min(100, vol));
        }

        @Override
        public synchronized boolean isVolumeChangePending() {
            return volumeChangePending;
        }

        @Override
        public synchronized boolean isAutoReconnecting() {
            return false;
        }

        @Override
        public synchronized boolean shouldShowPendingSongInfo(Duration timeout) {
            return showPendingSongInfo;
        }

        @Override
        public synchronized Path startRecording() {
            recording = true;
            recordFile = Path.of("recording.mp3");
            return recordFile;
        }

        @Override
        public synchronized Path stopRecording() {
            recording = false;
            return recordFile;
        }

        @Override
        public synchronized boolean isRecording() {
            return recording;
        }

        @Override
        public synchronized Path getRecordFile() {
            return recordFile;
        }

        @Override
        public synchronized void scheduleSleep(Duration duration) {
            sleepDuration = duration;
            sleepTimerActive = true;
            sleepEndsAt = LocalDateTime.now().plus(duration);
        }

        @Override
        public synchronized boolean cancelSleepTimer() {
            boolean wasActive = sleepTimerActive;
            sleepTimerActive = false;
            return wasActive;
        }

        @Override
        public synchronized boolean isSleepTimerActive() {
            return sleepTimerActive;
        }

        @Override
        public synchronized LocalDateTime getSleepEndsAt() {
            return sleepEndsAt;
        }

        @Override
        public synchronized Duration getSleepRemaining() {
            return sleepDuration != null ? sleepDuration : Duration.ZERO;
        }

        @Override
        public synchronized List<SongHistoryEntry> getSongHistory() {
            return history;
        }
    }
}
