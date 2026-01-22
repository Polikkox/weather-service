package com.weather.weatherservice.service

import com.weather.weatherservice.entity.KotlinWeather
import com.weather.weatherservice.model.WeatherResponseDto
import com.weather.weatherservice.repository.KotlinWeatherRepository
import org.springframework.stereotype.Service

@Service
class KotlinExportService(
    private val weatherRepository: KotlinWeatherRepository,
    private val fileService: KotlinFileService
) {

    fun exportAllWeatherData(): String {
        return try {
            val allWeatherRecords = weatherRepository.findAllByOrderByDateTimeDesc()

            val weatherDtos = allWeatherRecords.map {
                convertToDto(it)
            }

            fileService.exportAllWeatherToFile(weatherDtos)

            weatherRepository.deleteAll()
            "Successfully exported %d weather records and cleared database ${allWeatherRecords.size} records"
        } catch (e: Exception) {
            throw RuntimeException("Failed to export weather data: ${e.message}", e)
        }
    }

    private fun convertToDto(weather: KotlinWeather): WeatherResponseDto {
        return WeatherResponseDto().apply {
            this.city = weather.city
            this.temperature = weather.temperature
            this.date = weather.dateTime.toLocalDate()
        }
    }
}