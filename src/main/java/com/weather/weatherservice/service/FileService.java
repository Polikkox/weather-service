package com.weather.weatherservice.service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.weather.weatherservice.model.WeatherResponseDto;

@Service
public class FileService {

    @Value("${weather.file.base-path:src/main/resources/weather-data}")
    private String basePath;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void exportAllWeatherToFile(List<WeatherResponseDto> weatherDataList) {
        try {
            // Allow system property to override for testing
            String path = System.getProperty("weather.file.base-path", basePath);
            Path baseDir = Paths.get(path);

            // Handle case where path exists as a file instead of directory
            if (Files.exists(baseDir) && !Files.isDirectory(baseDir)) {
                Files.delete(baseDir);
            }

            Files.createDirectories(baseDir);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path exportFile = baseDir.resolve("weather_export_" + timestamp + ".txt");

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(exportFile.toFile()))) {
                writer.write("Weather Data Export - Generated on: " + LocalDateTime.now().format(formatter) + "\n");
                writer.write("==========================================\n\n");

                for (WeatherResponseDto weatherData : weatherDataList) {
                    String line = String.format("City: %s, Temperature: %s, Date: %s%n",
                            weatherData.getCity(),
                            weatherData.getTemperature(),
                            weatherData.getDate());
                    writer.write(line);
                }

                writer.flush();
            }

            System.out.println("Weather data exported successfully to: " + exportFile.toString());

        } catch (IOException e) {
            System.err.println("Error exporting weather data to file: " + e.getMessage());
            throw new RuntimeException("Failed to export weather data", e);
        }
    }
}
