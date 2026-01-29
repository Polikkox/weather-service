package com.weather.weatherservice.repository

import com.weather.weatherservice.entity.KotlinWeather
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface KotlinWeatherRepository : JpaRepository<KotlinWeather, Long> {

    fun findAllByCity(city: String): List<KotlinWeather>

    fun findAllByCityAndDateTimeBetween(city: String, fromDate: OffsetDateTime, toDate: OffsetDateTime): List<KotlinWeather>

    fun findAllByDateTimeBetween(fromDate: OffsetDateTime, toDate: OffsetDateTime): List<KotlinWeather>

    fun findAllByOrderByDateTimeDesc(): List<KotlinWeather>
}