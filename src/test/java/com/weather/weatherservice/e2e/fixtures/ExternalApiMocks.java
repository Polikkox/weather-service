package com.weather.weatherservice.e2e.fixtures;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

public class ExternalApiMocks {

    private final WireMockServer wireMockServer;

    public ExternalApiMocks(WireMockServer wireMockServer) {
        this.wireMockServer = wireMockServer;
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    public void mockSuccessfulWeatherResponse(String city, String temperature) {
        stubFor(get(urlPathEqualTo("/api/v1/weather"))
            .withQueryParam("city", equalTo(city))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                        "city": "%s",
                        "temperature": "%s",
                        "date": "2025-09-30"
                    }
                    """, city, temperature))));
    }

    public void mockWeatherApiError(String city, int statusCode) {
        stubFor(get(urlPathEqualTo("/api/v1/weather"))
            .withQueryParam("city", equalTo(city))
            .willReturn(aResponse()
                .withStatus(statusCode)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\": \"Weather API error\"}")));
    }

    public void mockWeatherApiTimeout(String city) {
        stubFor(get(urlPathEqualTo("/api/v1/weather"))
            .withQueryParam("city", equalTo(city))
            .willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(30000)
                .withBody("{}")));
    }

    public void verifyWeatherApiCalled(String city, int times) {
        verify(times, getRequestedFor(urlPathEqualTo("/api/v1/weather"))
            .withQueryParam("city", equalTo(city)));
    }

    public void resetAll() {
        wireMockServer.resetAll();
    }
}