package com.weather.weatherservice.controller;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import com.weather.weatherservice.api.DefaultApiDelegate;
import com.weather.weatherservice.model.WeatherResponseDto;
import com.weather.weatherservice.service.ExportService;
import com.weather.weatherservice.service.WeatherService;

@Controller
public class WeatherController implements DefaultApiDelegate {

    @Autowired
    private WeatherService weatherService;

    @Autowired
    private ExportService exportService;

    @Override
    public ResponseEntity<WeatherResponseDto> checkWeather(String city) {
        if (city == null || city.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        WeatherResponseDto response = weatherService.handleWeatherRequest(city);
        return ResponseEntity.ok(response);
    }
    //    2025-01-15T14:30:00+01:00
    @Override
    public ResponseEntity<List<WeatherResponseDto>> checkWeatherHistory(String city, @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromDate, @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toDate) {
        List<WeatherResponseDto> response = weatherService.getWeatherHistory(city, fromDate, toDate);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<String> exportWeatherData() {
        try {
            String result = exportService.exportAllWeatherData();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Export failed: " + e.getMessage());
        }
    }

}

