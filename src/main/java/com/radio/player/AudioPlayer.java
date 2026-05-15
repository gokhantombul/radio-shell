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
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    private static final int MAX_STREAM_INFO_ATTEMPTS = 4;
    private static final long[] RECONNECT_DELAYS_MS = {2_000, 4_000, 8_000};
    private static final int MAX_HISTORY_SIZE = 50;
    private static final Duration STREAM_INFO_TIMEOUT = Duration.ofSeconds(4);
    private static final Pattern STREAM_TITLE_SINGLE_QUOTED =
            Pattern.compile("(?i)StreamTitle\\s*=\\s*'((?:\\\\'|[^'])*)'\\s*;?");
    private static final Pattern STREAM_TITLE_DOUBLE_QUOTED =
            Pattern.compile("(?i)StreamTitle\\s*=\\s*\"((?:\\\\\"|[^\"])*)\"\\s*;?");
    private static final Pattern STREAM_TITLE_INLINE =
            Pattern.compile("(?i)(?:StreamTitle|icy-title)\\s*[:=]\\s*(.+)");
    private static final Pattern ICY_BITRATE =
            Pattern.compile("(?i)\\bicy-br\\s*[:=]\\s*(\\d+)");
    private static final Pattern BITRATE =
            Pattern.compile("(?i)\\b(?:bitrate\\s*[:=]\\s*)?(\\d+)\\s*kb/s\\b");
    private static final Pattern AUDIO_LINE =
            Pattern.compile("(?i)\\bAudio:\\s*([^,\\s]+)(.*)");
    private static final Pattern SAMPLE_RATE =
            Pattern.compile("(?i)\\b(\\d{4,6})\\s*Hz\\b");
    private static final Pattern CHANNELS =
            Pattern.compile("(?i)\\b(mono|stereo|\\d+\\s+channels?)\\b");
    private static final Pattern CONTENT_TYPE_LINE =
            Pattern.compile("(?i)\\bcontent-type\\s*[:=]\\s*([^;\\s]+)");

    private final RadioConfig config;
    private final SettingsService settingsService;
    private final ScheduledExecutorService sleepScheduler;
    private final ScheduledExecutorService streamInfoScheduler;
    private final HttpClient streamInfoClient;

    private Process currentProcess;
    private RadioStation currentStation;
    private String currentSongTitle;
    private SongInfo currentSongInfo;
    private StreamInfo currentStreamInfo;
    private LocalDateTime currentPlaybackStartedAt;
    private ScheduledFuture<?> streamInfoFuture;
    private int streamInfoAttempts;
    private long playbackGeneration;
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

    public record StreamInfo(Integer bitrateKbps, String codec, Integer sampleRateHz, String channels, String contentType) {
        public boolean hasAny() {
            return bitrateKbps != null
                    || (codec != null && !codec.isBlank())
                    || sampleRateHz != null
                    || (channels != null && !channels.isBlank())
                    || (contentType != null && !contentType.isBlank());
        }

        StreamInfo merge(StreamInfo other) {
            if (other == null || !other.hasAny()) return this;
            return new StreamInfo(
                    bitrateKbps != null ? bitrateKbps : other.bitrateKbps,
                    hasText(codec) ? codec : other.codec,
                    sampleRateHz != null ? sampleRateHz : other.sampleRateHz,
                    hasText(channels) ? channels : other.channels,
                    hasText(contentType) ? contentType : other.contentType);
        }
    }

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
        this.streamInfoScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "StreamInfoProbe");
            t.setDaemon(true);
            return t;
        });
        this.streamInfoClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
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
            currentStreamInfo = null;
            currentPlaybackStartedAt = LocalDateTime.now();
            long streamInfoGeneration = ++playbackGeneration;

            final Process captured = currentProcess;
            startMetadataThread(captured);
            startWatchdog(station, captured, 0);
            startStreamInfoProbe(station, streamInfoGeneration);

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
                    currentPlaybackStartedAt = null;
                    cancelStreamInfoProbe();
                    return false;
                }
            }
            saveLastStation(station);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            currentStation = null;
            currentProcess = null;
            currentPlaybackStartedAt = null;
            cancelStreamInfoProbe();
            return false;
        } catch (IOException e) {
            log.error("Oynatma hatası: {}", e.getMessage());
            currentStation = null;
            currentProcess = null;
            currentPlaybackStartedAt = null;
            cancelStreamInfoProbe();
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

    public synchronized StreamInfo getCurrentStreamInfo() {
        return currentStreamInfo != null && currentStreamInfo.hasAny() ? currentStreamInfo : null;
    }

    public synchronized boolean shouldShowPendingSongInfo(Duration timeout) {
        if (!isPlaying() || currentStation == null || currentPlaybackStartedAt == null) {
            return false;
        }
        if ((currentSongInfo != null && currentSongInfo.rawTitle() != null && !currentSongInfo.rawTitle().isBlank())
                || (currentSongTitle != null && !currentSongTitle.isBlank())) {
            return false;
        }
        return Duration.between(currentPlaybackStartedAt, LocalDateTime.now()).compareTo(timeout) < 0;
    }

    public synchronized Duration getPlaybackElapsed() {
        if (!isPlaying() || currentPlaybackStartedAt == null) {
            return Duration.ZERO;
        }
        Duration elapsed = Duration.between(currentPlaybackStartedAt, LocalDateTime.now());
        return elapsed.isNegative() ? Duration.ZERO : elapsed;
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
        cancelStreamInfoProbe();
        sleepScheduler.shutdownNow();
        streamInfoScheduler.shutdownNow();
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
        currentStreamInfo = null;
        currentPlaybackStartedAt = null;
        cancelStreamInfoProbe();
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
                    var streamInfo = extractStreamInfo(line);
                    if (streamInfo.hasAny()) {
                        synchronized (this) {
                            if (currentProcess == capturedProcess) {
                                updateStreamInfo(streamInfo, playbackGeneration);
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
                        currentStreamInfo = null;
                        currentPlaybackStartedAt = null;
                        cancelStreamInfoProbe();
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
            currentStreamInfo = null;
            currentPlaybackStartedAt = LocalDateTime.now();
            long streamInfoGeneration = ++playbackGeneration;
            saveLastStation(station);
            startMetadataThread(newProcess);
            startWatchdog(station, newProcess, generation);
            startStreamInfoProbe(station, streamInfoGeneration);
            log.info("Yeniden bağlandı ({}/{}): {}", generation, MAX_RECONNECT_ATTEMPTS, station.name());
        } catch (IOException e) {
            log.error("Yeniden bağlanma hatası: {}", e.getMessage());
            currentStation = null;
            currentProcess = null;
            currentStreamInfo = null;
            currentPlaybackStartedAt = null;
            cancelStreamInfoProbe();
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

    private synchronized void startStreamInfoProbe(RadioStation station, long generation) {
        cancelStreamInfoProbe();
        streamInfoAttempts = 0;
        streamInfoFuture = streamInfoScheduler.scheduleWithFixedDelay(
                () -> runStreamInfoProbe(station, generation),
                1,
                3,
                TimeUnit.SECONDS);
    }

    private void runStreamInfoProbe(RadioStation station, long generation) {
        synchronized (this) {
            if (!isCurrentPlayback(station, generation) || hasEnoughStreamInfo(currentStreamInfo)) {
                cancelStreamInfoProbe();
                return;
            }
        }

        StreamInfo streamInfo = probeStreamInfoFromHttp(station);

        synchronized (this) {
            if (!isCurrentPlayback(station, generation)) {
                cancelStreamInfoProbe();
                return;
            }

            streamInfoAttempts++;
            updateStreamInfo(streamInfo, generation);

            if (hasEnoughStreamInfo(currentStreamInfo) || streamInfoAttempts >= MAX_STREAM_INFO_ATTEMPTS) {
                cancelStreamInfoProbe();
            }
        }
    }

    private synchronized void cancelStreamInfoProbe() {
        if (streamInfoFuture != null) {
            streamInfoFuture.cancel(false);
            streamInfoFuture = null;
        }
        streamInfoAttempts = 0;
    }

    private synchronized void updateStreamInfo(StreamInfo streamInfo, long generation) {
        if (generation != playbackGeneration || streamInfo == null || !streamInfo.hasAny()) {
            return;
        }
        currentStreamInfo = currentStreamInfo == null ? streamInfo : currentStreamInfo.merge(streamInfo);
    }

    private synchronized boolean isCurrentPlayback(RadioStation station, long generation) {
        return generation == playbackGeneration
                && station != null
                && currentStation != null
                && station.id().equals(currentStation.id())
                && isPlaying();
    }

    private static boolean hasEnoughStreamInfo(StreamInfo streamInfo) {
        return streamInfo != null
                && streamInfo.bitrateKbps() != null
                && (hasText(streamInfo.codec()) || hasText(streamInfo.contentType()));
    }

    private StreamInfo probeStreamInfoFromHttp(RadioStation station) {
        StreamInfo headInfo = requestStreamInfo(station, "HEAD");
        if (hasEnoughStreamInfo(headInfo)) {
            return headInfo;
        }
        return headInfo.merge(requestStreamInfo(station, "GET"));
    }

    private StreamInfo requestStreamInfo(RadioStation station, String method) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(station.url()))
                    .timeout(STREAM_INFO_TIMEOUT)
                    .header("Icy-MetaData", "1")
                    .header("User-Agent", "Radio Shell/1.0")
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build();

            if ("GET".equals(method)) {
                HttpResponse<InputStream> response = streamInfoClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream ignored = response.body()) {
                    return streamInfoFromHeaders(response.headers());
                }
            }

            HttpResponse<Void> response = streamInfoClient.send(request, HttpResponse.BodyHandlers.discarding());
            return streamInfoFromHeaders(response.headers());
        } catch (Exception e) {
            return emptyStreamInfo();
        }
    }

    private static StreamInfo streamInfoFromHeaders(HttpHeaders headers) {
        Integer bitrate = parsePositiveInt(firstHeader(headers, "icy-br"));
        String contentType = normalizeContentType(firstHeader(headers, "content-type"));
        String codec = inferCodec(contentType);
        return new StreamInfo(bitrate, codec, null, null, contentType);
    }

    private static String firstHeader(HttpHeaders headers, String name) {
        if (headers == null || name == null) return null;
        return headers.map().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .filter(AudioPlayer::hasText)
                .findFirst()
                .orElse(null);
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

    static StreamInfo extractStreamInfo(String line) {
        if (line == null || line.isBlank()) return emptyStreamInfo();

        Integer bitrate = null;
        String codec = null;
        Integer sampleRate = null;
        String channels = null;
        String contentType = null;

        var icyBitrate = ICY_BITRATE.matcher(line);
        if (icyBitrate.find()) {
            bitrate = parsePositiveInt(icyBitrate.group(1));
        }

        var contentTypeMatch = CONTENT_TYPE_LINE.matcher(line);
        if (contentTypeMatch.find()) {
            contentType = normalizeContentType(contentTypeMatch.group(1));
            codec = inferCodec(contentType);
        }

        var audioLine = AUDIO_LINE.matcher(line);
        if (audioLine.find()) {
            codec = normalizeCodec(audioLine.group(1));
            String detail = audioLine.group(2);

            var sampleRateMatch = SAMPLE_RATE.matcher(detail);
            if (sampleRateMatch.find()) {
                sampleRate = parsePositiveInt(sampleRateMatch.group(1));
            }

            var channelsMatch = CHANNELS.matcher(detail);
            if (channelsMatch.find()) {
                channels = channelsMatch.group(1).toLowerCase();
            }

            var bitrateMatch = BITRATE.matcher(detail);
            if (bitrateMatch.find()) {
                bitrate = parsePositiveInt(bitrateMatch.group(1));
            }
        } else if (bitrate == null) {
            var bitrateMatch = BITRATE.matcher(line);
            if (bitrateMatch.find()) {
                bitrate = parsePositiveInt(bitrateMatch.group(1));
            }
        }

        return new StreamInfo(bitrate, codec, sampleRate, channels, contentType);
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

    private static StreamInfo emptyStreamInfo() {
        return new StreamInfo(null, null, null, null, null);
    }

    private static Integer parsePositiveInt(String value) {
        if (!hasText(value)) return null;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizeContentType(String value) {
        if (!hasText(value)) return null;
        String normalized = value.trim().toLowerCase();
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizeCodec(String value) {
        if (!hasText(value)) return null;
        String normalized = value.trim().toLowerCase();
        int openParen = normalized.indexOf('(');
        if (openParen > 0) {
            normalized = normalized.substring(0, openParen).trim();
        }
        return switch (normalized) {
            case "aac_latm", "aacp" -> "aac";
            case "mp3float" -> "mp3";
            default -> normalized;
        };
    }

    private static String inferCodec(String contentType) {
        if (!hasText(contentType)) return null;
        return switch (contentType.toLowerCase()) {
            case "audio/mpeg", "audio/mp3" -> "mp3";
            case "audio/aac", "audio/aacp", "audio/x-aac" -> "aac";
            case "audio/ogg", "application/ogg" -> "ogg";
            case "audio/flac", "audio/x-flac" -> "flac";
            case "audio/wav", "audio/x-wav", "audio/wave" -> "wav";
            case "audio/mp4", "audio/x-m4a" -> "aac";
            default -> null;
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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
