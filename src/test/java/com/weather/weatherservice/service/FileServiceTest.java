package com.weather.weatherservice.service;

import static org.junit.jupiter.api.Assertions.*;

import weatherservice.service.FileService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import com.weather.weatherservice.model.WeatherResponseDto;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @TempDir
    Path tempDir;

    private FileService fileService;
    private List<WeatherResponseDto> mockWeatherData;

    @BeforeEach
    void setUp() {
        fileService = new FileService();
        // Set the base path to our temp directory for testing using system property
        System.setProperty("weather.file.base-path", tempDir.toString());

        // Create mock weather data
        WeatherResponseDto weather1 = new WeatherResponseDto();
        weather1.setCity("Warsaw");
        weather1.setTemperature("25.5");
        weather1.setDate(LocalDate.of(2025, 9, 26));

        WeatherResponseDto weather2 = new WeatherResponseDto();
        weather2.setCity("Krakow");
        weather2.setTemperature("22.0");
        weather2.setDate(LocalDate.of(2025, 9, 25));

        mockWeatherData = Arrays.asList(weather1, weather2);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clear system property
        System.clearProperty("weather.file.base-path");

        // Clean up any files created during tests
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Ignore cleanup errors in tests
                    }
                });
        }
    }

    @Test
    void exportAllWeatherToFile_ShouldCreateFileWithCorrectContent_WhenDataProvided() throws IOException {
        fileService.exportAllWeatherToFile(mockWeatherData);

        // Find the created file (filename contains timestamp)
        List<Path> createdFiles = Files.list(tempDir)
            .filter(path -> path.getFileName().toString().startsWith("weather_export_"))
            .filter(path -> path.getFileName().toString().endsWith(".txt"))
            .toList();

        assertEquals(1, createdFiles.size(), "Exactly one export file should be created");

        Path exportFile = createdFiles.get(0);
        assertTrue(Files.exists(exportFile), "Export file should exist");

        // Read and verify file content
        String content = Files.readString(exportFile);

        assertTrue(content.contains("Weather Data Export - Generated on:"), "File should contain header");
        assertTrue(content.contains("=========================================="), "File should contain separator");
        assertTrue(content.contains("City: Warsaw, Temperature: 25.5, Date: 2025-09-26"), "File should contain first weather record");
        assertTrue(content.contains("City: Krakow, Temperature: 22.0, Date: 2025-09-25"), "File should contain second weather record");
    }

    @Test
    void exportAllWeatherToFile_ShouldCreateFileWithHeader_WhenEmptyListProvided() throws IOException {
        fileService.exportAllWeatherToFile(Collections.emptyList());

        // Find the created file
        List<Path> createdFiles = Files.list(tempDir)
            .filter(path -> path.getFileName().toString().startsWith("weather_export_"))
            .toList();

        assertEquals(1, createdFiles.size(), "File should be created even for empty list");

        Path exportFile = createdFiles.get(0);
        String content = Files.readString(exportFile);

        assertTrue(content.contains("Weather Data Export - Generated on:"), "File should contain header");
        assertTrue(content.contains("=========================================="), "File should contain separator");

        // Count lines - should only have header lines
        long lineCount = content.lines().count();
        assertEquals(3, lineCount, "File should contain only header and separator lines for empty data");
    }

    @Test
    void exportAllWeatherToFile_ShouldHandleNullFields_WhenWeatherDataContainsNulls() throws IOException {
        WeatherResponseDto weatherWithNulls = new WeatherResponseDto();
        weatherWithNulls.setCity(null);
        weatherWithNulls.setTemperature(null);
        weatherWithNulls.setDate(LocalDate.of(2025, 9, 26));

        fileService.exportAllWeatherToFile(Arrays.asList(weatherWithNulls));

        // Find and read the created file
        List<Path> createdFiles = Files.list(tempDir)
            .filter(path -> path.getFileName().toString().startsWith("weather_export_"))
            .toList();

        Path exportFile = createdFiles.get(0);
        String content = Files.readString(exportFile);

        assertTrue(content.contains("City: null, Temperature: null, Date: 2025-09-26"),
            "File should handle null fields gracefully");
    }

    @Test
    void exportAllWeatherToFile_ShouldCreateFileWithTimestampInName() throws IOException {
        LocalDateTime beforeExport = LocalDateTime.now();

        fileService.exportAllWeatherToFile(mockWeatherData);

        LocalDateTime afterExport = LocalDateTime.now();

        // Find the created file
        List<Path> createdFiles = Files.list(tempDir)
            .filter(path -> path.getFileName().toString().startsWith("weather_export_"))
            .toList();

        assertEquals(1, createdFiles.size());

        String fileName = createdFiles.get(0).getFileName().toString();
        assertTrue(fileName.startsWith("weather_export_"), "File should start with weather_export_");
        assertTrue(fileName.endsWith(".txt"), "File should end with .txt");

        // Extract timestamp from filename and verify it's reasonable
        String timestampPart = fileName.substring("weather_export_".length(), fileName.length() - ".txt".length());

        // Verify timestamp format (yyyy-MM-dd_HH-mm-ss)
        assertTrue(timestampPart.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}"),
            "Timestamp should match yyyy-MM-dd_HH-mm-ss format");

        // Parse and verify timestamp is reasonable
        DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        LocalDateTime fileTimestamp = LocalDateTime.parse(timestampPart, timestampFormatter);

        assertTrue(fileTimestamp.isAfter(beforeExport.minusSeconds(1)) &&
                  fileTimestamp.isBefore(afterExport.plusSeconds(1)),
                  "File timestamp should be within test execution time");
    }

    @Test
    void exportAllWeatherToFile_ShouldCreateDirectories_WhenBasePathDoesNotExist() throws IOException {
        // Create a nested path that doesn't exist
        Path nestedPath = tempDir.resolve("nested").resolve("path").resolve("weather");
        System.setProperty("weather.file.base-path", nestedPath.toString());

        assertFalse(Files.exists(nestedPath), "Nested path should not exist initially");

        fileService.exportAllWeatherToFile(mockWeatherData);

        assertTrue(Files.exists(nestedPath), "Nested directories should be created");
        assertTrue(Files.isDirectory(nestedPath), "Created path should be a directory");

        // Verify file was created in the nested directory
        List<Path> createdFiles = Files.list(nestedPath)
            .filter(path -> path.getFileName().toString().startsWith("weather_export_"))
            .toList();

        assertEquals(1, createdFiles.size(), "File should be created in nested directory");
    }

    @Test
    void exportAllWeatherToFile_ShouldContainCorrectTimestamp_InFileContent() throws IOException {
        LocalDateTime beforeExport = LocalDateTime.now();

        fileService.exportAllWeatherToFile(mockWeatherData);

        LocalDateTime afterExport = LocalDateTime.now();

        // Find and read the created file
        List<Path> createdFiles = Files.list(tempDir)
            .filter(path -> path.getFileName().toString().startsWith("weather_export_"))
            .toList();

        Path exportFile = createdFiles.get(0);
        String content = Files.readString(exportFile);

        // Extract timestamp from content
        String[] lines = content.split("\n");
        String headerLine = lines[0];
        assertTrue(headerLine.contains("Weather Data Export - Generated on:"), "Header should contain generation timestamp");

        // Extract timestamp from header line
        String timestampString = headerLine.substring(headerLine.indexOf("Generated on: ") + "Generated on: ".length());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime contentTimestamp = LocalDateTime.parse(timestampString, formatter);

        assertTrue(contentTimestamp.isAfter(beforeExport.minusSeconds(1)) &&
                  contentTimestamp.isBefore(afterExport.plusSeconds(1)),
                  "Content timestamp should be within test execution time");
    }

    @Test
    void exportAllWeatherToFile_ShouldThrowRuntimeException_WhenIOExceptionOccurs() throws IOException {
        // Create a file where we want to create directory to force IOException
        Path blockingFile = tempDir.resolve("blocking-file");
        Files.createFile(blockingFile);

        // Make the file read-only to prevent deletion
        blockingFile.toFile().setReadOnly();

        // Try to create a directory with the same name as the existing file
        System.setProperty("weather.file.base-path", blockingFile.toString());

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> fileService.exportAllWeatherToFile(mockWeatherData));

        assertEquals("Failed to export weather data", exception.getMessage());
        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    void exportAllWeatherToFile_ShouldFormatWeatherDataCorrectly() throws IOException {
        WeatherResponseDto weather = new WeatherResponseDto();
        weather.setCity("Gdansk");
        weather.setTemperature("18.5");
        weather.setDate(LocalDate.of(2025, 12, 31));

        fileService.exportAllWeatherToFile(Arrays.asList(weather));

        // Find and read the created file
        List<Path> createdFiles = Files.list(tempDir)
            .filter(path -> path.getFileName().toString().startsWith("weather_export_"))
            .toList();

        Path exportFile = createdFiles.get(0);
        String content = Files.readString(exportFile);

        assertTrue(content.contains("City: Gdansk, Temperature: 18.5, Date: 2025-12-31"),
            "Weather data should be formatted correctly");
    }

    @Test
    void exportAllWeatherToFile_ShouldCreateUniqueFiles_WhenCalledMultipleTimes() throws IOException, InterruptedException {
        fileService.exportAllWeatherToFile(mockWeatherData);

        // Wait a second to ensure different timestamps
        Thread.sleep(1100);

        fileService.exportAllWeatherToFile(mockWeatherData);

        // Verify two different files were created
        List<Path> createdFiles = Files.list(tempDir)
            .filter(path -> path.getFileName().toString().startsWith("weather_export_"))
            .toList();

        assertEquals(2, createdFiles.size(), "Two separate files should be created");

        // Verify files have different names
        String fileName1 = createdFiles.get(0).getFileName().toString();
        String fileName2 = createdFiles.get(1).getFileName().toString();
        assertNotEquals(fileName1, fileName2, "Files should have different names");
    }
}