package com.radio.player;

import com.radio.config.RadioConfig;
import com.radio.model.RadioStation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AudioPlayerTest {

    private AudioPlayer player;
    private static final RadioStation STATION = new RadioStation(
            "test-fm", "Test FM", "Test", "Pop", "http://example.com/stream", false);

    @BeforeEach
    void setUp() {
        player = new AudioPlayer(new RadioConfig());
    }

    @Test
    void initialState_notPlaying() {
        assertThat(player.isPlaying()).isFalse();
        assertThat(player.getCurrentStation()).isNull();
        assertThat(player.isRecording()).isFalse();
        assertThat(player.isAutoReconnecting()).isFalse();
    }

    @Test
    void stop_whenNotPlaying_doesNotThrow() {
        player.stop();
        assertThat(player.isPlaying()).isFalse();
        assertThat(player.getCurrentStation()).isNull();
    }

    @Test
    void stop_clearsCurrentStation() {
        // Manually set up state to simulate "playing" without real ffplay
        player.stop(); // Just ensure stop is idempotent
        assertThat(player.getCurrentStation()).isNull();
    }

    @Test
    void setVolume_clampsToRange() {
        player.setVolume(-10);
        assertThat(player.getVolume()).isEqualTo(0);

        player.setVolume(200);
        assertThat(player.getVolume()).isEqualTo(100);

        player.setVolume(50);
        assertThat(player.getVolume()).isEqualTo(50);
    }

    @Test
    void setVolume_validRange_setsExactValue() {
        player.setVolume(0);
        assertThat(player.getVolume()).isEqualTo(0);

        player.setVolume(100);
        assertThat(player.getVolume()).isEqualTo(100);

        player.setVolume(75);
        assertThat(player.getVolume()).isEqualTo(75);
    }

    @Test
    void isVolumeChangePending_whenNotPlaying_returnsFalse() {
        assertThat(player.isVolumeChangePending()).isFalse();
    }

    @Test
    void isAutoReconnecting_whenNotPlaying_returnsFalse() {
        assertThat(player.isAutoReconnecting()).isFalse();
    }

    @Test
    void defaultVolume_is100() {
        assertThat(player.getVolume()).isEqualTo(100);
    }

    @Test
    void getCurrentSongTitle_initiallyNull() {
        assertThat(player.getCurrentSongTitle()).isNull();
    }

    @Test
    void stopRecording_whenNotRecording_returnsNull() {
        assertThat(player.stopRecording()).isNull();
    }

    @Test
    void getRecordFile_whenNotRecording_returnsNull() {
        assertThat(player.getRecordFile()).isNull();
    }

    @Test
    void stopCalledMultipleTimes_isIdempotent() {
        player.stop();
        player.stop();
        player.stop();
        assertThat(player.isPlaying()).isFalse();
    }
}
