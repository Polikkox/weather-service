package com.weather.weatherservice.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.weather.weatherservice.entity.Weather;
import com.weather.weatherservice.model.WeatherResponseDto;
import com.weather.weatherservice.repository.WeatherRepository;

@Service
public class ExportService {

    @Autowired
    private WeatherRepository weatherRepository;

    @Autowired
    private FileService fileService;

    public String exportAllWeatherData() {
        try {
            List<Weather> allWeatherRecords = weatherRepository.findAllByOrderByDateTimeDesc();

            List<WeatherResponseDto> weatherDtos = allWeatherRecords.stream()
                    .map(this::convertToDto)
                    .toList();

            fileService.exportAllWeatherToFile(weatherDtos);

            weatherRepository.deleteAll();

            return String.format("Successfully exported %d weather records and cleared database", allWeatherRecords.size());
        } catch (Exception e) {
            throw new RuntimeException("Failed to export weather data: " + e.getMessage(), e);
        }
    }

    private WeatherResponseDto convertToDto(Weather weather) {
        WeatherResponseDto dto = new WeatherResponseDto();
        dto.setCity(weather.getCity());
        dto.setTemperature(weather.getTemperature());
        dto.setDate(weather.getDateTime().toLocalDate());
        return dto;
    }
}