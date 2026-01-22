package com.weather.weatherservice.controller

import com.weather.weatherservice.api.DefaultApiDelegate
import com.weather.weatherservice.model.WeatherResponseDto
import com.weather.weatherservice.service.KotlinExportService
import com.weather.weatherservice.service.KotlinWeatherService
import org.springframework.context.annotation.Primary
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import java.time.OffsetDateTime

@Controller
@Primary
class KotlinWeatherController(
    private val weatherService: KotlinWeatherService,
    private val exportService: KotlinExportService
) : DefaultApiDelegate {

    override fun checkWeather(city: String?): ResponseEntity<WeatherResponseDto> {
        if (city.isNullOrBlank()) {
            return ResponseEntity.badRequest().build()
        }
        return ResponseEntity.ok(weatherService.handleWeatherRequest(city))
    }

    override fun checkWeatherHistory(
        city: String,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromDate: OffsetDateTime,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) toDate: OffsetDateTime
    ): ResponseEntity<List<WeatherResponseDto>> {
        return ResponseEntity.ok(weatherService.getWeatherHistory(city, fromDate, toDate))
    }

    override fun exportWeatherData(): ResponseEntity<String> {
        return try {
            val result = exportService.exportAllWeatherData()
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            ResponseEntity.status(500).body("Export failed: ${e.message}")
        }
    }
}