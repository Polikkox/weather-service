package com.weather.weatherservice.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.weather.weatherservice.entity.Weather;
import com.weather.weatherservice.model.WeatherResponseDto;
import com.weather.weatherservice.repository.WeatherRepository;

@Service
public class WeatherService {

    private final RestTemplate restTemplate;

    @Autowired
    private WeatherRepository weatherRepository;

    @Autowired
    private FileService fileService;

    @Value("${weather.api.base-url:http://localhost:8081}")
    private String baseUrl;

    public WeatherService() {
        this.restTemplate = new RestTemplate();
    }


    public List<WeatherResponseDto> getWeatherHistory(String city, OffsetDateTime fromDate, OffsetDateTime toDate) {
        List<Weather> weatherList;

        if (city != null && fromDate != null && toDate != null) {
            weatherList = weatherRepository.findAllByCityAndDateTimeBetween(city, fromDate, toDate);
        } else if (city != null) {
            weatherList = weatherRepository.findAllByCity(city);
        } else if (fromDate != null && toDate != null) {
            weatherList = weatherRepository.findAllByDateTimeBetween(fromDate, toDate);
        } else {
            weatherList = weatherRepository.findAllByOrderByDateTimeDesc();
        }

        return weatherList.stream().map(this::convertToDto).toList();
    }

    public WeatherResponseDto handleWeatherRequest(String city) {
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/api/v1/weather")
                .queryParam("city", city)
                .toUriString();

        WeatherResponseDto response = restTemplate.getForObject(url, WeatherResponseDto.class);
        saveWeatherResponse(response);

        return response;
    }

    private WeatherResponseDto convertToDto(Weather weather) {
        WeatherResponseDto dto = new WeatherResponseDto();
        dto.setCity(weather.getCity());
        dto.setTemperature(weather.getTemperature());
        dto.setDate(weather.getDateTime().toLocalDate());
        return dto;
    }

    private void saveWeatherResponse(WeatherResponseDto response) {
        if (response != null) {
            Weather weatherEntity = new Weather(response.getCity(), response.getTemperature(), OffsetDateTime.now());
            weatherRepository.save(weatherEntity);
        }
    }
}



