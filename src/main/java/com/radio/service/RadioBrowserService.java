package com.radio.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Service
public class RadioBrowserService {

    private static final Logger log = LoggerFactory.getLogger(RadioBrowserService.class);
    private static final String API_BASE = "https://de1.api.radio-browser.info/json";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OnlineStation(
            @JsonProperty("stationuuid") String uuid,
            @JsonProperty("name") String name,
            @JsonProperty("country") String country,
            @JsonProperty("tags") String tags,
            @JsonProperty("url_resolved") String url,
            @JsonProperty("bitrate") int bitrate,
            @JsonProperty("votes") int votes,
            @JsonProperty("codec") String codec
    ) {
        public String genreDisplay() {
            if (tags == null || tags.isBlank()) return "Çeşitli";
            String first = tags.split(",")[0].trim();
            return first.isBlank() ? "Çeşitli" : first;
        }

        public String countryDisplay() {
            return country == null || country.isBlank() ? "Bilinmiyor" : country;
        }
    }

    private final HttpClient client;
    private final ObjectMapper mapper;

    public RadioBrowserService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.mapper = new ObjectMapper();
    }

    public List<OnlineStation> search(String query, String country, String tag, int limit) {
        var url = new StringBuilder(API_BASE + "/stations/search?hidebroken=true&order=votes&reverse=true");
        if (limit > 0) url.append("&limit=").append(limit);
        if (hasText(query))   url.append("&name=").append(encode(query));
        if (hasText(country)) url.append("&country=").append(encode(country));
        if (hasText(tag))     url.append("&tag=").append(encode(tag));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Radio Shell/2.0")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("RadioBrowser API yanıt kodu: {}", response.statusCode());
                return List.of();
            }

            OnlineStation[] stations = mapper.readValue(response.body(), OnlineStation[].class);
            return List.of(stations).stream()
                    .filter(s -> s.url() != null && s.url().startsWith("http"))
                    .toList();
        } catch (Exception e) {
            log.error("RadioBrowser araması başarısız: {}", e.getMessage());
            return List.of();
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
