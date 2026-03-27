package com.radio.player;

import com.radio.config.RadioConfig;
import com.radio.model.RadioStation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class AudioPlayer {

    private static final Logger log = LoggerFactory.getLogger(AudioPlayer.class);

    private final RadioConfig config;
    private Process currentProcess;
    private RadioStation currentStation;
    private int volume = 100;

    public AudioPlayer(RadioConfig config) {
        this.config = config;
    }

    public synchronized boolean play(RadioStation station) {
        stop();

        List<String> command = new ArrayList<>();
        command.add(config.getPlayer().getCommand());
        command.addAll(config.getPlayer().getArgs());
        command.add("-volume");
        command.add(String.valueOf(volume));
        command.add(station.url());

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            currentProcess = pb.start();
            currentStation = station;

            // Check if process started successfully (give it a moment)
            Thread.sleep(500);
            if (!currentProcess.isAlive()) {
                int exit = currentProcess.exitValue();
                if (exit != 0) {
                    currentStation = null;
                    currentProcess = null;
                    return false;
                }
            }
            return true;
        } catch (IOException | InterruptedException e) {
            log.error("Oynatma hatası: {}", e.getMessage());
            currentStation = null;
            currentProcess = null;
            return false;
        }
    }

    public synchronized void stop() {
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroyForcibly();
            try {
                currentProcess.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        currentProcess = null;
        currentStation = null;
    }

    public synchronized boolean isPlaying() {
        return currentProcess != null && currentProcess.isAlive();
    }

    public synchronized RadioStation getCurrentStation() {
        return currentStation;
    }

    public synchronized int getVolume() {
        return volume;
    }

    public synchronized void setVolume(int vol) {
        this.volume = Math.max(0, Math.min(100, vol));
        if (isPlaying()) {
            RadioStation station = currentStation;
            play(station);
        }
    }
}
