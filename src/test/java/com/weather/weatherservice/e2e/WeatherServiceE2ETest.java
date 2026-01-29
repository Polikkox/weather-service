package com.weather.weatherservice.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.weather.weatherservice.e2e.fixtures.ExternalApiMocks;
import com.weather.weatherservice.e2e.fixtures.TestDataBuilder;
import weatherservice.entity.Weather;
import com.weather.weatherservice.model.WeatherResponseDto;
import weatherservice.repository.WeatherRepository;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    }
)
@ActiveProfiles("test")
class WeatherServiceE2ETest {

    private static WireMockServer wireMockServer;

    static {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("weather.api.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private WeatherRepository weatherRepository;

    private ExternalApiMocks externalApiMocks;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";

        externalApiMocks = new ExternalApiMocks(wireMockServer);
        externalApiMocks.resetAll();

        weatherRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        weatherRepository.deleteAll();
        externalApiMocks.resetAll();
    }

    @Test
    void shouldCompleteFullWeatherWorkflow() {
        // Given: External API is mocked
        externalApiMocks.mockSuccessfulWeatherResponse("Warsaw", "25.5");

        // When: User checks weather for Warsaw
        WeatherResponseDto weatherResponse = given()
                .queryParam("city", "Warsaw")
            .when()
                .get("/api/v1/weather")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("city", equalTo("Warsaw"))
                .body("temperature", equalTo("25.5"))
                .body("date", notNullValue())
            .extract()
                .as(WeatherResponseDto.class);

        // Then: Verify response
        assertNotNull(weatherResponse);
        assertEquals("Warsaw", weatherResponse.getCity());
        assertEquals("25.5", weatherResponse.getTemperature());

        // And: Verify data was saved to database
        List<Weather> savedWeather = weatherRepository.findAll();
        assertEquals(1, savedWeather.size());
        assertEquals("Warsaw", savedWeather.get(0).getCity());
        assertEquals("25.5", savedWeather.get(0).getTemperature());

        // And: Verify external API was called
        externalApiMocks.verifyWeatherApiCalled("Warsaw", 1);
    }

    @Test
    void shouldRetrieveWeatherHistoryAfterMultipleRequests() {
        // Given: External API mocks for multiple cities
        externalApiMocks.mockSuccessfulWeatherResponse("Warsaw", "25.5");
        externalApiMocks.mockSuccessfulWeatherResponse("Krakow", "22.0");
        externalApiMocks.mockSuccessfulWeatherResponse("Gdansk", "18.5");

        // When: User checks weather for multiple cities
        given().queryParam("city", "Warsaw").when().get("/api/v1/weather").then().statusCode(200);
        given().queryParam("city", "Krakow").when().get("/api/v1/weather").then().statusCode(200);
        given().queryParam("city", "Gdansk").when().get("/api/v1/weather").then().statusCode(200);

        // Then: User should see all weather history
        given()
            .when()
                .get("/api/v1/weather/history")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(3))
                .body("city", hasItems("Warsaw", "Krakow", "Gdansk"));
    }

    @Test
    void shouldFilterWeatherHistoryByCity() {
        // Given: Weather data exists for multiple cities
        weatherRepository.save(TestDataBuilder.createWeatherEntity("Warsaw", "25.5"));
        weatherRepository.save(TestDataBuilder.createWeatherEntity("Krakow", "22.0"));
        weatherRepository.save(TestDataBuilder.createWeatherEntity("Gdansk", "18.5"));

        // When: User requests history for specific city
        given()
                .queryParam("city", "Krakow")
            .when()
                .get("/api/v1/weather/history")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(1))
                .body("[0].city", equalTo("Krakow"))
                .body("[0].temperature", equalTo("22.0"));
    }

    @Test
    void shouldFilterWeatherHistoryByDateRange() {
        // Given: Weather data with different timestamps
        OffsetDateTime now = OffsetDateTime.now();
        weatherRepository.save(TestDataBuilder.createWeatherEntity("Warsaw", "25.5", now.minusDays(5)));
        weatherRepository.save(TestDataBuilder.createWeatherEntity("Krakow", "22.0", now.minusDays(3)));
        weatherRepository.save(TestDataBuilder.createWeatherEntity("Gdansk", "18.5", now.minusDays(1)));

        // When: User requests history for last 2 days
        OffsetDateTime fromDate = now.minusDays(2);
        OffsetDateTime toDate = now;

        given()
                .queryParam("fromDate", fromDate.toString())
                .queryParam("toDate", toDate.toString())
            .when()
                .get("/api/v1/weather/history")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(1))
                .body("[0].city", equalTo("Gdansk"));
    }

    @Test
    void shouldHandleExternalApiError() {
        // Given: External API returns error
        externalApiMocks.mockWeatherApiError("InvalidCity", 500);

        // When: User checks weather for invalid city
        // Then: Should handle error gracefully
        given()
                .queryParam("city", "InvalidCity")
            .when()
                .get("/api/v1/weather")
            .then()
                .statusCode(500);

        // And: No data should be saved to database
        List<Weather> savedWeather = weatherRepository.findAll();
        assertTrue(savedWeather.isEmpty());
    }

    @Test
    void shouldHandleInvalidRequestParameters() {
        // When: User provides empty city parameter
        given()
                .queryParam("city", "")
            .when()
                .get("/api/v1/weather")
            .then()
                .statusCode(400);

        // When: User provides no city parameter
        given()
            .when()
                .get("/api/v1/weather")
            .then()
                .statusCode(400);
    }

    @Test
    void shouldReturnEmptyHistoryWhenNoData() {
        // When: User requests history with no data in database
        given()
            .when()
                .get("/api/v1/weather/history")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(0));
    }
}