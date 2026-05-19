package com.radio.service;

import com.radio.util.UIUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class SystemInfoService {

    private static final int PANEL_INNER_WIDTH = 122;
    private static final Duration COMMAND_TIMEOUT = Duration.ofMillis(700);

    private final com.sun.management.OperatingSystemMXBean osBean;

    public SystemInfoService() {
        var bean = ManagementFactory.getOperatingSystemMXBean();
        this.osBean = bean instanceof com.sun.management.OperatingSystemMXBean sunBean ? sunBean : null;
    }

    public String formatSystemInfo(ProcessHandle playerProcess) {
        return formatSnapshot(snapshot(playerProcess));
    }

    public Snapshot snapshot(ProcessHandle playerProcess) {
        long appMemoryBytes = processResidentMemoryBytes(ProcessHandle.current())
                .orElseGet(this::heapUsedBytes);
        long playerMemoryBytes = playerProcess != null
                ? processResidentMemoryBytes(playerProcess).orElse(0L)
                : 0L;
        long totalMemoryBytes = totalMemoryBytes();
        long freeMemoryBytes = freeMemoryBytes();
        long usedMemoryBytes = totalMemoryBytes > 0 && freeMemoryBytes >= 0
                ? Math.max(0L, totalMemoryBytes - freeMemoryBytes)
                : -1L;

        return new Snapshot(
                appMemoryBytes,
                playerMemoryBytes,
                appMemoryBytes + playerMemoryBytes,
                osDisplayName(),
                System.getProperty("java.version", "Bilinmiyor"),
                cpuLoad(),
                usedMemoryBytes,
                totalMemoryBytes);
    }

    public String formatSnapshot(Snapshot snapshot) {
        List<String> rows = List.of(
                formatRow("Radyo Shell (Java):", formatMb(snapshot.appMemoryBytes()),
                        "İşletim Sistemi:", snapshot.osName()),
                formatRow("ffplay (Motor):", formatMb(snapshot.playerMemoryBytes()),
                        "Java Sürümü:", snapshot.javaVersion()),
                formatRow("Toplam Kullanım:", formatMb(snapshot.totalProcessMemoryBytes()),
                        "CPU Kullanımı:", formatPercent(snapshot.cpuLoad())),
                formatRow("", "", "Sistem RAM:", formatRam(snapshot.usedMemoryBytes(), snapshot.totalMemoryBytes()))
        );
        return "\n" + titledPanel("Sistem Bilgileri", rows, PANEL_INNER_WIDTH);
    }

    private Optional<Long> processResidentMemoryBytes(ProcessHandle processHandle) {
        if (processHandle == null || !processHandle.isAlive()) {
            return Optional.empty();
        }
        return commandOutput(List.of("ps", "-o", "rss=", "-p", String.valueOf(processHandle.pid())), COMMAND_TIMEOUT)
                .flatMap(SystemInfoService::parseRssKilobytes)
                .map(kilobytes -> kilobytes * 1024L);
    }

    private static Optional<Long> parseRssKilobytes(String output) {
        if (output == null || output.isBlank()) {
            return Optional.empty();
        }
        String firstToken = output.trim().split("\\s+")[0];
        try {
            return Optional.of(Long.parseLong(firstToken));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<String> commandOutput(List<String> command, Duration timeout) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            return Optional.of(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim())
                    .filter(s -> !s.isBlank());
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private long heapUsedBytes() {
        Runtime runtime = Runtime.getRuntime();
        return Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
    }

    @SuppressWarnings("deprecation")
    private long totalMemoryBytes() {
        return osBean != null ? osBean.getTotalPhysicalMemorySize() : -1L;
    }

    @SuppressWarnings("deprecation")
    private long freeMemoryBytes() {
        return osBean != null ? osBean.getFreePhysicalMemorySize() : -1L;
    }

    private double cpuLoad() {
        return osBean != null ? osBean.getCpuLoad() : -1.0;
    }

    private String osDisplayName() {
        Optional<String> unameName = commandOutput(List.of("uname", "-s"), COMMAND_TIMEOUT);
        Optional<String> unameVersion = commandOutput(List.of("uname", "-r"), COMMAND_TIMEOUT);
        if (unameName.isPresent() && unameVersion.isPresent()) {
            return unameName.get() + " " + unameVersion.get();
        }
        String name = System.getProperty("os.name", "Bilinmiyor");
        String version = System.getProperty("os.version", "");
        return version.isBlank() ? name : name + " " + version;
    }

    private static String formatRow(String leftLabel, String leftValue, String rightLabel, String rightValue) {
        String left = leftLabel.isBlank() && leftValue.isBlank()
                ? " ".repeat(34)
                : "%-22s %10s".formatted(leftLabel, leftValue);
        String right = "%-18s %s".formatted(rightLabel, rightValue);
        return "  " + left + "   " + right;
    }

    private static String titledPanel(String title, List<String> rows, int innerWidth) {
        StringBuilder sb = new StringBuilder();
        sb.append(topBorder(title, innerWidth)).append('\n');
        for (String row : rows) {
            sb.append("│ ");
            sb.append(UIUtils.padRight(row, innerWidth));
            sb.append(" │\n");
        }
        sb.append("╰").append("─".repeat(innerWidth + 2)).append("╯\n");
        return sb.toString();
    }

    private static String topBorder(String title, int innerWidth) {
        String caption = " " + title + " ";
        int lineWidth = innerWidth + 2 - UIUtils.getVisualWidth(caption);
        int left = Math.max(0, lineWidth / 2);
        int right = Math.max(0, lineWidth - left);
        return "╭" + "─".repeat(left) + caption + "─".repeat(right) + "╮";
    }

    private static String formatMb(long bytes) {
        if (bytes < 0) {
            return "-";
        }
        return String.format(Locale.US, "%.2f MB", bytes / 1024.0 / 1024.0);
    }

    private static String formatRam(long usedBytes, long totalBytes) {
        if (usedBytes < 0 || totalBytes <= 0) {
            return "-";
        }
        return String.format(Locale.US, "%.2f GB / %.2f GB",
                usedBytes / 1024.0 / 1024.0 / 1024.0,
                totalBytes / 1024.0 / 1024.0 / 1024.0);
    }

    private static String formatPercent(double load) {
        if (load < 0) {
            return "-";
        }
        return String.format(Locale.US, "%%%.1f", load * 100.0);
    }

    public record Snapshot(
            long appMemoryBytes,
            long playerMemoryBytes,
            long totalProcessMemoryBytes,
            String osName,
            String javaVersion,
            double cpuLoad,
            long usedMemoryBytes,
            long totalMemoryBytes) {
    }
}
