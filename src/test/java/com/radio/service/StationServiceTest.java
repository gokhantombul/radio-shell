package com.radio.service;

import com.radio.config.RadioConfig;
import com.radio.model.RadioStation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StationServiceTest {

    @TempDir
    Path tempDir;

    private StationService service;
    private RadioConfig config;

    @BeforeEach
    void setUp() {
        config = new RadioConfig();
        config.setFavoritesFile(tempDir.resolve("favorites.json").toString());
        config.setCustomStationsFile(tempDir.resolve("custom-stations.json").toString());
        service = new StationService(config);
        service.init();
    }

    @Test
    void getAllStations_loadsBuiltInStations() {
        var stations = service.getAllStations();
        assertThat(stations).isNotEmpty();
    }

    @Test
    void getAllStations_sortedByCountryThenName() {
        var stations = service.getAllStations();
        for (int i = 1; i < stations.size(); i++) {
            var prev = stations.get(i - 1);
            var curr = stations.get(i);
            int cmp = prev.country().compareTo(curr.country());
            if (cmp == 0) {
                assertThat(prev.name().compareTo(curr.name())).isLessThanOrEqualTo(0);
            } else {
                assertThat(cmp).isLessThanOrEqualTo(0);
            }
        }
    }

    @Test
    void getStationsByCountry_filtersCorrectly() {
        var turkish = service.getStationsByCountry("Türkiye");
        assertThat(turkish).isNotEmpty();
        assertThat(turkish).allMatch(s -> s.country().equalsIgnoreCase("Türkiye"));
    }

    @Test
    void getStationsByCountry_unknownCountryReturnsEmpty() {
        var result = service.getStationsByCountry("Atlantis");
        assertThat(result).isEmpty();
    }

    @Test
    void getTurkishStations_returnsTurkishOnly() {
        var result = service.getTurkishStations();
        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(s -> s.country().equalsIgnoreCase("Türkiye"));
    }

    @Test
    void searchStations_findsByName() {
        var all = service.getAllStations();
        String someName = all.getFirst().name();
        String fragment = someName.substring(0, Math.min(4, someName.length()));
        var results = service.searchStations(fragment);
        assertThat(results).isNotEmpty();
    }

    @Test
    void searchStations_findsByCountry() {
        var results = service.searchStations("Türkiye");
        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(s -> s.country().equalsIgnoreCase("Türkiye"));
    }

    @Test
    void searchStations_caseInsensitive() {
        var lower = service.searchStations("türkiye");
        var upper = service.searchStations("TÜRKİYE");
        assertThat(lower.stream().map(RadioStation::id).sorted().toList())
                .isEqualTo(upper.stream().map(RadioStation::id).sorted().toList());
    }

    @Test
    void searchStations_noMatchReturnsEmpty() {
        var results = service.searchStations("xyzzy_no_match_12345");
        assertThat(results).isEmpty();
    }

    @Test
    void findStation_byExactId() {
        var first = service.getAllStations().getFirst();
        var found = service.findStation(first.id());
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(first.id());
    }

    @Test
    void findStation_byExactName_caseInsensitive() {
        var first = service.getAllStations().getFirst();
        var found = service.findStation(first.name().toLowerCase());
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(first.id());
    }

    @Test
    void findStation_unknownIdReturnsEmpty() {
        var found = service.findStation("this-id-does-not-exist");
        assertThat(found).isEmpty();
    }

    @Test
    void toggleFavorite_addsAndRemoves() {
        var station = service.getAllStations().getFirst();
        assertThat(service.isFavorite(station.id())).isFalse();

        service.toggleFavorite(station.id());
        assertThat(service.isFavorite(station.id())).isTrue();

        service.toggleFavorite(station.id());
        assertThat(service.isFavorite(station.id())).isFalse();
    }

    @Test
    void toggleFavorite_persistsAcrossReload() throws IOException {
        var station = service.getAllStations().getFirst();
        service.toggleFavorite(station.id());

        // New service instance loading same files
        var reloaded = new StationService(config);
        reloaded.init();
        assertThat(reloaded.isFavorite(station.id())).isTrue();
    }

    @Test
    void getFavorites_returnsOnlyFavorites() {
        var all = service.getAllStations();
        service.toggleFavorite(all.get(0).id());
        service.toggleFavorite(all.get(1).id());

        var favs = service.getFavorites();
        assertThat(favs).hasSize(2);
        assertThat(favs).allMatch(s -> service.isFavorite(s.id()));
    }

    @Test
    void addCustomStation_appearsInGetAll() {
        var custom = new RadioStation("test-id-1", "Test FM", "Test Ülke", "Pop", "http://example.com/stream", false);
        service.addCustomStation(custom);

        var found = service.findStation("test-id-1");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Test FM");
    }

    @Test
    void addCustomStation_persistsAcrossReload() {
        var custom = new RadioStation("test-persist-1", "Persist FM", "Test", "Jazz", "http://example.com/jazz", false);
        service.addCustomStation(custom);

        var reloaded = new StationService(config);
        reloaded.init();
        assertThat(reloaded.findStation("test-persist-1")).isPresent();
    }

    @Test
    void removeCustomStation_removesFromMap() {
        var custom = new RadioStation("test-remove-1", "Remove FM", "Test", "Rock", "http://example.com/rock", false);
        service.addCustomStation(custom);
        assertThat(service.findStation("test-remove-1")).isPresent();

        boolean removed = service.removeCustomStation("test-remove-1");
        assertThat(removed).isTrue();
        assertThat(service.findStation("test-remove-1")).isEmpty();
    }

    @Test
    void removeCustomStation_builtInCannotBeRemoved() {
        var builtIn = service.getAllStations().getFirst();
        boolean removed = service.removeCustomStation(builtIn.id());
        assertThat(removed).isFalse();
        assertThat(service.findStation(builtIn.id())).isPresent();
    }

    @Test
    void getCountries_returnsDistinctSortedList() {
        var countries = service.getCountries();
        assertThat(countries).isNotEmpty();
        assertThat(countries).doesNotHaveDuplicates();
        for (int i = 1; i < countries.size(); i++) {
            assertThat(countries.get(i - 1).compareTo(countries.get(i))).isLessThanOrEqualTo(0);
        }
    }

    @Test
    void getCountries_containsOnlyNormalizedNames() {
        var countries = service.getCountries();
        assertThat(countries).doesNotContain("UK", "USA", "USA/World", "Internet", "New Zealand");
        assertThat(countries).contains("Türkiye");
    }

    @Test
    void getGenres_returnsDistinctSortedList() {
        var genres = service.getGenres();
        assertThat(genres).isNotEmpty();
        assertThat(genres).doesNotHaveDuplicates();
    }

    @Test
    void getStationsByGenre_filtersCorrectly() {
        var genres = service.getGenres();
        if (genres.isEmpty()) return;
        String someGenre = genres.getFirst();
        var results = service.getStationsByGenre(someGenre);
        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(s -> s.genre().toLowerCase().contains(someGenre.toLowerCase()));
    }

    @Test
    void getAllStations_favoriteFieldReflectsState() {
        var first = service.getAllStations().getFirst();
        service.toggleFavorite(first.id());

        var withFav = service.getAllStations().stream()
                .filter(s -> s.id().equals(first.id()))
                .findFirst();
        assertThat(withFav).isPresent();
        assertThat(withFav.get().favorite()).isTrue();
    }
}
