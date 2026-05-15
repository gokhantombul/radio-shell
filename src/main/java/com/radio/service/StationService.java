package com.radio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.radio.config.RadioConfig;
import com.radio.model.RadioStation;
import com.radio.model.StationList;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StationService {

    private static final Logger log = LoggerFactory.getLogger(StationService.class);
    private static final Locale TR = Locale.forLanguageTag("tr");

    public record PlaylistImportResult(int importedCount, int skippedCount, List<RadioStation> importedStations) {}

    private record PlaylistEntry(String title, String url) {}

    private final RadioConfig config;
    private final ObjectMapper mapper;
    private final Map<String, RadioStation> stationMap = new ConcurrentHashMap<>();
    private final Set<String> favoriteIds = ConcurrentHashMap.newKeySet();
    private final Set<String> builtInIds = new HashSet<>();

    public StationService(RadioConfig config) {
        this.config = config;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void init() {
        loadBuiltInStations();
        loadCustomStations();
        loadFavorites();
    }

    private void loadBuiltInStations() {
        try {
            var resource = new ClassPathResource("stations.json");
            var list = mapper.readValue(resource.getInputStream(), StationList.class);
            list.stations().forEach(s -> {
                stationMap.put(s.id(), s);
                builtInIds.add(s.id());
            });
            log.info("{} dahili istasyon yüklendi", list.stations().size());
        } catch (IOException e) {
            log.error("Dahili istasyonlar yüklenemedi: {}", e.getMessage());
        }
    }

    private void loadCustomStations() {
        String path = config.getCustomStationsFile();
        if (path == null) return;
        File file = new File(path);
        if (!file.exists()) return;
        try {
            var list = mapper.readValue(file, StationList.class);
            list.stations().forEach(s -> stationMap.put(s.id(), s));
            log.info("{} özel istasyon yüklendi", list.stations().size());
        } catch (IOException e) {
            log.error("Özel istasyonlar yüklenemedi: {}", e.getMessage());
        }
    }

    private void loadFavorites() {
        String path = config.getFavoritesFile();
        if (path == null) return;
        File file = new File(path);
        if (!file.exists()) return;
        try {
            var ids = mapper.readValue(file, String[].class);
            favoriteIds.addAll(Arrays.asList(ids));
        } catch (IOException e) {
            log.error("Favoriler yüklenemedi: {}", e.getMessage());
        }
    }

    private void saveFavorites() {
        try {
            String path = config.getFavoritesFile();
            if (path == null) return;
            Path p = Path.of(path);
            Files.createDirectories(p.getParent());
            mapper.writeValue(p.toFile(), favoriteIds.toArray(String[]::new));
        } catch (IOException e) {
            log.error("Favoriler kaydedilemedi: {}", e.getMessage());
        }
    }

    public List<RadioStation> getAllStations() {
        return stationMap.values().stream()
                .sorted(Comparator.comparing(RadioStation::country)
                        .thenComparing(RadioStation::name))
                .map(s -> s.withFavorite(favoriteIds.contains(s.id())))
                .toList();
    }

    public List<RadioStation> getStationsByCountry(String country) {
        return getAllStations().stream()
                .filter(s -> s.country().equalsIgnoreCase(country))
                .toList();
    }

    public List<RadioStation> getTurkishStations() {
        return getStationsByCountry("Türkiye");
    }

    public List<RadioStation> searchStations(String query) {
        String q = query.toLowerCase(TR);
        return getAllStations().stream()
                .filter(s -> s.name().toLowerCase(TR).contains(q)
                        || s.country().toLowerCase(TR).contains(q)
                        || s.genre().toLowerCase(TR).contains(q)
                        || s.id().toLowerCase().contains(q))
                .toList();
    }

    public Optional<RadioStation> findStation(String idOrName) {
        // Try exact ID match first
        var station = stationMap.get(idOrName);
        if (station != null) return Optional.of(station.withFavorite(favoriteIds.contains(station.id())));

        // Try name match (case-insensitive)
        String lower = idOrName.toLowerCase(TR);
        return stationMap.values().stream()
                .filter(s -> s.name().toLowerCase(TR).equals(lower)
                        || s.id().toLowerCase().equals(lower))
                .findFirst()
                .map(s -> s.withFavorite(favoriteIds.contains(s.id())));
    }

    public List<RadioStation> getFavorites() {
        return getAllStations().stream()
                .filter(s -> favoriteIds.contains(s.id()))
                .toList();
    }

    public boolean toggleFavorite(String stationId) {
        if (!stationMap.containsKey(stationId)) return false;
        if (favoriteIds.contains(stationId)) {
            favoriteIds.remove(stationId);
        } else {
            favoriteIds.add(stationId);
        }
        saveFavorites();
        return true;
    }

    public boolean isFavorite(String stationId) {
        return favoriteIds.contains(stationId);
    }

    public List<String> getCountries() {
        return stationMap.values().stream()
                .map(RadioStation::country)
                .distinct()
                .sorted()
                .toList();
    }

    public List<String> getGenres() {
        return stationMap.values().stream()
                .map(RadioStation::genre)
                .distinct()
                .sorted()
                .toList();
    }

    public boolean addCustomStation(RadioStation station) {
        stationMap.put(station.id(), station);
        saveCustomStations();
        return true;
    }

    public boolean isBuiltInStation(String id) {
        return builtInIds.contains(id);
    }

    public Optional<RadioStation> updateCustomStation(String id, String name, String country, String genre, String url) {
        if (builtInIds.contains(id)) return Optional.empty();

        var existing = stationMap.get(id);
        if (existing == null) return Optional.empty();

        var updated = new RadioStation(
                existing.id(),
                valueOrCurrent(name, existing.name()),
                valueOrCurrent(country, existing.country()),
                valueOrCurrent(genre, existing.genre()),
                valueOrCurrent(url, existing.url()),
                favoriteIds.contains(existing.id()));

        stationMap.put(id, updated);
        saveCustomStations();
        return Optional.of(updated);
    }

    public boolean removeCustomStation(String id) {
        if (builtInIds.contains(id)) return false;
        var removed = stationMap.remove(id);
        if (removed != null) {
            favoriteIds.remove(id);
            saveCustomStations();
            saveFavorites();
            return true;
        }
        return false;
    }

    private void saveCustomStations() {
        try {
            String path = config.getCustomStationsFile();
            if (path == null) return;
            Path p = Path.of(path);
            Files.createDirectories(p.getParent());
            var customStations = stationMap.values().stream()
                    .filter(s -> !builtInIds.contains(s.id()))
                    .toList();
            mapper.writeValue(p.toFile(), new StationList(customStations));
        } catch (IOException e) {
            log.error("Özel istasyonlar kaydedilemedi: {}", e.getMessage());
        }
    }

    public List<RadioStation> getStationsByGenre(String genre) {
        String g = genre.toLowerCase(TR);
        return getAllStations().stream()
                .filter(s -> s.genre().toLowerCase(TR).contains(g))
                .toList();
    }

    public PlaylistImportResult importPlaylist(Path playlistFile, String country, String genre, String prefix) throws IOException {
        var entries = parsePlaylist(playlistFile);
        String defaultCountry = valueOrCurrent(country, "Özel");
        String defaultGenre = valueOrCurrent(genre, "Karma");
        String idPrefix = slug(valueOrCurrent(prefix, "import"));
        if (idPrefix.isBlank()) {
            idPrefix = "import";
        }

        var imported = new ArrayList<RadioStation>();
        int skipped = 0;

        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (!isHttpUrl(entry.url())) {
                skipped++;
                continue;
            }

            String fallbackName = fileStem(playlistFile) + " " + (i + 1);
            String name = valueOrCurrent(entry.title(), fallbackName);
            String id = uniqueStationId(idPrefix + "-" + name);
            var station = new RadioStation(id, name, defaultCountry, defaultGenre, entry.url(), false);

            stationMap.put(station.id(), station);
            imported.add(station);
        }

        if (!imported.isEmpty()) {
            saveCustomStations();
        }

        return new PlaylistImportResult(imported.size(), skipped, List.copyOf(imported));
    }

    private List<PlaylistEntry> parsePlaylist(Path playlistFile) throws IOException {
        var lines = Files.readAllLines(playlistFile, StandardCharsets.UTF_8);
        String fileName = playlistFile.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean looksLikePls = fileName.endsWith(".pls")
                || lines.stream().anyMatch(line -> line.trim().equalsIgnoreCase("[playlist]"));
        return looksLikePls ? parsePls(lines) : parseM3u(lines);
    }

    private List<PlaylistEntry> parseM3u(List<String> lines) {
        var entries = new ArrayList<PlaylistEntry>();
        String pendingTitle = null;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isBlank()) continue;

            if (line.startsWith("#EXTINF")) {
                int comma = line.indexOf(',');
                if (comma >= 0 && comma < line.length() - 1) {
                    pendingTitle = line.substring(comma + 1).trim();
                }
                continue;
            }

            if (line.startsWith("#")) continue;

            entries.add(new PlaylistEntry(pendingTitle, line));
            pendingTitle = null;
        }

        return entries;
    }

    private List<PlaylistEntry> parsePls(List<String> lines) {
        var files = new TreeMap<Integer, String>();
        var titles = new HashMap<Integer, String>();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("[") || !line.contains("=")) continue;

            int equals = line.indexOf('=');
            String key = line.substring(0, equals).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(equals + 1).trim();

            if (key.startsWith("file")) {
                parseIndex(key, "file").ifPresent(index -> files.put(index, value));
            } else if (key.startsWith("title")) {
                parseIndex(key, "title").ifPresent(index -> titles.put(index, value));
            }
        }

        var entries = new ArrayList<PlaylistEntry>();
        files.forEach((index, url) -> entries.add(new PlaylistEntry(titles.get(index), url)));
        return entries;
    }

    private Optional<Integer> parseIndex(String key, String prefix) {
        try {
            return Optional.of(Integer.parseInt(key.substring(prefix.length())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String uniqueStationId(String seed) {
        String base = slug(seed);
        if (base.isBlank()) {
            base = "station";
        }

        String candidate = base;
        int suffix = 2;
        while (stationMap.containsKey(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String slug(String value) {
        String normalized = value.toLowerCase(TR)
                .replace("ı", "i")
                .replace("ğ", "g")
                .replace("ü", "u")
                .replace("ş", "s")
                .replace("ö", "o")
                .replace("ç", "c");
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    private boolean isHttpUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            var uri = java.net.URI.create(url);
            return uri.getScheme() != null
                    && (uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String fileStem(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String valueOrCurrent(String value, String current) {
        return value == null || value.isBlank() ? current : value.trim();
    }
}
