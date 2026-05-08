package com.radio.player;

import com.radio.config.RadioConfig;
import com.radio.model.RadioStation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class AudioPlayer {

    private static final Logger log = LoggerFactory.getLogger(AudioPlayer.class);
    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final long[] RECONNECT_DELAYS_MS = {2_000, 4_000, 8_000};

    private final RadioConfig config;

    private Process currentProcess;
    private RadioStation currentStation;
    private String currentSongTitle;
    private int volume = 100;

    // true while user has explicitly requested a stop — prevents watchdog reconnects
    private volatile boolean stopRequested = false;

    private Process recordProcess;
    private Path recordFile;

    public AudioPlayer(RadioConfig config) {
        this.config = config;
    }

    public synchronized boolean play(RadioStation station) {
        return play(station, null);
    }

    public synchronized boolean play(RadioStation station, PrintWriter out) {
        // Signal any running watchdog to stand down before killing the old process
        stopRequested = true;
        killCurrentProcess();
        stopRequested = false;

        List<String> command = buildCommand(station);
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            currentProcess = pb.start();
            currentStation = station;
            currentSongTitle = null;

            final Process captured = currentProcess;
            startMetadataThread(captured);
            startWatchdog(station, captured, 0);

            // Show connection progress animation
            String[] spinner = {"⣾", "⣽", "⣻", "⢿", "⡿", "⣟", "⣯", "⣷"};
            int steps = 25;
            for (int i = 0; i < steps; i++) {
                if (out != null) {
                    int pct = (i + 1) * 100 / steps;
                    int filled = pct / 5;
                    String bar = "█".repeat(filled) + "░".repeat(20 - filled);
                    out.print("\r  " + spinner[i % spinner.length] + " Baglaniyor [" + bar + "] %" + pct);
                    out.flush();
                }
                Thread.sleep(80);
                if (!currentProcess.isAlive()) break;
            }
            if (out != null) {
                out.print("\r" + " ".repeat(60) + "\r");
                out.flush();
            }

            if (!currentProcess.isAlive()) {
                if (currentProcess.exitValue() != 0) {
                    currentStation = null;
                    currentProcess = null;
                    return false;
                }
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            currentStation = null;
            currentProcess = null;
            return false;
        } catch (IOException e) {
            log.error("Oynatma hatası: {}", e.getMessage());
            currentStation = null;
            currentProcess = null;
            return false;
        }
    }

    public synchronized void stop() {
        stopRequested = true;
        killCurrentProcess();
    }

    public synchronized boolean isPlaying() {
        return currentProcess != null && currentProcess.isAlive();
    }

    public synchronized RadioStation getCurrentStation() {
        return currentStation;
    }

    public synchronized String getCurrentSongTitle() {
        return currentSongTitle;
    }

    public synchronized int getVolume() {
        return volume;
    }

    public synchronized void setVolume(int vol) {
        this.volume = Math.max(0, Math.min(100, vol));
    }

    public synchronized boolean isVolumeChangePending() {
        return isPlaying();
    }

    public synchronized boolean isAutoReconnecting() {
        return !stopRequested && !isPlaying() && currentStation != null;
    }

    public synchronized Path startRecording() throws IOException {
        if (currentStation == null || !isPlaying()) return null;
        stopRecording();

        Path recordDir = Path.of(config.getRecordingsDir());
        Files.createDirectories(recordDir);

        String safeName = currentStation.name().replaceAll("[^a-zA-Z0-9çÇğĞıİöÖşŞüÜ_ -]", "");
        String timestamp = LocalDateTime.now().format(FILE_DATE_FMT);
        recordFile = recordDir.resolve(safeName + "_" + timestamp + ".mp3");

        List<String> cmd = List.of(
                "ffmpeg", "-y",
                "-i", currentStation.url(),
                "-acodec", "libmp3lame",
                "-ab", "192k",
                "-loglevel", "quiet",
                recordFile.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        recordProcess = pb.start();
        return recordFile;
    }

    public synchronized Path stopRecording() {
        if (recordProcess != null && recordProcess.isAlive()) {
            recordProcess.destroy();
            try {
                recordProcess.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Path stoppedFile = recordFile;
        recordProcess = null;
        recordFile = null;
        return stoppedFile;
    }

    public synchronized boolean isRecording() {
        return recordProcess != null && recordProcess.isAlive();
    }

    public synchronized Path getRecordFile() {
        return recordFile;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<String> buildCommand(RadioStation station) {
        List<String> command = new ArrayList<>();
        command.add(config.getPlayer().getCommand());
        for (String arg : config.getPlayer().getArgs()) {
            if ("quiet".equals(arg)) {
                command.add("info");
            } else if ("-loglevel".equals(arg)) {
                command.add("-loglevel");
            } else {
                command.add(arg);
            }
        }
        command.add("-volume");
        command.add(String.valueOf(volume));
        command.add(station.url());
        return command;
    }

    private void killCurrentProcess() {
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

    private void startMetadataThread(Process capturedProcess) {
        Thread t = new Thread(() -> {
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(capturedProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("StreamTitle")) {
                        int start = line.indexOf("StreamTitle='") + 13;
                        if (start > 12) {
                            int end = line.indexOf("';", start);
                            if (end > start) {
                                String title = line.substring(start, end).trim();
                                synchronized (this) {
                                    if (currentProcess == capturedProcess) {
                                        this.currentSongTitle = title;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                // Process ended or stream closed — expected on stop()
            }
        }, "MetadataParser");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Watches for unexpected process death and retries up to MAX_RECONNECT_ATTEMPTS times.
     * The generation counter prevents infinite reconnect loops on persistently dead streams.
     */
    private void startWatchdog(RadioStation station, Process monitoredProcess, int generation) {
        Thread watchdog = new Thread(() -> {
            try {
                monitoredProcess.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // User requested stop — don't reconnect
            if (stopRequested) return;

            // A newer play/reconnect already superseded this watchdog
            synchronized (AudioPlayer.this) {
                if (currentProcess != monitoredProcess) return;
            }

            if (generation >= MAX_RECONNECT_ATTEMPTS) {
                log.error("Yeniden bağlanma denemesi tükendi ({}): {}", MAX_RECONNECT_ATTEMPTS, station.name());
                synchronized (AudioPlayer.this) {
                    if (!stopRequested && currentProcess == monitoredProcess) {
                        currentStation = null;
                        currentProcess = null;
                    }
                }
                return;
            }

            long delay = RECONNECT_DELAYS_MS[generation];
            log.warn("Stream kesildi ({}/{}), {}ms sonra yeniden bağlanılıyor: {}",
                    generation + 1, MAX_RECONNECT_ATTEMPTS, delay, station.name());

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            if (!stopRequested) {
                reconnectSilent(station, generation + 1);
            }
        }, "Watchdog-" + station.id());
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private synchronized void reconnectSilent(RadioStation station, int generation) {
        if (stopRequested) return;
        try {
            ProcessBuilder pb = new ProcessBuilder(buildCommand(station));
            pb.redirectErrorStream(true);
            Process newProcess = pb.start();
            currentProcess = newProcess;
            currentStation = station;
            currentSongTitle = null;
            startMetadataThread(newProcess);
            startWatchdog(station, newProcess, generation);
            log.info("Yeniden bağlandı ({}/{}): {}", generation, MAX_RECONNECT_ATTEMPTS, station.name());
        } catch (IOException e) {
            log.error("Yeniden bağlanma hatası: {}", e.getMessage());
            currentStation = null;
            currentProcess = null;
        }
    }
}
