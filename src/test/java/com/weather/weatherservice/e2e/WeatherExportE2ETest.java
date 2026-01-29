package com.weather.weatherservice.e2e;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import com.weather.weatherservice.e2e.fixtures.TestDataBuilder;
import weatherservice.entity.Weather;
import weatherservice.repository.WeatherRepository;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb_export",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    }
)
@ActiveProfiles("test")
class WeatherExportE2ETest {

    @TempDir
    Path tempExportDir;

    @LocalServerPort
    private int port;

    @Autowired
    private WeatherRepository weatherRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";

        weatherRepository.deleteAll();

        // Set export path to temp directory
        System.setProperty("weather.file.base-path", tempExportDir.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        weatherRepository.deleteAll();

        // Clean up exported files
        if (Files.exists(tempExportDir)) {
            Files.walk(tempExportDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                });
        }
    }

    @Test
    void shouldExportWeatherDataAndClearDatabase() throws IOException {
        // Given: Weather data exists in database
        weatherRepository.save(TestDataBuilder.createWeatherEntity("Warsaw", "25.5"));
        weatherRepository.save(TestDataBuilder.createWeatherEntity("Krakow", "22.0"));
        weatherRepository.save(TestDataBuilder.createWeatherEntity("Gdansk", "18.5"));

        long countBeforeExport = weatherRepository.count();
        assertEquals(3, countBeforeExport);

        // When: User exports weather data
        String response = given()
            .when()
                .post("/api/v1/weather/export")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
            .extract()
                .asString();

        // Then: Verify success message
        assertTrue(response.contains("Successfully exported 3 weather records"));
        assertTrue(response.contains("cleared database"));

        // And: Verify database is empty
        long countAfterExport = weatherRepository.count();
        assertEquals(0, countAfterExport);

        // And: Verify export file was created
        List<Path> exportFiles = Files.list(tempExportDir)
            .filter(path -> path.getFileName().toString().startsWith("weather_export_"))
            .filter(path -> path.getFileName().toString().endsWith(".txt"))
            .collect(Collectors.toList());

        assertEquals(1, exportFiles.size(), "Should create one export file");

        // And: Verify file content
        Path exportFile = exportFiles.get(0);
        String content = Files.readString(exportFile);

        assertTrue(content.contains("Weather Data Export"), "Should contain header");
        assertTrue(content.contains("Warsaw"), "Should contain Warsaw data");
        assertTrue(content.contains("25.5"), "Should contain temperature");
        assertTrue(content.contains("Krakow"), "Should contain Krakow data");
        assertTrue(content.contains("Gdansk"), "Should contain Gdansk data");
    }

    @Test
    void shouldHandleExportWithEmptyDatabase() throws IOException {
        // Given: Empty database
        assertEquals(0, weatherRepository.count());

        // When: User exports weather data
        String response = given()
            .when()
                .post("/api/v1/weather/export")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
            .extract()
                .asString();

        // Then: Verify success message for empty export
        assertTrue(response.contains("Successfully exported 0 weather records"));

        // And: Verify file was still created
        List<Path> exportFiles = Files.list(tempExportDir)
            .filter(path -> path.getFileName().toString().startsWith("weather_export_"))
            .collect(Collectors.toList());

        assertEquals(1, exportFiles.size());

        // And: Verify file contains only header
        Path exportFile = exportFiles.get(0);
        String content = Files.readString(exportFile);
        assertTrue(content.contains("Weather Data Export"));
        assertFalse(content.contains("City:"), "Should not contain any weather data");
    }

    @Test
    void shouldCreateUniqueExportFilesForMultipleExports() throws IOException, InterruptedException {
        // Given: Weather data exists
        weatherRepository.save(TestDataBuilder.createWeatherEntity("Warsaw", "25.5"));

        // When: User exports data twice
        given().when().post("/api/v1/weather/export").then().statusCode(200);

        // Add new data for second export
        weatherRepository.save(TestDataBuilder.createWeatherEntity("Krakow", "22.0"));

        // Wait to ensure different timestamp
        Thread.sleep(1100);

        given().when().post("/api/v1/weather/export").then().statusCode(200);

        // Then: Two different export files should exist
        List<Path> exportFiles = Files.list(tempExportDir)
            .filter(path -> path.getFileName().toString().startsWith("weather_export_"))
            .collect(Collectors.toList());

        assertEquals(2, exportFiles.size(), "Should create two separate export files");

        // Verify files have different names
        String fileName1 = exportFiles.get(0).getFileName().toString();
        String fileName2 = exportFiles.get(1).getFileName().toString();
        assertNotEquals(fileName1, fileName2, "Export files should have different names");
    }

    @Test
    void shouldExportLargeAmountOfData() throws IOException {
        // Given: Large amount of weather data (100 records)
        List<Weather> largeDataSet = TestDataBuilder.createMultipleWeatherEntities(100, "City_");
        weatherRepository.saveAll(largeDataSet);

        assertEquals(100, weatherRepository.count());

        // When: User exports the data
        String response = given()
            .when()
                .post("/api/v1/weather/export")
            .then()
                .statusCode(200)
            .extract()
                .asString();

        // Then: Verify all records were exported
        assertTrue(response.contains("Successfully exported 100 weather records"));

        // And: Database should be empty
        assertEquals(0, weatherRepository.count());

        // And: File should contain all records
        List<Path> exportFiles = Files.list(tempExportDir)
            .filter(path -> path.getFileName().toString().startsWith("weather_export_"))
            .collect(Collectors.toList());

        Path exportFile = exportFiles.get(0);
        String content = Files.readString(exportFile);

        // Count data lines (excluding header)
        long dataLineCount = content.lines()
            .filter(line -> line.contains("City:"))
            .count();

        assertEquals(100, dataLineCount, "File should contain 100 weather records");
    }

    @Test
    void shouldPreserveDataIntegrityDuringExport() throws IOException {
        // Given: Weather data with special characters and edge cases
        Weather weatherWithSpecialChars = TestDataBuilder.createWeatherEntity("São Paulo", "-5.0");
        Weather weatherWithLongName = TestDataBuilder.createWeatherEntity(
            "VeryLongCityNameThatShouldStillBeHandledCorrectly", "99.9");

        weatherRepository.save(weatherWithSpecialChars);
        weatherRepository.save(weatherWithLongName);

        // When: User exports the data
        given()
            .when()
                .post("/api/v1/weather/export")
            .then()
                .statusCode(200);

        // Then: Verify file contains all data correctly
        List<Path> exportFiles = Files.list(tempExportDir)
            .filter(path -> path.getFileName().toString().startsWith("weather_export_"))
            .collect(Collectors.toList());

        Path exportFile = exportFiles.get(0);
        String content = Files.readString(exportFile);

        assertTrue(content.contains("São Paulo"), "Should preserve special characters");
        assertTrue(content.contains("-5.0"), "Should handle negative temperatures");
        assertTrue(content.contains("VeryLongCityNameThatShouldStillBeHandledCorrectly"),
            "Should handle long city names");
    }

    @Test
    void shouldHandleExportFileNamingWithTimestamp() throws IOException {
        // Given: Weather data exists
        weatherRepository.save(TestDataBuilder.createWeatherEntity("Warsaw", "25.5"));

        // When: User exports the data
        given()
            .when()
                .post("/api/v1/weather/export")
            .then()
                .statusCode(200);

        // Then: Verify filename format
        List<Path> exportFiles = Files.list(tempExportDir)
            .filter(path -> path.getFileName().toString().startsWith("weather_export_"))
            .collect(Collectors.toList());

        assertEquals(1, exportFiles.size());

        String fileName = exportFiles.get(0).getFileName().toString();

        // Verify format: weather_export_yyyy-MM-dd_HH-mm-ss.txt
        assertTrue(fileName.matches("weather_export_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}\\.txt"),
            "Filename should match pattern: weather_export_yyyy-MM-dd_HH-mm-ss.txt");
    }
}