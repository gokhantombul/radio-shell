package com.radio.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemInfoServiceTest {

    private final SystemInfoService service = new SystemInfoService();

    @Test
    void formatSnapshot_returnsSystemInfoPanel() {
        var snapshot = new SystemInfoService.Snapshot(
                mb(54.52),
                mb(28.42),
                mb(54.52) + mb(28.42),
                "Darwin 23.2.0",
                "21.0.2",
                0.20,
                gb(7.24),
                gb(16.00));

        String result = service.formatSnapshot(snapshot);

        assertThat(result).contains("Sistem Bilgileri");
        assertThat(result).contains("Radyo Shell (Java):");
        assertThat(result).contains("ffplay (Motor):");
        assertThat(result).contains("Toplam Kullanım:");
        assertThat(result).contains("İşletim Sistemi:");
        assertThat(result).contains("Java Sürümü:");
        assertThat(result).contains("CPU Kullanımı:");
        assertThat(result).contains("Sistem RAM:");
        assertThat(result).contains("54.52 MB");
        assertThat(result).contains("28.42 MB");
        assertThat(result).contains("82.94 MB");
        assertThat(result).contains("Darwin 23.2.0");
        assertThat(result).contains("21.0.2");
        assertThat(result).contains("%20.0");
        assertThat(result).contains("7.24 GB / 16.00 GB");
        assertThat(result).contains("╭");
        assertThat(result).contains("╰");
    }

    private static long mb(double value) {
        return Math.round(value * 1024.0 * 1024.0);
    }

    private static long gb(double value) {
        return Math.round(value * 1024.0 * 1024.0 * 1024.0);
    }
}
