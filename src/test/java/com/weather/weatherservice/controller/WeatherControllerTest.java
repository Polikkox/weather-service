package com.weather.weatherservice.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.weather.weatherservice.model.WeatherResponseDto;
import com.weather.weatherservice.service.ExportService;
import com.weather.weatherservice.service.WeatherService;

@ExtendWith(MockitoExtension.class)
class WeatherControllerTest {

    @Mock
    private WeatherService weatherService;

    @Mock
    private ExportService exportService;

    @InjectMocks
    private WeatherController weatherController;

    private WeatherResponseDto mockWeatherResponse;
    private List<WeatherResponseDto> mockWeatherHistory;

    @BeforeEach
    void setUp() {
        mockWeatherResponse = new WeatherResponseDto();
        mockWeatherResponse.setCity("Warsaw");
        mockWeatherResponse.setTemperature("25.5");
        mockWeatherResponse.setDate(LocalDate.of(2025, 9, 26));

        WeatherResponseDto historyResponse1 = new WeatherResponseDto();
        historyResponse1.setCity("Krakow");
        historyResponse1.setTemperature("22.0");
        historyResponse1.setDate(LocalDate.of(2025, 9, 25));

        WeatherResponseDto historyResponse2 = new WeatherResponseDto();
        historyResponse2.setCity("Gdansk");
        historyResponse2.setTemperature("18.5");
        historyResponse2.setDate(LocalDate.of(2025, 9, 24));

        mockWeatherHistory = Arrays.asList(historyResponse1, historyResponse2);
    }

    @Test
    void checkWeather_ShouldReturnWeatherResponse_WhenValidCityProvided() {
        String city = "Warsaw";
        when(weatherService.handleWeatherRequest(city)).thenReturn(mockWeatherResponse);

        ResponseEntity<WeatherResponseDto> result = weatherController.checkWeather(city);

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("Warsaw", result.getBody().getCity());
        assertEquals("25.5", result.getBody().getTemperature());
        assertEquals(LocalDate.of(2025, 9, 26), result.getBody().getDate());

        verify(weatherService, times(1)).handleWeatherRequest(city);
    }

    @Test
    void checkWeather_ShouldCallWeatherService_WithCorrectParameters() {
        String city = "Krakow";
        when(weatherService.handleWeatherRequest(city)).thenReturn(mockWeatherResponse);

        weatherController.checkWeather(city);

        verify(weatherService).handleWeatherRequest(city);
        verifyNoMoreInteractions(weatherService);
    }

    @Test
    void checkWeatherHistory_ShouldReturnWeatherHistoryList_WhenValidParametersProvided() {
        String city = "Krakow";
        OffsetDateTime fromDate = OffsetDateTime.parse("2025-09-20T00:00:00Z");
        OffsetDateTime toDate = OffsetDateTime.parse("2025-09-26T23:59:59Z");

        when(weatherService.getWeatherHistory(city, fromDate, toDate)).thenReturn(mockWeatherHistory);

        ResponseEntity<List<WeatherResponseDto>> result = weatherController.checkWeatherHistory(city, fromDate, toDate);

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(2, result.getBody().size());
        assertEquals("Krakow", result.getBody().get(0).getCity());
        assertEquals("Gdansk", result.getBody().get(1).getCity());

        verify(weatherService, times(1)).getWeatherHistory(city, fromDate, toDate);
    }

    @Test
    void checkWeatherHistory_ShouldAcceptNullParameters() {
        when(weatherService.getWeatherHistory(null, null, null)).thenReturn(mockWeatherHistory);

        ResponseEntity<List<WeatherResponseDto>> result = weatherController.checkWeatherHistory(null, null, null);

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(2, result.getBody().size());

        verify(weatherService, times(1)).getWeatherHistory(null, null, null);
    }

    @Test
    void exportWeatherData_ShouldReturnSuccessMessage_WhenExportSucceeds() {
        String expectedMessage = "Successfully exported 5 weather records and cleared database";
        when(exportService.exportAllWeatherData()).thenReturn(expectedMessage);

        ResponseEntity<String> result = weatherController.exportWeatherData();

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(expectedMessage, result.getBody());
        verify(exportService, times(1)).exportAllWeatherData();
    }

    @Test
    void exportWeatherData_ShouldReturn500_WhenExportFails() {
        when(exportService.exportAllWeatherData()).thenThrow(new RuntimeException("Export error"));

        ResponseEntity<String> result = weatherController.exportWeatherData();

        assertNotNull(result);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertTrue(result.getBody().contains("Export failed"));
        verify(exportService, times(1)).exportAllWeatherData();
    }

}