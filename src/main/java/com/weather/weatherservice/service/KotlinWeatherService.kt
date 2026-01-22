package com.weather.weatherservice.service

import com.weather.weatherservice.entity.KotlinWeather
import com.weather.weatherservice.model.WeatherResponseDto
import com.weather.weatherservice.repository.KotlinWeatherRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.OffsetDateTime

@Service
class KotlinWeatherService(
    private val weatherRepository: KotlinWeatherRepository,

    ) {

    private val restTemplate: RestTemplate = RestTemplate()

    @Value("\${weather.api.base-url:http://localhost:8081}")
    private val baseUrl: String = ""

    fun getWeatherHistory(city: String?, fromDate: OffsetDateTime?, toDate: OffsetDateTime?): List<WeatherResponseDto> {

        val weatherList = when {
            city != null && fromDate != null && toDate != null -> weatherRepository.findAllByCityAndDateTimeBetween(city, fromDate, toDate)
            city != null -> weatherRepository.findAllByCity(city)
            fromDate != null && toDate != null -> weatherRepository.findAllByDateTimeBetween(fromDate, toDate)
            else -> weatherRepository.findAllByOrderByDateTimeDesc()
        }
        return weatherList.map(::convertToDto)
    }


    private fun convertToDto(weather: KotlinWeather): WeatherResponseDto {
        return WeatherResponseDto().apply {
            city = weather.city
            temperature = weather.temperature
            date = weather.dateTime.toLocalDate()
        }
    }

    private fun saveWeatherResponse(response: WeatherResponseDto) {
        weatherRepository.save(KotlinWeather(response.city, response.temperature, OffsetDateTime.now()))
    }


    fun handleWeatherRequest(city: String?): WeatherResponseDto? {
        val url = UriComponentsBuilder.fromUriString("$baseUrl/api/v1/weather")
            .queryParam("city", city)
            .toUriString()

        return restTemplate.getForObject(url, WeatherResponseDto::class.java)?.also { saveWeatherResponse(it) }
    }

}
