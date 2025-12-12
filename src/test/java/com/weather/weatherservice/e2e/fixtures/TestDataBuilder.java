package com.weather.weatherservice.e2e.fixtures;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.weather.weatherservice.entity.Weather;

public class TestDataBuilder {

    public static Weather createWeatherEntity(String city, String temperature) {
        Weather weather = new Weather();
        weather.setCity(city);
        weather.setTemperature(temperature);
        weather.setDateTime(OffsetDateTime.now());
        return weather;
    }

    public static Weather createWeatherEntity(String city, String temperature, OffsetDateTime dateTime) {
        Weather weather = new Weather();
        weather.setCity(city);
        weather.setTemperature(temperature);
        weather.setDateTime(dateTime);
        return weather;
    }

    public static List<Weather> createMultipleWeatherEntities(int count, String cityPrefix) {
        List<Weather> entities = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Weather weather = new Weather();
            weather.setCity(cityPrefix + i);
            weather.setTemperature(String.valueOf(20.0 + i));
            weather.setDateTime(OffsetDateTime.now().minusHours(i));
            entities.add(weather);
        }
        return entities;
    }

    public static List<Weather> createWeatherEntitiesForCities(String... cities) {
        List<Weather> entities = new ArrayList<>();
        int tempCounter = 20;
        for (String city : cities) {
            Weather weather = new Weather();
            weather.setCity(city);
            weather.setTemperature(String.valueOf(tempCounter++));
            weather.setDateTime(OffsetDateTime.now());
            entities.add(weather);
        }
        return entities;
    }
}