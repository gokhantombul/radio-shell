package com.radio.command;

import com.radio.model.RadioStation;
import com.radio.player.AudioPlayer;
import com.radio.service.StationService;
import com.radio.util.UIUtils;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@Component
public class RadioCommands {

    private final StationService stationService;
    private final AudioPlayer player;

    public RadioCommands(StationService stationService, AudioPlayer player) {
        this.stationService = stationService;
        this.player = player;
    }

    @Command(name = "listele", description = "Tüm radyo istasyonlarını listeler", group = "Radio")
    public String listAll() {
        return formatStationTable(stationService.getAllStations());
    }

    @Command(name = "turkiye", description = "Türkiye radyo istasyonlarını listeler", group = "Radio")
    public String turkiye() {
        return formatStationTable(stationService.getTurkishStations());
    }

    @Command(name = "ulkeler", description = "Mevcut ülkeleri listeler", group = "Radio")
    public String countries() {
        var countries = stationService.getCountries();
        var sb = new StringBuilder();
        sb.append("\n").append(UIUtils.getBoxedString(new String[]{"ÜLKE LİSTESİ"}, 34)).append("\n");
        for (var c : countries) {
            long count = stationService.getStationsByCountry(c).size();
            sb.append("  • %-20s (%d istasyon)\n".formatted(c, count));
        }
        return sb.toString();
    }

    @Command(name = "ulke", description = "Belirli bir ülkenin istasyonlarını listeler", group = "Radio")
    public String byCountry(
            @Option(longName = "isim", shortName = 'i', required = true, description = "Ülke adı") String country) {
        var stations = stationService.getStationsByCountry(country);
        if (stations.isEmpty()) {
            return "  ⚠ '%s' ülkesi için istasyon bulunamadı. 'ulkeler' komutu ile mevcut ülkeleri görün.".formatted(country);
        }
        return formatStationTable(stations);
    }

    @Command(name = "turler", description = "Mevcut müzik türlerini listeler", group = "Radio")
    public String genres() {
        var genres = stationService.getGenres();
        var sb = new StringBuilder();
        sb.append("\n").append(UIUtils.getBoxedString(new String[]{"MÜZİK TÜRLERİ"}, 34)).append("\n");
        for (var g : genres) {
            sb.append("  • %s\n".formatted(g));
        }
        return sb.toString();
    }

    @Command(name = "tur", description = "Belirli bir müzik türündeki istasyonları listeler", group = "Radio")
    public String byGenre(
            @Option(longName = "isim", shortName = 'i', required = true, description = "Tür adı") String genre) {
        var stations = stationService.getStationsByGenre(genre);
        if (stations.isEmpty()) {
            return "  ⚠ '%s' türü için istasyon bulunamadı.".formatted(genre);
        }
        return formatStationTable(stations);
    }

    @Command(name = "ara", description = "İstasyon arar (isim, ülke veya tür)", group = "Radio")
    public String search(
            @Option(longName = "sorgu", shortName = 's', required = true, description = "Arama sorgusu") String query) {
        var stations = stationService.searchStations(query);
        if (stations.isEmpty()) {
            return "  ⚠ '%s' için sonuç bulunamadı.".formatted(query);
        }
        return "  🔍 '%s' araması - %d sonuç:\n".formatted(query, stations.size()) + formatStationTable(stations);
    }

    @Command(name = "cal", description = "Bir radyo istasyonunu çalar", group = "Radio")
    public String play(
            @Option(longName = "istasyon", shortName = 'i', required = true, description = "İstasyon ID veya adı") String stationId) {
        var stationOpt = stationService.findStation(stationId);
        if (stationOpt.isEmpty()) {
            var results = stationService.searchStations(stationId);
            if (results.isEmpty()) {
                return "  ⚠ '%s' istasyonu bulunamadı. 'listele' komutu ile istasyonları görün.".formatted(stationId);
            }
            if (results.size() == 1) {
                stationOpt = java.util.Optional.of(results.getFirst());
            } else {
                return "  Birden fazla eşleşme bulundu, lütfen seçin:\n" + formatStationTable(results);
            }
        }

        var station = stationOpt.get();
        var sb = new StringBuilder();
        sb.append("\n  ♬ Bağlanıyor: %s (%s)\n".formatted(station.name(), station.country()));
        sb.append("  ► Tür: %s\n".formatted(station.genre()));
        sb.append("  ► Ses: %%%d\n".formatted(player.getVolume()));

        PrintWriter progressOut = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        boolean success = player.play(station, progressOut);
        if (success) {
            sb.append("  ✓ Çalınıyor! Durdurmak için 'dur' yazın.\n");
        } else {
            sb.append("  ✗ Bağlantı kurulamadı. Akış adresi erişilebilir olmayabilir.\n");
        }
        return sb.toString();
    }

    @Command(name = "dur", description = "Çalan radyoyu durdurur", group = "Radio")
    public String stop() {
        if (!player.isPlaying()) {
            return "  ⚠ Şu an çalan bir istasyon yok.";
        }
        var station = player.getCurrentStation();
        var sb = new StringBuilder();
        if (player.isRecording()) {
            Path file = player.stopRecording();
            sb.append("  ■ Kayıt durduruldu: %s\n".formatted(file.getFileName()));
        }
        player.stop();
        sb.append("  ■ Durduruldu: %s".formatted(station != null ? station.name() : ""));
        return sb.toString();
    }

    @Command(name = "durum", description = "Şu anki çalma durumunu gösterir", group = "Radio")
    public String status() {
        if (!player.isPlaying()) {
            return "  ⏸ Şu an çalan bir istasyon yok.";
        }
        var station = player.getCurrentStation();
        var sb = new StringBuilder();
        sb.append("\n").append(UIUtils.getBoxedString(new String[]{"ŞU AN ÇALIYOR"}, 38)).append("\n");
        sb.append("  ♬ %s\n".formatted(station.name()));
        sb.append("  ► Ülke: %s\n".formatted(station.country()));
        sb.append("  ► Tür:  %s\n".formatted(station.genre()));
        sb.append("  ► Ses:  %%%d\n".formatted(player.getVolume()));
        sb.append("  ► ID:   %s\n".formatted(station.id()));
        if (player.isRecording()) {
            sb.append("  ⏺ Kayıt: %s\n".formatted(player.getRecordFile().getFileName()));
        }
        return sb.toString();
    }

    @Command(name = "ses", description = "Ses seviyesini ayarlar (0-100)", group = "Radio")
    public String volume(
            @Option(longName = "seviye", shortName = 's', required = true, description = "Ses seviyesi 0-100") int level) {
        player.setVolume(level);
        String bar = "█".repeat(player.getVolume() / 5) + "░".repeat(20 - player.getVolume() / 5);
        return "  🔊 Ses: %%%d [%s]".formatted(player.getVolume(), bar);
    }

    @Command(name = "favori", description = "İstasyonu favorilere ekler/çıkarır", group = "Favoriler")
    public String favorite(
            @Option(longName = "istasyon", shortName = 'i', required = true, description = "İstasyon ID") String stationId) {
        var stationOpt = stationService.findStation(stationId);
        if (stationOpt.isEmpty()) {
            return "  ⚠ '%s' istasyonu bulunamadı.".formatted(stationId);
        }
        var station = stationOpt.get();
        stationService.toggleFavorite(station.id());
        boolean isFav = stationService.isFavorite(station.id());
        return isFav
                ? "  ★ '%s' favorilere eklendi.".formatted(station.name())
                : "  ☆ '%s' favorilerden çıkarıldı.".formatted(station.name());
    }

    @Command(name = "favoriler", description = "Favori istasyonları listeler", group = "Favoriler")
    public String favorites() {
        var favs = stationService.getFavorites();
        if (favs.isEmpty()) {
            return "  ⚠ Henüz favori istasyon eklenmemiş. 'favori -i <istasyon-id>' ile ekleyin.";
        }
        return "  ★ Favori İstasyonlar:\n" + formatStationTable(favs);
    }

    @Command(name = "kaydet", description = "Çalan yayını MP3 olarak kaydetmeye başlar", group = "Kayıt")
    public String startRecording() {
        if (!player.isPlaying()) {
            return "  ⚠ Kayıt için önce bir istasyon çalın. Örnek: cal -i tr-powerfm";
        }
        if (player.isRecording()) {
            return "  ⚠ Zaten kayıt yapılıyor: " + player.getRecordFile().getFileName();
        }
        try {
            Path file = player.startRecording();
            if (file == null) {
                return "  ✗ Kayıt başlatılamadı.";
            }
            var station = player.getCurrentStation();
            return "  ⏺ Kayıt başladı: %s\n  ► Dosya: %s\n  ► Durdurmak için 'kayitdur' yazın."
                    .formatted(station.name(), file);
        } catch (IOException e) {
            return "  ✗ Kayıt hatası: " + e.getMessage();
        }
    }

    @Command(name = "kayitdur", description = "Kaydı durdurur ve dosyayı kaydeder", group = "Kayıt")
    public String stopRecording() {
        if (!player.isRecording()) {
            return "  ⚠ Şu an aktif bir kayıt yok.";
        }
        Path file = player.stopRecording();
        return "  ■ Kayıt durduruldu.\n  ► Dosya: %s".formatted(file);
    }

    @Command(name = "ekle", description = "Yeni özel istasyon ekler", group = "Yönetim")
    public String addStation(
            @Option(longName = "id", required = true, description = "Benzersiz istasyon ID") String id,
            @Option(longName = "isim", shortName = 'n', required = true, description = "İstasyon adı") String name,
            @Option(longName = "ulke", shortName = 'u', required = true, description = "Ülke") String country,
            @Option(longName = "tur", shortName = 't', required = true, description = "Müzik türü") String genre,
            @Option(longName = "url", required = true, description = "Akış URL'si") String url) {
        if (stationService.findStation(id).isPresent()) {
            return "  ⚠ '%s' ID'si zaten mevcut.".formatted(id);
        }
        var station = new RadioStation(id, name, country, genre, url, false);
        stationService.addCustomStation(station);
        return "  ✓ '%s' istasyonu eklendi! 'cal -i %s' ile dinleyebilirsiniz.".formatted(name, id);
    }

    @Command(name = "sil", description = "Özel istasyonu siler", group = "Yönetim")
    public String removeStation(
            @Option(longName = "id", required = true, description = "İstasyon ID") String id) {
        if (stationService.removeCustomStation(id)) {
            return "  ✓ İstasyon silindi.";
        }
        return "  ⚠ '%s' istasyonu bulunamadı veya dahili istasyon silinemez.".formatted(id);
    }

    private String formatStationTable(List<RadioStation> stations) {
        if (stations.isEmpty()) {
            return "  İstasyon bulunamadı.";
        }

        var sb = new StringBuilder();
        sb.append("\n  ┌────────┬─────────────────────────┬──────────────┬────────────────────┬──────────────────────┐\n");
        sb.append("  │   #    │ İstasyon                │ Ülke         │ Tür                │ ID                   │\n");
        sb.append("  ├────────┼─────────────────────────┼──────────────┼────────────────────┼──────────────────────┤\n");

        int idx = 1;
        for (var s : stations) {
            String fav = stationService.isFavorite(s.id()) ? "★" : " ";
            String playing = (player.isPlaying() && player.getCurrentStation() != null
                    && player.getCurrentStation().id().equals(s.id())) ? "♬" : " ";

            // First column contains fav icon (1/2 visual width), playing icon (2 visual width), and idx (2 chars)
            // Visual width: fav(2?) + playing(2) + idx(2) = 6?
            // If fav is wide, it's 2. If playing is ♬ it's 2.
            // String.format("%2d", idx++) is 2.
            // Total visual width for the first column needs careful management.

            String col1 = fav + playing + String.format("%2d", idx++);
            sb.append("  │ %s │ %s │ %s │ %s │ %s │\n".formatted(
                    UIUtils.padRight(col1, 6),
                    UIUtils.padRight(UIUtils.truncate(s.name(), 23), 23),
                    UIUtils.padRight(UIUtils.truncate(s.country(), 12), 12),
                    UIUtils.padRight(UIUtils.truncate(s.genre(), 18), 18),
                    UIUtils.padRight(UIUtils.truncate(s.id(), 20), 20)));
        }
        sb.append("  └────────┴─────────────────────────┴──────────────┴────────────────────┴──────────────────────┘\n");
        sb.append("  Toplam: %d istasyon | Çalmak için: cal -i <ID>\n".formatted(stations.size()));
        return sb.toString();
    }
}
