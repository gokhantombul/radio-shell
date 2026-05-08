package com.radio.command;

import com.radio.config.RadioConfig;
import com.radio.model.RadioStation;
import com.radio.player.AudioPlayer;
import com.radio.service.StationService;
import com.radio.util.ThemeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RadioCommandsTest {

    @TempDir
    Path tempDir;

    private RadioCommands commands;
    private StationService stationService;
    private AudioPlayer player;

    @BeforeEach
    void setUp() {
        RadioConfig config = new RadioConfig();
        config.setFavoritesFile(tempDir.resolve("favorites.json").toString());
        config.setCustomStationsFile(tempDir.resolve("custom-stations.json").toString());

        stationService = new StationService(config);
        stationService.init();

        player = mock(AudioPlayer.class);
        ThemeManager themeManager = mock(ThemeManager.class);

        commands = new RadioCommands(stationService, player, themeManager);
    }

    // --- navigate() NPE guard ---

    @Test
    void next_whenNotPlaying_returnsWarning() {
        when(player.isPlaying()).thenReturn(false);
        String result = commands.next();
        assertThat(result).contains("⚠");
    }

    @Test
    void next_whenPlayingButCurrentStationNull_returnsWarning() {
        when(player.isPlaying()).thenReturn(true);
        when(player.getCurrentStation()).thenReturn(null);

        String result = commands.next();
        assertThat(result).contains("⚠");
    }

    @Test
    void previous_whenNotPlaying_returnsWarning() {
        when(player.isPlaying()).thenReturn(false);
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
        when(player.isPlaying()).thenReturn(true);
        when(player.getCurrentStation()).thenReturn(first);
        when(player.getVolume()).thenReturn(80);
        when(player.play(eq(second), any())).thenReturn(true);

        String result = commands.next();
        assertThat(result).contains(second.name());
        verify(player).play(eq(second), any());
    }

    // --- Volume validation ---

    @Test
    void volume_outOfRangeLow_returnsError() {
        String result = commands.volume(-1);
        assertThat(result).contains("⚠");
        verify(player, never()).setVolume(anyInt());
    }

    @Test
    void volume_outOfRangeHigh_returnsError() {
        String result = commands.volume(101);
        assertThat(result).contains("⚠");
        verify(player, never()).setVolume(anyInt());
    }

    @Test
    void volume_validRange_setsVolume() {
        when(player.getVolume()).thenReturn(50);
        when(player.isVolumeChangePending()).thenReturn(false);

        String result = commands.volume(50);
        verify(player).setVolume(50);
        assertThat(result).contains("50");
    }

    @Test
    void volume_whilePlaying_showsPendingNote() {
        when(player.getVolume()).thenReturn(70);
        when(player.isVolumeChangePending()).thenReturn(true);

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
        when(player.isPlaying()).thenReturn(false);
        String result = commands.stop();
        assertThat(result).contains("⚠");
    }

    @Test
    void stop_whenPlaying_stopsPlayer() {
        var station = stationService.getAllStations().getFirst();
        when(player.isPlaying()).thenReturn(true);
        when(player.getCurrentStation()).thenReturn(station);
        when(player.isRecording()).thenReturn(false);

        String result = commands.stop();
        verify(player).stop();
        assertThat(result).contains(station.name());
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
}

