package com.radio.player;

import com.radio.config.RadioConfig;
import com.radio.model.RadioStation;
import com.radio.service.SettingsService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class AudioPlayer {

    private static final Logger log = LoggerFactory.getLogger(AudioPlayer.class);
    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final long[] RECONNECT_DELAYS_MS = {2_000, 4_000, 8_000};
    private static final int MAX_HISTORY_SIZE = 50;
    private static final Pattern STREAM_TITLE_SINGLE_QUOTED =
            Pattern.compile("(?i)StreamTitle\\s*=\\s*'((?:\\\\'|[^'])*)'\\s*;?");
    private static final Pattern STREAM_TITLE_DOUBLE_QUOTED =
            Pattern.compile("(?i)StreamTitle\\s*=\\s*\"((?:\\\\\"|[^\"])*)\"\\s*;?");
    private static final Pattern STREAM_TITLE_INLINE =
            Pattern.compile("(?i)(?:StreamTitle|icy-title)\\s*[:=]\\s*(.+)");

    private final RadioConfig config;
    private final SettingsService settingsService;
    private final ScheduledExecutorService sleepScheduler;

    private Process currentProcess;
    private RadioStation currentStation;
    private String currentSongTitle;
    private SongInfo currentSongInfo;
    private int volume = 100;

    // true while user has explicitly requested a stop — prevents watchdog reconnects
    private volatile boolean stopRequested = false;

    private Process recordProcess;
    private Path recordFile;

    private ScheduledFuture<?> sleepFuture;
    private LocalDateTime sleepEndsAt;

    private final Deque<SongHistoryEntry> songHistory = new ArrayDeque<>();

    public record SongInfo(String rawTitle, String artist, String title, LocalDateTime updatedAt) {
        public String displayTitle() {
            if (artist != null && !artist.isBlank() && title != null && !title.isBlank()) {
                return artist + " - " + title;
            }
            return rawTitle;
        }
    }

    public record SongHistoryEntry(String stationId, String stationName, String title, LocalDateTime playedAt) {}

    @Autowired
    public AudioPlayer(RadioConfig config, SettingsService settingsService) {
        this.config = config;
        this.settingsService = settingsService;
        this.volume = settingsService != null ? settingsService.getVolume() : 100;
        this.sleepScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SleepTimer");
            t.setDaemon(true);
            return t;
        });
    }

    public AudioPlayer(RadioConfig config) {
        this(config, null);
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
            currentSongInfo = null;

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
            saveLastStation(station);
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
        cancelSleepTimer();
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

    public synchronized SongInfo getCurrentSongInfo() {
        return currentSongInfo;
    }

    public synchronized int getVolume() {
        return volume;
    }

    public synchronized void setVolume(int vol) {
        this.volume = Math.max(0, Math.min(100, vol));
        if (settingsService != null) {
            settingsService.setVolume(this.volume);
        }
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

    public synchronized void scheduleSleep(Duration duration) {
        cancelSleepTimer();
        sleepEndsAt = LocalDateTime.now().plus(duration);
        sleepFuture = sleepScheduler.schedule(this::stopFromSleepTimer,
                duration.toMillis(), TimeUnit.MILLISECONDS);
    }

    public synchronized boolean cancelSleepTimer() {
        boolean active = sleepFuture != null && !sleepFuture.isDone();
        if (sleepFuture != null) {
            sleepFuture.cancel(false);
        }
        sleepFuture = null;
        sleepEndsAt = null;
        return active;
    }

    public synchronized boolean isSleepTimerActive() {
        return sleepFuture != null && !sleepFuture.isDone() && sleepEndsAt != null;
    }

    public synchronized LocalDateTime getSleepEndsAt() {
        return sleepEndsAt;
    }

    public synchronized Duration getSleepRemaining() {
        if (!isSleepTimerActive()) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(LocalDateTime.now(), sleepEndsAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public synchronized List<SongHistoryEntry> getSongHistory() {
        return List.copyOf(songHistory);
    }

    @PreDestroy
    public synchronized void shutdown() {
        cancelSleepTimer();
        sleepScheduler.shutdownNow();
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
        if (isFfplayCommand() && !hasOption(command, "-icy")) {
            command.add("-icy");
            command.add("1");
        }
        command.add("-volume");
        command.add(String.valueOf(volume));
        command.add(station.url());
        return command;
    }

    private boolean isFfplayCommand() {
        String command = config.getPlayer().getCommand();
        return command != null && Path.of(command).getFileName().toString().equals("ffplay");
    }

    private boolean hasOption(List<String> command, String option) {
        return command.stream().anyMatch(option::equals);
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
        currentSongTitle = null;
        currentSongInfo = null;
    }

    private void startMetadataThread(Process capturedProcess) {
        Thread t = new Thread(() -> {
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(capturedProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String title = extractSongTitle(line);
                    if (title != null) {
                        synchronized (this) {
                            if (currentProcess == capturedProcess) {
                                updateSongTitle(title);
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
            currentSongInfo = null;
            saveLastStation(station);
            startMetadataThread(newProcess);
            startWatchdog(station, newProcess, generation);
            log.info("Yeniden bağlandı ({}/{}): {}", generation, MAX_RECONNECT_ATTEMPTS, station.name());
        } catch (IOException e) {
            log.error("Yeniden bağlanma hatası: {}", e.getMessage());
            currentStation = null;
            currentProcess = null;
        }
    }

    private synchronized void stopFromSleepTimer() {
        if (recordProcess != null && recordProcess.isAlive()) {
            stopRecording();
        }
        stopRequested = true;
        killCurrentProcess();
        sleepFuture = null;
        sleepEndsAt = null;
    }

    private void updateSongTitle(String title) {
        if (title == null || title.isBlank()) return;
        SongInfo songInfo = toSongInfo(title);
        if (songInfo.rawTitle().isBlank()) return;
        this.currentSongTitle = songInfo.rawTitle();
        this.currentSongInfo = songInfo;

        if (currentStation == null) return;
        var latest = songHistory.peekFirst();
        if (latest != null
                && latest.stationId().equals(currentStation.id())
                && latest.title().equals(songInfo.rawTitle())) {
            return;
        }

        songHistory.addFirst(new SongHistoryEntry(
                currentStation.id(),
                currentStation.name(),
                songInfo.rawTitle(),
                LocalDateTime.now()));

        while (songHistory.size() > MAX_HISTORY_SIZE) {
            songHistory.removeLast();
        }
    }

    private void saveLastStation(RadioStation station) {
        if (settingsService != null && station != null) {
            settingsService.setLastStationId(station.id());
        }
    }

    static String extractSongTitle(String line) {
        if (line == null || line.isBlank()) return null;

        var singleQuoted = STREAM_TITLE_SINGLE_QUOTED.matcher(line);
        if (singleQuoted.find()) {
            return cleanMetadataValue(singleQuoted.group(1));
        }

        var doubleQuoted = STREAM_TITLE_DOUBLE_QUOTED.matcher(line);
        if (doubleQuoted.find()) {
            return cleanMetadataValue(doubleQuoted.group(1));
        }

        var inline = STREAM_TITLE_INLINE.matcher(line);
        if (inline.find()) {
            return cleanMetadataValue(inline.group(1));
        }

        return null;
    }

    static SongInfo toSongInfo(String rawTitle) {
        String normalized = cleanMetadataValue(rawTitle);
        if (normalized == null) {
            return new SongInfo("", null, "", LocalDateTime.now());
        }
        String artist = null;
        String title = normalized;

        for (String separator : List.of(" - ", " – ", " — ", " | ")) {
            int idx = normalized.indexOf(separator);
            if (idx > 0 && idx + separator.length() < normalized.length()) {
                artist = normalized.substring(0, idx).trim();
                title = normalized.substring(idx + separator.length()).trim();
                break;
            }
        }

        return new SongInfo(normalized, artist, title, LocalDateTime.now());
    }

    private static String cleanMetadataValue(String value) {
        if (value == null) return null;
        String cleaned = value.trim()
                .replace("\\'", "'")
                .replace("\\\"", "\"")
                .replace("&apos;", "'")
                .replace("&quot;", "\"");

        while (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        if ((cleaned.startsWith("'") && cleaned.endsWith("'"))
                || (cleaned.startsWith("\"") && cleaned.endsWith("\""))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned.isBlank() ? null : cleaned;
    }
}
