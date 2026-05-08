package com.radio.command;

import com.radio.model.RadioStation;
import com.radio.player.AudioPlayer;
import com.radio.service.StationService;
import com.radio.util.Theme;
import com.radio.util.ThemeManager;
import com.radio.util.UIUtils;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class RadioCommands {

    private final StationService stationService;
    private final AudioPlayer player;
    private final ThemeManager themeManager;

    // Navigation context: last displayed station list for sonraki/onceki commands
    private List<RadioStation> navigationList = List.of();

    public RadioCommands(StationService stationService, AudioPlayer player, ThemeManager themeManager) {
        this.stationService = stationService;
        this.player = player;
        this.themeManager = themeManager;
    }

    @Command(name = "listele", description = "Tüm radyo istasyonlarını listeler", group = "Radio")
    public String listAll() {
        var stations = stationService.getAllStations();
        navigationList = stations;
        return formatStationTable(stations);
    }

    @Command(name = "turkiye", description = "Türkiye radyo istasyonlarını listeler", group = "Radio")
    public String turkiye() {
        var stations = stationService.getTurkishStations();
        navigationList = stations;
        return formatStationTable(stations);
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
        navigationList = stations;
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
        navigationList = stations;
        return formatStationTable(stations);
    }

    @Command(name = "ara", description = "İstasyon arar (isim, ülke veya tür)", group = "Radio")
    public String search(
            @Option(longName = "sorgu", shortName = 's', required = true, description = "Arama sorgusu") String query) {
        var stations = stationService.searchStations(query);
        if (stations.isEmpty()) {
            return "  ⚠ '%s' için sonuç bulunamadı.".formatted(query);
        }
        navigationList = stations;
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
        if (player.isAutoReconnecting()) {
            var station = player.getCurrentStation();
            return "  ↻ Yeniden bağlanılıyor: %s — lütfen bekleyin...".formatted(
                    station != null ? station.name() : "");
        }
        if (!player.isPlaying()) {
            return "  ⏸ Şu an çalan bir istasyon yok.";
        }
        var station = player.getCurrentStation();
        var song = player.getCurrentSongTitle();
        var sb = new StringBuilder();
        sb.append("\n").append(UIUtils.getBoxedString(new String[]{"ŞU AN ÇALIYOR"}, 38)).append("\n");
        sb.append("  ♬ %s\n".formatted(station.name()));
        if (song != null && !song.isBlank()) {
            sb.append("  ❯❯ %s\n".formatted(song));
        }
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
        if (level < 0 || level > 100) {
            return "  ⚠ Ses seviyesi 0-100 arasında olmalıdır (girilen: %d).".formatted(level);
        }
        player.setVolume(level);
        String bar = "█".repeat(player.getVolume() / 5) + "░".repeat(20 - player.getVolume() / 5);
        String note = player.isVolumeChangePending() ? "\n  ℹ Çalmakta olan yayında bir sonraki 'cal' komutunda geçerli olur." : "";
        return ("  🔊 Ses: %%%d [%s]" + note).formatted(player.getVolume(), bar);
    }

    @Command(name = "sonraki", description = "Listedeki bir sonraki istasyona geçer", group = "Radio")
    public String next() {
        return navigate(1);
    }

    @Command(name = "onceki", description = "Listedeki bir önceki istasyona geçer", group = "Radio")
    public String previous() {
        return navigate(-1);
    }

    @Command(name = "karistir", description = "Rastgele bir istasyon çalar (opsiyonel: ülke veya tür filtresi)", group = "Radio")
    public String shuffle(
            @Option(longName = "ulke", shortName = 'u', required = false, description = "Ülke filtresi") String country,
            @Option(longName = "tur", shortName = 't', required = false, description = "Tür filtresi") String genre,
            @Option(longName = "favori", shortName = 'f', required = false, description = "Sadece favorilerden", defaultValue = "false") boolean favoritesOnly) {

        List<RadioStation> pool;
        String source;

        if (favoritesOnly) {
            pool = stationService.getFavorites();
            source = "favoriler";
        } else if (country != null && !country.isBlank()) {
            pool = stationService.getStationsByCountry(country);
            source = country;
        } else if (genre != null && !genre.isBlank()) {
            pool = stationService.getStationsByGenre(genre);
            source = genre;
        } else {
            pool = stationService.getAllStations();
            source = "tüm istasyonlar";
        }

        if (pool.isEmpty()) {
            return "  ⚠ '%s' için istasyon bulunamadı.".formatted(source);
        }

        // Shuffle and set as navigation list
        var shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        navigationList = shuffled;

        var station = shuffled.getFirst();
        var sb = new StringBuilder();
        sb.append("\n  🎲 Rastgele seçim (%s - %d istasyon arasından)\n".formatted(source, pool.size()));
        sb.append("  ♬ Bağlanıyor: %s (%s)\n".formatted(station.name(), station.country()));
        sb.append("  ► Tür: %s\n".formatted(station.genre()));
        sb.append("  ► Ses: %%%d\n".formatted(player.getVolume()));

        PrintWriter progressOut = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        boolean success = player.play(station, progressOut);
        if (success) {
            sb.append("  ✓ Çalınıyor! 'sonraki' ile karışık sırada devam edin.\n");
        } else {
            sb.append("  ✗ Bağlantı kurulamadı. 'karistir' ile tekrar deneyin.\n");
        }
        return sb.toString();
    }

    private String navigate(int direction) {
        if (!player.isPlaying()) {
            return "  ⚠ Şu an çalan bir istasyon yok. Önce 'cal -i <id>' ile bir istasyon çalın.";
        }

        if (navigationList.isEmpty()) {
            navigationList = stationService.getAllStations();
        }

        var current = player.getCurrentStation();
        if (current == null) {
            return "  ⚠ Şu an çalan bir istasyon yok. Önce 'cal -i <id>' ile bir istasyon çalın.";
        }

        int currentIndex = -1;
        for (int i = 0; i < navigationList.size(); i++) {
            if (navigationList.get(i).id().equals(current.id())) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            navigationList = stationService.getAllStations();
            for (int i = 0; i < navigationList.size(); i++) {
                if (navigationList.get(i).id().equals(current.id())) {
                    currentIndex = i;
                    break;
                }
            }
        }

        int nextIndex = (currentIndex + direction + navigationList.size()) % navigationList.size();
        var nextStation = navigationList.get(nextIndex);

        var sb = new StringBuilder();
        sb.append("\n  %s %s (%d/%d)\n".formatted(
                direction > 0 ? "⏭" : "⏮",
                direction > 0 ? "Sonraki istasyon" : "Önceki istasyon",
                nextIndex + 1, navigationList.size()));
        sb.append("  ♬ Bağlanıyor: %s (%s)\n".formatted(nextStation.name(), nextStation.country()));
        sb.append("  ► Tür: %s\n".formatted(nextStation.genre()));
        sb.append("  ► Ses: %%%d\n".formatted(player.getVolume()));

        PrintWriter progressOut = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        boolean success = player.play(nextStation, progressOut);
        if (success) {
            sb.append("  ✓ Çalınıyor! 'sonraki' veya 'onceki' ile geçiş yapabilirsiniz.\n");
        } else {
            sb.append("  ✗ Bağlantı kurulamadı. Bir sonraki istasyonu deneyin.\n");
        }
        return sb.toString();
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
        navigationList = favs;
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

    @Command(name = "tema", description = "Renk temasını değiştirir", group = "Yönetim")
    public String theme(
            @Option(longName = "isim", shortName = 'i', required = false, description = "Tema adı") String name) {

        if (name == null || name.isBlank()) {
            var sb = new StringBuilder();
            sb.append("\n").append(UIUtils.getBoxedString(new String[]{"RENK TEMALARI"}, 44)).append("\n");
            var current = themeManager.getCurrent();
            for (var theme : Theme.all().values()) {
                String active = theme.name().equals(current.name()) ? " ◄ aktif" : "";
                String preview = theme.primary() + "██" + theme.secondary() + "██" + theme.accent() + "██" + theme.reset();
                sb.append("  %s %-14s %s%s\n".formatted(preview, theme.name(), theme.description(), active));
            }
            sb.append("\n  Kullanım: tema -i <tema-adı>\n");
            return sb.toString();
        }

        if (themeManager.setTheme(name)) {
            var t = themeManager.getCurrent();
            String preview = t.primary() + "██" + t.secondary() + "██" + t.accent() + "██" + t.reset();
            return "  ✓ Tema değiştirildi: %s %s — %s".formatted(preview, t.name(), t.description());
        }
        return "  ⚠ '%s' teması bulunamadı. 'tema' yazarak mevcut temaları görün.".formatted(name);
    }

    @Command(name = "kontrol", description = "İstasyonların akış URL'lerini kontrol eder", group = "Yönetim")
    public String healthCheck(
            @Option(longName = "istasyon", shortName = 'i', required = false, description = "Belirli istasyon ID (boş bırakılırsa tümü kontrol edilir)") String stationId) {

        List<RadioStation> toCheck;
        if (stationId != null && !stationId.isBlank()) {
            var opt = stationService.findStation(stationId);
            if (opt.isEmpty()) {
                return "  ⚠ '%s' istasyonu bulunamadı.".formatted(stationId);
            }
            toCheck = List.of(opt.get());
        } else {
            toCheck = stationService.getAllStations();
        }

        var sb = new StringBuilder();
        sb.append("\n").append(UIUtils.getBoxedString(new String[]{"İSTASYON SAĞLIK KONTROLÜ"}, 44)).append("\n");
        sb.append("  %d istasyon kontrol ediliyor...\n\n".formatted(toCheck.size()));

        PrintWriter progressOut = new PrintWriter(System.out, true, StandardCharsets.UTF_8);

        record CheckResult(RadioStation station, boolean alive, int statusCode, String error) {}

        var results = new ConcurrentLinkedQueue<CheckResult>();

        try (var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {

            var futures = toCheck.stream()
                    .map(station -> {
                        HttpRequest request;
                        try {
                            request = HttpRequest.newBuilder()
                                    .uri(URI.create(station.url()))
                                    .timeout(Duration.ofSeconds(8))
                                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                                    .build();
                        } catch (IllegalArgumentException e) {
                            results.add(new CheckResult(station, false, 0, "Geçersiz URL"));
                            return CompletableFuture.completedFuture((Void) null);
                        }

                        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                                .thenAccept(resp -> results.add(new CheckResult(station, resp.statusCode() < 400, resp.statusCode(), null)))
                                .exceptionally(ex -> {
                                    // HEAD may be rejected, try GET
                                    HttpRequest getReq = HttpRequest.newBuilder()
                                            .uri(URI.create(station.url()))
                                            .timeout(Duration.ofSeconds(8))
                                            .GET()
                                            .build();
                                    try {
                                        var resp = client.send(getReq, HttpResponse.BodyHandlers.discarding());
                                        results.add(new CheckResult(station, resp.statusCode() < 400, resp.statusCode(), null));
                                    } catch (Exception e2) {
                                        results.add(new CheckResult(station, false, 0, "Bağlantı hatası"));
                                    }
                                    return null;
                                });
                    })
                    .toList();

            // Show progress
            int total = futures.size();
            for (int i = 0; i < total; i++) {
                futures.get(i).join();
                int done = i + 1;
                int pct = done * 100 / total;
                int filled = pct / 5;
                String bar = "█".repeat(filled) + "░".repeat(20 - filled);
                progressOut.print("\r  [%s] %%%d (%d/%d)".formatted(bar, pct, done, total));
                progressOut.flush();
            }
            progressOut.print("\r" + " ".repeat(50) + "\r");
            progressOut.flush();
        }

        // Sort results: alive first, then by name
        var sorted = results.stream()
                .sorted((a, b) -> {
                    if (a.alive() != b.alive()) return a.alive() ? -1 : 1;
                    return a.station().name().compareTo(b.station().name());
                })
                .toList();

        int alive = (int) sorted.stream().filter(CheckResult::alive).count();
        int dead = sorted.size() - alive;

        sb.append("  ┌─────────────────────────┬──────────────────────┬────────┐\n");
        sb.append("  │ İstasyon                │ ID                   │ Durum  │\n");
        sb.append("  ├─────────────────────────┼──────────────────────┼────────┤\n");

        for (var r : sorted) {
            String status = r.alive() ? "  ✓   " : "  ✗   ";
            sb.append("  │ %s │ %s │%s│\n".formatted(
                    UIUtils.padRight(UIUtils.truncate(r.station().name(), 23), 23),
                    UIUtils.padRight(UIUtils.truncate(r.station().id(), 20), 20),
                    status));
        }
        sb.append("  └─────────────────────────┴──────────────────────┴────────┘\n");
        sb.append("  ✓ Aktif: %d | ✗ Erişilemez: %d | Toplam: %d\n".formatted(alive, dead, sorted.size()));

        return sb.toString();
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
        try {
            var uri = URI.create(url);
            if (uri.getScheme() == null || (!uri.getScheme().equals("http") && !uri.getScheme().equals("https"))) {
                return "  ⚠ Geçersiz URL: http:// veya https:// ile başlamalıdır.";
            }
        } catch (IllegalArgumentException e) {
            return "  ⚠ Geçersiz URL formatı: " + e.getMessage();
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
