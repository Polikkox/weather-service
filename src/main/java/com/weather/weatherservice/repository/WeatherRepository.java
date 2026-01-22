package com.weather.weatherservice.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.weather.weatherservice.entity.Weather;

@Repository
public interface WeatherRepository extends JpaRepository<Weather, Long> {

    List<Weather> findAllByCity(String city);

    List<Weather> findAllByCityAndDateTimeBetween(String city, OffsetDateTime fromDate, OffsetDateTime toDate);

    List<Weather> findAllByDateTimeBetween(OffsetDateTime fromDate, OffsetDateTime toDate);

    List<Weather> findAllByOrderByDateTimeDesc();
}