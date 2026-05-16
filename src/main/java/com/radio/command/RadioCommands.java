package com.radio.command;

import com.radio.model.RadioStation;
import com.radio.player.AudioPlayer;
import com.radio.service.NotificationService;
import com.radio.service.RadioBrowserService;
import com.radio.service.SettingsService;
import com.radio.service.StatisticsService;
import com.radio.service.StationService;
import com.radio.util.Theme;
import com.radio.util.ThemeManager;
import com.radio.util.UIUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class RadioCommands {

    private final StationService stationService;
    private final AudioPlayer player;
    private final ThemeManager themeManager;
    private final SettingsService settingsService;
    private final StatisticsService statisticsService;
    private final NotificationService notificationService;
    private final RadioBrowserService radioBrowserService;

    private List<RadioStation> navigationList = List.of();
    private List<RadioBrowserService.OnlineStation> lastOnlineSearch = List.of();

    @Autowired
    public RadioCommands(StationService stationService, AudioPlayer player, ThemeManager themeManager,
                         SettingsService settingsService, StatisticsService statisticsService,
                         NotificationService notificationService, RadioBrowserService radioBrowserService) {
        this.stationService = stationService;
        this.player = player;
        this.themeManager = themeManager;
        this.settingsService = settingsService;
        this.statisticsService = statisticsService;
        this.notificationService = notificationService;
        this.radioBrowserService = radioBrowserService;
    }

    public RadioCommands(StationService stationService, AudioPlayer player, ThemeManager themeManager) {
        this(stationService, player, themeManager, null, null, null, null);
    }

    public RadioCommands(StationService stationService, AudioPlayer player, ThemeManager themeManager, SettingsService settingsService) {
        this(stationService, player, themeManager, settingsService, null, null, null);
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
            sb.append("  ♫ Yayın başladı. Durdurmak için 'dur' yazın.\n");
            appendCurrentSongInfo(sb, false);
        } else {
            sb.append("  ✗ Bağlantı kurulamadı. Akış adresi erişilebilir olmayabilir.\n");
        }
        return sb.toString();
    }

    @Command(name = "son", description = "Son çalınan istasyonu çalar", group = "Radio")
    public String playLast() {
        if (settingsService == null || settingsService.getLastStationId() == null || settingsService.getLastStationId().isBlank()) {
            return "  ⚠ Henüz kayıtlı son istasyon yok. Önce 'cal -i <id>' ile bir istasyon çalın.";
        }
        return play(settingsService.getLastStationId());
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
        var sb = new StringBuilder();
        sb.append("\n").append(UIUtils.getBoxedString(new String[]{"ŞU AN ÇALIYOR"}, 38)).append("\n");
        sb.append("  %s %s\n".formatted(UIUtils.playbackAnimationFrame(), station.name()));
        appendCurrentSongInfo(sb, true);
        sb.append("  ► Ülke: %s\n".formatted(station.country()));
        sb.append("  ► Tür:  %s\n".formatted(station.genre()));
        sb.append("  ► Ses:  %%%d\n".formatted(player.getVolume()));
        sb.append("  ► ID:   %s\n".formatted(station.id()));
        if (player.isRecording()) {
            sb.append("  ⏺ Kayıt: %s\n".formatted(player.getRecordFile().getFileName()));
        }
        if (player.isSleepTimerActive()) {
            sb.append("  ◷ Uyku: %s dakika kaldı\n".formatted(formatMinutes(player.getSleepRemaining())));
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
            sb.append("  ♫ Yayın başladı. 'sonraki' ile karışık sırada devam edin.\n");
            appendCurrentSongInfo(sb, false);
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
            sb.append("  ♫ Yayın başladı. 'sonraki' veya 'onceki' ile geçiş yapabilirsiniz.\n");
            appendCurrentSongInfo(sb, false);
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

    @Command(name = "uyku", description = "Uyku zamanlayıcısını gösterir veya başlatır", group = "Oynatma")
    public String sleepTimer(
            @Option(longName = "dakika", shortName = 'd', required = false, description = "Kaç dakika sonra duracağı") Integer minutes) {
        if (minutes == null) {
            if (!player.isSleepTimerActive()) {
                return "  ◷ Uyku zamanlayıcısı aktif değil. Kullanım: uyku -d <dakika>";
            }
            return "  ◷ Uyku zamanlayıcısı aktif: %s dakika kaldı. İptal için: uyku iptal"
                    .formatted(formatMinutes(player.getSleepRemaining()));
        }
        if (minutes <= 0) {
            return "  ⚠ Dakika değeri 1 veya daha büyük olmalıdır.";
        }

        player.scheduleSleep(Duration.ofMinutes(minutes));
        var endsAt = player.getSleepEndsAt();
        String endTime = endsAt != null ? endsAt.format(DateTimeFormatter.ofPattern("HH:mm")) : "";
        return "  ◷ Uyku zamanlayıcısı ayarlandı: %d dakika sonra duracak. Bitiş: %s"
                .formatted(minutes, endTime);
    }

    @Command(name = "uyku iptal", description = "Uyku zamanlayıcısını iptal eder", group = "Oynatma")
    public String cancelSleepTimer() {
        return player.cancelSleepTimer()
                ? "  ◷ Uyku zamanlayıcısı iptal edildi."
                : "  ⚠ Aktif uyku zamanlayıcısı yok.";
    }

    @Command(name = "gecmis", description = "Son görülen şarkı bilgilerini listeler", group = "Oynatma")
    public String songHistory(
            @Option(longName = "adet", shortName = 'n', required = false, description = "Gösterilecek kayıt sayısı", defaultValue = "10") int limit) {
        if (limit <= 0) {
            return "  ⚠ Kayıt sayısı 1 veya daha büyük olmalıdır.";
        }

        var history = player.getSongHistory();
        if (history.isEmpty()) {
            return "  ⚠ Henüz şarkı bilgisi alınmadı. Bazı yayınlar metadata göndermeyebilir.";
        }

        var sb = new StringBuilder();
        sb.append("\n").append(UIUtils.getBoxedString(new String[]{"ŞARKI GEÇMİŞİ"}, 44)).append("\n");
        sb.append("  ┌──────────┬─────────────────────────┬──────────────────────────────┐\n");
        sb.append("  │ Saat     │ İstasyon                │ Parça                        │\n");
        sb.append("  ├──────────┼─────────────────────────┼──────────────────────────────┤\n");

        int count = Math.min(limit, history.size());
        var timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss");
        for (int i = 0; i < count; i++) {
            var entry = history.get(i);
            sb.append("  │ %s │ %s │ %s │\n".formatted(
                    UIUtils.padRight(entry.playedAt().format(timeFormat), 8),
                    UIUtils.padRight(UIUtils.truncate(entry.stationName(), 23), 23),
                    UIUtils.padRight(UIUtils.truncate(entry.title(), 28), 28)));
        }
        sb.append("  └──────────┴─────────────────────────┴──────────────────────────────┘\n");
        return sb.toString();
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
        String urlError = validateStreamUrl(url);
        if (urlError != null) {
            return urlError;
        }
        var station = new RadioStation(id, name, country, genre, url, false);
        stationService.addCustomStation(station);
        return "  ✓ '%s' istasyonu eklendi! 'cal -i %s' ile dinleyebilirsiniz.".formatted(name, id);
    }

    @Command(name = "duzenle", description = "Özel istasyonu düzenler", group = "Yönetim")
    public String editStation(
            @Option(longName = "id", required = true, description = "İstasyon ID") String id,
            @Option(longName = "isim", shortName = 'n', required = false, description = "Yeni istasyon adı") String name,
            @Option(longName = "ulke", shortName = 'u', required = false, description = "Yeni ülke") String country,
            @Option(longName = "tur", shortName = 't', required = false, description = "Yeni tür") String genre,
            @Option(longName = "url", required = false, description = "Yeni akış URL'si") String url) {
        if (stationService.findStation(id).isEmpty()) {
            return "  ⚠ '%s' istasyonu bulunamadı.".formatted(id);
        }
        if (stationService.isBuiltInStation(id)) {
            return "  ⚠ Dahili istasyonlar düzenlenemez. Sadece özel istasyonları düzenleyebilirsiniz.";
        }
        if (isBlank(name) && isBlank(country) && isBlank(genre) && isBlank(url)) {
            return "  ⚠ En az bir alan verin. Örnek: duzenle --id %s --url https://...".formatted(id);
        }
        if (!isBlank(url)) {
            String urlError = validateStreamUrl(url);
            if (urlError != null) {
                return urlError;
            }
        }

        var updated = stationService.updateCustomStation(id, name, country, genre, url);
        if (updated.isEmpty()) {
            return "  ⚠ '%s' istasyonu düzenlenemedi.".formatted(id);
        }
        var station = updated.get();
        return "  ✓ '%s' güncellendi.\n  ► Ülke: %s\n  ► Tür: %s\n  ► URL: %s"
                .formatted(station.name(), station.country(), station.genre(), station.url());
    }

    @Command(name = "iceaktar", description = "M3U/PLS playlist dosyasından özel istasyon ekler", group = "Yönetim")
    public String importPlaylist(
            @Option(longName = "dosya", shortName = 'd', required = true, description = "M3U veya PLS dosya yolu") String file,
            @Option(longName = "ulke", shortName = 'u', required = false, description = "İçe aktarılan istasyon ülkesi", defaultValue = "Özel") String country,
            @Option(longName = "tur", shortName = 't', required = false, description = "İçe aktarılan istasyon türü", defaultValue = "Karma") String genre,
            @Option(longName = "oneki", shortName = 'p', required = false, description = "Üretilecek ID ön eki", defaultValue = "import") String prefix) {
        Path playlist = resolvePath(file);
        if (!Files.exists(playlist)) {
            return "  ⚠ Dosya bulunamadı: %s".formatted(playlist);
        }
        if (!Files.isRegularFile(playlist)) {
            return "  ⚠ Playlist yolu normal bir dosya olmalıdır: %s".formatted(playlist);
        }

        try {
            var result = stationService.importPlaylist(playlist, country, genre, prefix);
            if (result.importedCount() == 0) {
                return "  ⚠ İçe aktarılacak geçerli http/https akış bulunamadı. Atlanan: %d"
                        .formatted(result.skippedCount());
            }
            navigationList = result.importedStations();
            return "  ✓ Playlist içe aktarıldı: %d istasyon eklendi, %d atlandı.\n%s"
                    .formatted(result.importedCount(), result.skippedCount(), formatStationTable(result.importedStations()));
        } catch (IOException e) {
            return "  ✗ Playlist okunamadı: " + e.getMessage();
        }
    }

    @Command(name = "istatistik", description = "Dinleme istatistiklerini gösterir", group = "İstatistik")
    public String statistics(
            @Option(longName = "adet", shortName = 'n', required = false, description = "Gösterilecek istasyon sayısı", defaultValue = "10") int limit) {
        if (statisticsService == null) {
            return "  ⚠ İstatistik servisi kullanılamıyor.";
        }
        var total = statisticsService.getTotalListenTime();
        int sessions = statisticsService.getTotalSessions();
        var top = new ArrayList<>(statisticsService.getTopStations(limit));

        var live = player.getLiveSession();
        if (live != null) {
            total = total.plus(live.duration());
            // Live session counts as an in-progress session in the totals.
            sessions += 1;
            mergeLiveIntoTop(top, live, limit);
        }

        var sb = new StringBuilder();
        sb.append("\n").append(UIUtils.getBoxedString(new String[]{"DİNLEME İSTATİSTİKLERİ"}, 50)).append("\n");
        sb.append("  Toplam: %s | Oturum: %d\n".formatted(formatListenDuration(total), sessions));
        if (live != null) {
            sb.append("  ● Canlı: %s (%s)\n".formatted(live.station().name(), formatListenDuration(live.duration())));
        }
        sb.append("\n");

        if (top.isEmpty()) {
            sb.append("  ⚠ Henüz kayıtlı dinleme geçmişi yok. Bir istasyon çalın.\n");
            return sb.toString();
        }

        sb.append("  ┌────┬─────────────────────────┬──────────────┬──────────────────┬──────────┐\n");
        sb.append("  │ #  │ İstasyon                │ Ülke         │ Tür              │ Süre     │\n");
        sb.append("  ├────┼─────────────────────────┼──────────────┼──────────────────┼──────────┤\n");

        int idx = 1;
        String liveId = live != null ? live.station().id() : null;
        for (var stat : top) {
            boolean isLive = liveId != null && liveId.equals(stat.stationId());
            String marker = isLive ? "●" : " ";
            sb.append("  │%s%s │ %s │ %s │ %s │ %s │\n".formatted(
                    marker,
                    UIUtils.padRight(String.valueOf(idx++), 2),
                    UIUtils.padRight(UIUtils.truncate(stat.stationName(), 23), 23),
                    UIUtils.padRight(UIUtils.truncate(stat.country(), 12), 12),
                    UIUtils.padRight(UIUtils.truncate(stat.genre(), 16), 16),
                    UIUtils.padRight(formatListenDuration(Duration.ofSeconds(stat.totalSeconds())), 8)));
        }
        sb.append("  └────┴─────────────────────────┴──────────────┴──────────────────┴──────────┘\n");
        return sb.toString();
    }

    private void mergeLiveIntoTop(List<StatisticsService.StationStat> top, AudioPlayer.LiveSession live, int limit) {
        long liveSeconds = live.duration().toSeconds();
        var station = live.station();
        StatisticsService.StationStat existing = null;
        int existingIdx = -1;
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).stationId().equals(station.id())) {
                existing = top.get(i);
                existingIdx = i;
                break;
            }
        }
        StatisticsService.StationStat merged = existing != null
                ? new StatisticsService.StationStat(existing.stationId(), existing.stationName(),
                        existing.country(), existing.genre(),
                        existing.totalSeconds() + liveSeconds, existing.sessionCount() + 1)
                : new StatisticsService.StationStat(station.id(), station.name(),
                        station.country(), station.genre(), liveSeconds, 1);
        if (existingIdx >= 0) {
            top.set(existingIdx, merged);
        } else {
            top.add(merged);
        }
        top.sort((a, b) -> Long.compare(b.totalSeconds(), a.totalSeconds()));
        while (top.size() > limit) {
            top.remove(top.size() - 1);
        }
    }

    @Command(name = "bildirim", description = "Masaüstü bildirimlerini açar/kapatır", group = "Yönetim")
    public String notification() {
        if (notificationService == null) {
            return "  ⚠ Bildirim servisi kullanılamıyor.";
        }
        boolean newState = !notificationService.isEnabled();
        notificationService.setEnabled(newState);
        return newState
                ? "  Bildirimler acildi. Sarki degisince masaustu bildirimi gosterilecek."
                : "  Bildirimler kapatildi.";
    }

    @Command(name = "online-ara", description = "RadioBrowser.info üzerinden istasyon arar", group = "Online")
    public String onlineSearch(
            @Option(longName = "sorgu", shortName = 's', required = false, description = "İstasyon adı") String query,
            @Option(longName = "ulke", shortName = 'u', required = false, description = "Ülke filtresi") String country,
            @Option(longName = "tur", shortName = 't', required = false, description = "Tür/etiket filtresi") String tag,
            @Option(longName = "adet", shortName = 'n', required = false, description = "Sonuç sayısı", defaultValue = "15") int limit) {

        if (isBlank(query) && isBlank(country) && isBlank(tag)) {
            return "  ⚠ En az bir kriter girin. Örnek: online-ara -s jazz   ya da   online-ara -t pop -u Turkey";
        }

        var results = radioBrowserService.search(query, country, tag, limit);
        if (results.isEmpty()) {
            return "  ⚠ Sonuç bulunamadı. Farklı arama kriterleri deneyin.";
        }

        lastOnlineSearch = results;

        var sb = new StringBuilder();
        sb.append("\n  Kaynak: RadioBrowser.info — %d istasyon bulundu:\n".formatted(results.size()));
        sb.append("  ┌────┬─────────────────────────┬──────────────┬──────────────┬──────┬────────┐\n");
        sb.append("  │ #  │ İstasyon                │ Ülke         │ Tür          │ Kbps │ Oylar  │\n");
        sb.append("  ├────┼─────────────────────────┼──────────────┼──────────────┼──────┼────────┤\n");

        for (int i = 0; i < results.size(); i++) {
            var s = results.get(i);
            sb.append("  │ %s │ %s │ %s │ %s │ %s │ %s │\n".formatted(
                    UIUtils.padRight(String.valueOf(i + 1), 2),
                    UIUtils.padRight(UIUtils.truncate(s.name(), 23), 23),
                    UIUtils.padRight(UIUtils.truncate(s.countryDisplay(), 12), 12),
                    UIUtils.padRight(UIUtils.truncate(s.genreDisplay(), 12), 12),
                    UIUtils.padRight(s.bitrate() > 0 ? String.valueOf(s.bitrate()) : "-", 4),
                    UIUtils.padRight(String.valueOf(s.votes()), 6)));
        }
        sb.append("  └────┴─────────────────────────┴──────────────┴──────────────┴──────┴────────┘\n");
        sb.append("  Eklemek için: online-ekle --numara <#>\n");
        return sb.toString();
    }

    @Command(name = "online-ekle", description = "Online arama sonucundan istasyon ekler", group = "Online")
    public String onlineAdd(
            @Option(longName = "numara", shortName = 'n', required = true, description = "online-ara sonucundaki sıra numarası") int number) {

        if (lastOnlineSearch.isEmpty()) {
            return "  ⚠ Önce 'online-ara' komutu ile arama yapın.";
        }
        if (number < 1 || number > lastOnlineSearch.size()) {
            return "  ⚠ Geçersiz numara. 1-%d arasında bir değer girin.".formatted(lastOnlineSearch.size());
        }

        var s = lastOnlineSearch.get(number - 1);
        if (isBlank(s.url())) {
            return "  ⚠ Bu istasyonun geçerli bir akış URL'si yok.";
        }

        String baseId = onlineSlug(s.name());
        String id = baseId;
        int suffix = 2;
        while (stationService.findStation(id).isPresent()) {
            id = baseId + "-" + suffix++;
        }
        String genre = s.genreDisplay();
        String country = s.countryDisplay();
        var station = new RadioStation(id, s.name(), country, genre, s.url(), false);
        stationService.addCustomStation(station);
        return "  ✓ '%s' eklendi. Çalmak için: cal -i %s".formatted(s.name(), id);
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

    private void appendCurrentSongInfo(StringBuilder sb, boolean showPendingMessage) {
        var songInfo = player.getCurrentSongInfo();
        if (songInfo != null && songInfo.rawTitle() != null && !songInfo.rawTitle().isBlank()) {
            if (songInfo.artist() != null && !songInfo.artist().isBlank()) {
                sb.append("  ► Sanatçı: %s\n".formatted(songInfo.artist()));
                sb.append("  ► Şarkı: %s\n".formatted(songInfo.title()));
            } else {
                sb.append("  ► Şarkı: %s\n".formatted(songInfo.rawTitle()));
            }
            return;
        }

        var rawTitle = player.getCurrentSongTitle();
        if (rawTitle != null && !rawTitle.isBlank()) {
            sb.append("  ► Şarkı: %s\n".formatted(rawTitle));
            return;
        }

        if (showPendingMessage) {
            if (player.shouldShowPendingSongInfo(Duration.ofSeconds(15))) {
                sb.append("  ► Şarkı: Bilgi bekleniyor veya yayın desteklemiyor\n");
            }
        } else {
            sb.append("  ℹ Şarkı bilgisi geldiğinde 'durum' ve promptta görünür.\n");
        }
    }

    private String validateStreamUrl(String url) {
        try {
            var uri = URI.create(url);
            if (uri.getScheme() == null
                    || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
                return "  ⚠ Geçersiz URL: http:// veya https:// ile başlamalıdır.";
            }
        } catch (IllegalArgumentException e) {
            return "  ⚠ Geçersiz URL formatı: " + e.getMessage();
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private long formatMinutes(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        return Math.max(1, (seconds + 59) / 60);
    }

    private Path resolvePath(String file) {
        if (file.startsWith("~/")) {
            return Path.of(System.getProperty("user.home"), file.substring(2));
        }
        if (file.equals("~")) {
            return Path.of(System.getProperty("user.home"));
        }
        return Path.of(file);
    }

    private String formatListenDuration(Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        long s = d.toSecondsPart();
        if (h > 0) return "%dsa %02ddk".formatted(h, m);
        if (m > 0) return "%ddk %02dsn".formatted(m, s);
        return "%dsn".formatted(s);
    }

    private String onlineSlug(String name) {
        if (name == null || name.isBlank()) return "online-station";
        String normalized = name.toLowerCase(Locale.forLanguageTag("tr"))
                .replace("ı", "i").replace("ğ", "g").replace("ü", "u")
                .replace("ş", "s").replace("ö", "o").replace("ç", "c");
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String slug = normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? "online-station" : slug;
    }
}
