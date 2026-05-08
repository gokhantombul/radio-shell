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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class StationService {

    private static final Logger log = LoggerFactory.getLogger(StationService.class);
    private static final Locale TR = Locale.forLanguageTag("tr");

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
}
