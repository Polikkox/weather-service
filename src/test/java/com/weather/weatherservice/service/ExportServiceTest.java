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

import weatherservice.entity.Weather;
import com.weather.weatherservice.model.WeatherResponseDto;
import weatherservice.repository.WeatherRepository;
import weatherservice.service.ExportService;
import weatherservice.service.FileService;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private WeatherRepository weatherRepository;

    @Mock
    private FileService fileService;

    @InjectMocks
    private ExportService exportService;

    private List<Weather> mockWeatherRecords;
    private Weather weather1;
    private Weather weather2;

    @BeforeEach
    void setUp() {
        weather1 = new Weather();
        weather1.setId(1L);
        weather1.setCity("Warsaw");
        weather1.setTemperature("25.5");
        weather1.setDateTime(OffsetDateTime.parse("2025-09-26T10:00:00Z"));

        weather2 = new Weather();
        weather2.setId(2L);
        weather2.setCity("Krakow");
        weather2.setTemperature("22.0");
        weather2.setDateTime(OffsetDateTime.parse("2025-09-25T15:30:00Z"));

        mockWeatherRecords = Arrays.asList(weather1, weather2);
    }

    @Test
    void exportAllWeatherData_ShouldReturnSuccessMessage_WhenDataExistsAndExportSucceeds() {
        when(weatherRepository.findAllByOrderByDateTimeDesc()).thenReturn(mockWeatherRecords);
        doNothing().when(fileService).exportAllWeatherToFile(anyList());
        doNothing().when(weatherRepository).deleteAll();

        String result = exportService.exportAllWeatherData();

        assertEquals("Successfully exported 2 weather records and cleared database", result);

        verify(weatherRepository, times(1)).findAllByOrderByDateTimeDesc();
        verify(fileService, times(1)).exportAllWeatherToFile(anyList());
        verify(weatherRepository, times(1)).deleteAll();
    }

    @Test
    void exportAllWeatherData_ShouldReturnSuccessMessage_WhenDatabaseIsEmpty() {
        when(weatherRepository.findAllByOrderByDateTimeDesc()).thenReturn(Collections.emptyList());
        doNothing().when(fileService).exportAllWeatherToFile(anyList());
        doNothing().when(weatherRepository).deleteAll();

        String result = exportService.exportAllWeatherData();

        assertEquals("Successfully exported 0 weather records and cleared database", result);

        verify(weatherRepository, times(1)).findAllByOrderByDateTimeDesc();
        verify(fileService, times(1)).exportAllWeatherToFile(Collections.emptyList());
        verify(weatherRepository, times(1)).deleteAll();
    }

    @Test
    void exportAllWeatherData_ShouldPassCorrectDtosToFileService() {
        when(weatherRepository.findAllByOrderByDateTimeDesc()).thenReturn(mockWeatherRecords);
        doNothing().when(fileService).exportAllWeatherToFile(anyList());
        doNothing().when(weatherRepository).deleteAll();

        exportService.exportAllWeatherData();

        ArgumentCaptor<List<WeatherResponseDto>> dtoCaptor = ArgumentCaptor.forClass(List.class);
        verify(fileService).exportAllWeatherToFile(dtoCaptor.capture());

        List<WeatherResponseDto> capturedDtos = dtoCaptor.getValue();
        assertEquals(2, capturedDtos.size());

        WeatherResponseDto dto1 = capturedDtos.get(0);
        assertEquals("Warsaw", dto1.getCity());
        assertEquals("25.5", dto1.getTemperature());
        assertEquals(LocalDate.of(2025, 9, 26), dto1.getDate());

        WeatherResponseDto dto2 = capturedDtos.get(1);
        assertEquals("Krakow", dto2.getCity());
        assertEquals("22.0", dto2.getTemperature());
        assertEquals(LocalDate.of(2025, 9, 25), dto2.getDate());
    }

    @Test
    void exportAllWeatherData_ShouldThrowRuntimeException_WhenRepositoryFindFails() {
        RuntimeException repositoryException = new RuntimeException("Database connection failed");
        when(weatherRepository.findAllByOrderByDateTimeDesc()).thenThrow(repositoryException);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> exportService.exportAllWeatherData());

        assertEquals("Failed to export weather data: Database connection failed", exception.getMessage());
        assertEquals(repositoryException, exception.getCause());

        verify(weatherRepository, times(1)).findAllByOrderByDateTimeDesc();
        verify(fileService, never()).exportAllWeatherToFile(anyList());
        verify(weatherRepository, never()).deleteAll();
    }

    @Test
    void exportAllWeatherData_ShouldThrowRuntimeException_WhenFileServiceFails() {
        when(weatherRepository.findAllByOrderByDateTimeDesc()).thenReturn(mockWeatherRecords);
        RuntimeException fileServiceException = new RuntimeException("File write failed");
        doThrow(fileServiceException).when(fileService).exportAllWeatherToFile(anyList());

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> exportService.exportAllWeatherData());

        assertEquals("Failed to export weather data: File write failed", exception.getMessage());
        assertEquals(fileServiceException, exception.getCause());

        verify(weatherRepository, times(1)).findAllByOrderByDateTimeDesc();
        verify(fileService, times(1)).exportAllWeatherToFile(anyList());
        verify(weatherRepository, never()).deleteAll();
    }

    @Test
    void exportAllWeatherData_ShouldThrowRuntimeException_WhenRepositoryDeleteFails() {
        when(weatherRepository.findAllByOrderByDateTimeDesc()).thenReturn(mockWeatherRecords);
        doNothing().when(fileService).exportAllWeatherToFile(anyList());
        RuntimeException deleteException = new RuntimeException("Delete operation failed");
        doThrow(deleteException).when(weatherRepository).deleteAll();

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> exportService.exportAllWeatherData());

        assertEquals("Failed to export weather data: Delete operation failed", exception.getMessage());
        assertEquals(deleteException, exception.getCause());

        verify(weatherRepository, times(1)).findAllByOrderByDateTimeDesc();
        verify(fileService, times(1)).exportAllWeatherToFile(anyList());
        verify(weatherRepository, times(1)).deleteAll();
    }

    @Test
    void exportAllWeatherData_ShouldExecuteOperationsInCorrectOrder() {
        when(weatherRepository.findAllByOrderByDateTimeDesc()).thenReturn(mockWeatherRecords);
        doNothing().when(fileService).exportAllWeatherToFile(anyList());
        doNothing().when(weatherRepository).deleteAll();

        exportService.exportAllWeatherData();

        var inOrder = inOrder(weatherRepository, fileService);
        inOrder.verify(weatherRepository).findAllByOrderByDateTimeDesc();
        inOrder.verify(fileService).exportAllWeatherToFile(anyList());
        inOrder.verify(weatherRepository).deleteAll();
    }

    @Test
    void exportAllWeatherData_ShouldHandleNullWeatherFields() {
        Weather weatherWithNulls = new Weather();
        weatherWithNulls.setId(3L);
        weatherWithNulls.setCity(null);
        weatherWithNulls.setTemperature(null);
        weatherWithNulls.setDateTime(OffsetDateTime.parse("2025-09-26T12:00:00Z"));

        when(weatherRepository.findAllByOrderByDateTimeDesc()).thenReturn(Arrays.asList(weatherWithNulls));
        doNothing().when(fileService).exportAllWeatherToFile(anyList());
        doNothing().when(weatherRepository).deleteAll();

        String result = exportService.exportAllWeatherData();

        assertEquals("Successfully exported 1 weather records and cleared database", result);

        ArgumentCaptor<List<WeatherResponseDto>> dtoCaptor = ArgumentCaptor.forClass(List.class);
        verify(fileService).exportAllWeatherToFile(dtoCaptor.capture());

        List<WeatherResponseDto> capturedDtos = dtoCaptor.getValue();
        assertEquals(1, capturedDtos.size());

        WeatherResponseDto dto = capturedDtos.get(0);
        assertNull(dto.getCity());
        assertNull(dto.getTemperature());
        assertEquals(LocalDate.of(2025, 9, 26), dto.getDate());
    }

    @Test
    void convertToDto_ShouldCorrectlyConvertWeatherToDto() {
        Weather weather = new Weather();
        weather.setCity("Gdansk");
        weather.setTemperature("18.5");
        weather.setDateTime(OffsetDateTime.parse("2025-09-24T08:15:30Z"));

        // Using reflection to test private method or create a public method for testing
        // For this test, we'll test through the public exportAllWeatherData method
        when(weatherRepository.findAllByOrderByDateTimeDesc()).thenReturn(Arrays.asList(weather));
        doNothing().when(fileService).exportAllWeatherToFile(anyList());
        doNothing().when(weatherRepository).deleteAll();

        exportService.exportAllWeatherData();

        ArgumentCaptor<List<WeatherResponseDto>> dtoCaptor = ArgumentCaptor.forClass(List.class);
        verify(fileService).exportAllWeatherToFile(dtoCaptor.capture());

        List<WeatherResponseDto> capturedDtos = dtoCaptor.getValue();
        assertEquals(1, capturedDtos.size());

        WeatherResponseDto dto = capturedDtos.get(0);
        assertEquals("Gdansk", dto.getCity());
        assertEquals("18.5", dto.getTemperature());
        assertEquals(LocalDate.of(2025, 9, 24), dto.getDate());
    }
}