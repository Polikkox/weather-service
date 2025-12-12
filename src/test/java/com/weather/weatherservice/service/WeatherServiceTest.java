package com.weather.weatherservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.weather.weatherservice.entity.Weather;
import com.weather.weatherservice.model.WeatherResponseDto;
import com.weather.weatherservice.repository.WeatherRepository;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    @Mock
    private WeatherRepository weatherRepository;

    @Mock
    private FileService fileService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private WeatherService weatherService;

    private List<Weather> mockWeatherEntities;
    private WeatherResponseDto mockWeatherResponseDto;
    private Weather mockWeatherEntity;

    @BeforeEach
    void setUp() {
        // Set up mock RestTemplate
        ReflectionTestUtils.setField(weatherService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(weatherService, "baseUrl", "http://localhost:8081");

        // Create mock weather entities
        mockWeatherEntity = new Weather();
        mockWeatherEntity.setId(1L);
        mockWeatherEntity.setCity("Warsaw");
        mockWeatherEntity.setTemperature("25.5");
        mockWeatherEntity.setDateTime(OffsetDateTime.parse("2025-09-26T10:00:00Z"));

        Weather weather2 = new Weather();
        weather2.setId(2L);
        weather2.setCity("Krakow");
        weather2.setTemperature("22.0");
        weather2.setDateTime(OffsetDateTime.parse("2025-09-25T15:30:00Z"));

        mockWeatherEntities = Arrays.asList(mockWeatherEntity, weather2);

        // Create mock weather response DTO
        mockWeatherResponseDto = new WeatherResponseDto();
        mockWeatherResponseDto.setCity("Warsaw");
        mockWeatherResponseDto.setTemperature("25.5");
        mockWeatherResponseDto.setDate(LocalDate.of(2025, 9, 26));
    }

    @Test
    void getWeatherHistory_ShouldReturnWeatherDtoList_WhenRecordsExist() {
        String city = "Warsaw";
        OffsetDateTime fromDate = OffsetDateTime.parse("2025-09-20T00:00:00Z");
        OffsetDateTime toDate = OffsetDateTime.parse("2025-09-30T23:59:59Z");

        when(weatherRepository.findAllByCityAndDateTimeBetween(city, fromDate, toDate))
            .thenReturn(Arrays.asList(mockWeatherEntity));

        List<WeatherResponseDto> result = weatherService.getWeatherHistory(city, fromDate, toDate);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Warsaw", result.get(0).getCity());
        assertEquals("25.5", result.get(0).getTemperature());
        assertEquals(LocalDate.of(2025, 9, 26), result.get(0).getDate());

        verify(weatherRepository, times(1)).findAllByCityAndDateTimeBetween(city, fromDate, toDate);
    }

    @Test
    void getWeatherHistory_ShouldReturnEmptyList_WhenNoRecordsFound() {
        String city = "Gdansk";
        OffsetDateTime fromDate = OffsetDateTime.parse("2025-09-20T00:00:00Z");
        OffsetDateTime toDate = OffsetDateTime.parse("2025-09-30T23:59:59Z");

        when(weatherRepository.findAllByCityAndDateTimeBetween(city, fromDate, toDate))
            .thenReturn(Collections.emptyList());

        List<WeatherResponseDto> result = weatherService.getWeatherHistory(city, fromDate, toDate);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(weatherRepository, times(1)).findAllByCityAndDateTimeBetween(city, fromDate, toDate);
    }

    @Test
    void getWeatherHistory_ShouldReturnMultipleRecords_WhenMultipleRecordsExist() {
        String city = "Warsaw";
        OffsetDateTime fromDate = OffsetDateTime.parse("2025-09-20T00:00:00Z");
        OffsetDateTime toDate = OffsetDateTime.parse("2025-09-30T23:59:59Z");

        when(weatherRepository.findAllByCityAndDateTimeBetween(city, fromDate, toDate))
            .thenReturn(mockWeatherEntities);

        List<WeatherResponseDto> result = weatherService.getWeatherHistory(city, fromDate, toDate);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Warsaw", result.get(0).getCity());
        assertEquals("Krakow", result.get(1).getCity());

        verify(weatherRepository, times(1)).findAllByCityAndDateTimeBetween(city, fromDate, toDate);
    }

    @Test
    void getWeatherHistory_ShouldAcceptNullParameters() {
        when(weatherRepository.findAllByOrderByDateTimeDesc())
            .thenReturn(mockWeatherEntities);

        List<WeatherResponseDto> result = weatherService.getWeatherHistory(null, null, null);

        assertNotNull(result);
        assertEquals(2, result.size());

        verify(weatherRepository, times(1)).findAllByOrderByDateTimeDesc();
    }

    @Test
    void handleWeatherRequest_ShouldReturnWeatherResponse_WhenApiCallSucceeds() {
        String city = "Warsaw";
        String expectedUrl = "http://localhost:8081/api/v1/weather?city=Warsaw";

        when(restTemplate.getForObject(expectedUrl, WeatherResponseDto.class))
            .thenReturn(mockWeatherResponseDto);
        when(weatherRepository.save(any(Weather.class))).thenReturn(mockWeatherEntity);

        WeatherResponseDto result = weatherService.handleWeatherRequest(city);

        assertNotNull(result);
        assertEquals("Warsaw", result.getCity());
        assertEquals("25.5", result.getTemperature());
        assertEquals(LocalDate.of(2025, 9, 26), result.getDate());

        verify(restTemplate, times(1)).getForObject(expectedUrl, WeatherResponseDto.class);
        verify(weatherRepository, times(1)).save(any(Weather.class));
    }

    @Test
    void handleWeatherRequest_ShouldSaveWeatherToRepository_WhenApiReturnsData() {
        String city = "Warsaw";
        String expectedUrl = "http://localhost:8081/api/v1/weather?city=Warsaw";

        when(restTemplate.getForObject(expectedUrl, WeatherResponseDto.class))
            .thenReturn(mockWeatherResponseDto);
        when(weatherRepository.save(any(Weather.class))).thenReturn(mockWeatherEntity);

        weatherService.handleWeatherRequest(city);

        ArgumentCaptor<Weather> weatherCaptor = ArgumentCaptor.forClass(Weather.class);
        verify(weatherRepository).save(weatherCaptor.capture());

        Weather savedWeather = weatherCaptor.getValue();
        assertEquals("Warsaw", savedWeather.getCity());
        assertEquals("25.5", savedWeather.getTemperature());
        assertNotNull(savedWeather.getDateTime());
    }

    @Test
    void handleWeatherRequest_ShouldNotSaveToRepository_WhenApiReturnsNull() {
        String city = "Warsaw";
        String expectedUrl = "http://localhost:8081/api/v1/weather?city=Warsaw";

        when(restTemplate.getForObject(expectedUrl, WeatherResponseDto.class))
            .thenReturn(null);

        WeatherResponseDto result = weatherService.handleWeatherRequest(city);

        assertNull(result);
        verify(restTemplate, times(1)).getForObject(expectedUrl, WeatherResponseDto.class);
        verify(weatherRepository, never()).save(any(Weather.class));
    }

    @Test
    void handleWeatherRequest_ShouldConstructCorrectUrl_WithCityParameter() {
        String city = "Krakow";
        String expectedUrl = "http://localhost:8081/api/v1/weather?city=Krakow";

        when(restTemplate.getForObject(expectedUrl, WeatherResponseDto.class))
            .thenReturn(mockWeatherResponseDto);
        when(weatherRepository.save(any(Weather.class))).thenReturn(mockWeatherEntity);

        weatherService.handleWeatherRequest(city);

        verify(restTemplate, times(1)).getForObject(expectedUrl, WeatherResponseDto.class);
    }

    @Test
    void handleWeatherRequest_ShouldUseConfiguredBaseUrl() {
        ReflectionTestUtils.setField(weatherService, "baseUrl", "http://custom-api:9000");

        String city = "Warsaw";
        String expectedUrl = "http://custom-api:9000/api/v1/weather?city=Warsaw";

        when(restTemplate.getForObject(expectedUrl, WeatherResponseDto.class))
            .thenReturn(mockWeatherResponseDto);
        when(weatherRepository.save(any(Weather.class))).thenReturn(mockWeatherEntity);

        weatherService.handleWeatherRequest(city);

        verify(restTemplate, times(1)).getForObject(expectedUrl, WeatherResponseDto.class);
    }

    @Test
    void convertToDto_ShouldCorrectlyConvertWeatherEntityToDto() {
        when(weatherRepository.findAllByCityAndDateTimeBetween(any(), any(), any()))
            .thenReturn(Arrays.asList(mockWeatherEntity));

        List<WeatherResponseDto> result = weatherService.getWeatherHistory("Warsaw",
            OffsetDateTime.now(), OffsetDateTime.now());

        WeatherResponseDto dto = result.get(0);
        assertEquals(mockWeatherEntity.getCity(), dto.getCity());
        assertEquals(mockWeatherEntity.getTemperature(), dto.getTemperature());
        assertEquals(mockWeatherEntity.getDateTime().toLocalDate(), dto.getDate());
    }

    @Test
    void saveWeatherResponse_ShouldSaveWithCurrentTimestamp() throws InterruptedException {
        String city = "Warsaw";
        String expectedUrl = "http://localhost:8081/api/v1/weather?city=Warsaw";

        OffsetDateTime beforeCall = OffsetDateTime.now();
        Thread.sleep(10); // Small delay to ensure timestamp difference

        when(restTemplate.getForObject(expectedUrl, WeatherResponseDto.class))
            .thenReturn(mockWeatherResponseDto);
        when(weatherRepository.save(any(Weather.class))).thenReturn(mockWeatherEntity);

        weatherService.handleWeatherRequest(city);

        Thread.sleep(10);
        OffsetDateTime afterCall = OffsetDateTime.now();

        ArgumentCaptor<Weather> weatherCaptor = ArgumentCaptor.forClass(Weather.class);
        verify(weatherRepository).save(weatherCaptor.capture());

        Weather savedWeather = weatherCaptor.getValue();
        assertTrue(savedWeather.getDateTime().isAfter(beforeCall.minusSeconds(1)));
        assertTrue(savedWeather.getDateTime().isBefore(afterCall.plusSeconds(1)));
    }

    @Test
    void handleWeatherRequest_ShouldHandleSpecialCharactersInCityName() {
        String city = "São Paulo";
        String expectedUrl = "http://localhost:8081/api/v1/weather?city=S%C3%A3o%20Paulo";

        when(restTemplate.getForObject(expectedUrl, WeatherResponseDto.class))
            .thenReturn(mockWeatherResponseDto);
        when(weatherRepository.save(any(Weather.class))).thenReturn(mockWeatherEntity);

        weatherService.handleWeatherRequest(city);

        verify(restTemplate, times(1)).getForObject(expectedUrl, WeatherResponseDto.class);
    }

    @Test
    void getWeatherHistory_ShouldConvertAllFields_WhenMultipleRecordsExist() {
        when(weatherRepository.findAllByCityAndDateTimeBetween(any(), any(), any()))
            .thenReturn(mockWeatherEntities);

        List<WeatherResponseDto> result = weatherService.getWeatherHistory("Warsaw",
            OffsetDateTime.now(), OffsetDateTime.now());

        assertEquals(2, result.size());

        // Verify first record conversion
        assertEquals("Warsaw", result.get(0).getCity());
        assertEquals("25.5", result.get(0).getTemperature());
        assertEquals(LocalDate.of(2025, 9, 26), result.get(0).getDate());

        // Verify second record conversion
        assertEquals("Krakow", result.get(1).getCity());
        assertEquals("22.0", result.get(1).getTemperature());
        assertEquals(LocalDate.of(2025, 9, 25), result.get(1).getDate());
    }
}