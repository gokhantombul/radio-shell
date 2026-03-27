package com.radio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RadioStation(
        String id,
        String name,
        String country,
        String genre,
        String url,
        boolean favorite
) {
    public RadioStation withFavorite(boolean fav) {
        return new RadioStation(id, name, country, genre, url, fav);
    }
}
