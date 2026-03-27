package com.radio.config;

import com.radio.player.AudioPlayer;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class ShellConfig {

    private final AudioPlayer player;

    public ShellConfig(AudioPlayer player) {
        this.player = player;
    }

    @PreDestroy
    public void cleanup() {
        player.stop();
    }
}
