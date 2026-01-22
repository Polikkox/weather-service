package com.weather.weatherservice.service

import com.weather.weatherservice.model.WeatherResponseDto
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

@Service
class KotlinFileService {

    @Value("\${weather.file.base-path:src/main/resources/weather-data}")
    private val basePath = ""
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")


    fun exportAllWeatherToFile(weatherDataList: List<WeatherResponseDto>) {

        try {
            // Allow system property to override for testing
            val path = System.getProperty("weather.file.base-path", basePath)
            val baseDir = Paths.get(path)

            // Handle case where path exists as a file instead of directory
            if (baseDir.exists() && !baseDir.isDirectory()) {
                baseDir.deleteIfExists()
            }

            baseDir.createDirectories()

            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            val exportFile = baseDir.resolve("weather_export_$timestamp.txt")

            exportFile.toFile().bufferedWriter().use { writer ->
                writer.write("Weather Data Export - Generated on: ${LocalDateTime.now().format(formatter)}\n")
                writer.write("==========================================\n\n")

                weatherDataList.forEach { weatherData ->
                    writer.write("City: ${weatherData.city}, Temperature: ${weatherData.temperature}, Date: ${weatherData.date}\n")
                }

            }
            println("Weather data exported successfully to: $exportFile")
        } catch (e: IOException) {
            println("Error exporting weather data to file: ${e.message}")
            throw RuntimeException("Error exporting weather data to file: $e")
        }
    }
}