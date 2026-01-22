package com.weather.weatherservice.entity

import jakarta.persistence.*
import jakarta.persistence.GenerationType.IDENTITY
import java.time.OffsetDateTime


@Entity
@Table(name = "weather")
data class KotlinWeather(
    @Id
    @GeneratedValue(strategy = IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var city: String,

    @Column(nullable = false)
    var temperature: String,

    @Column(nullable = false)
    var dateTime: OffsetDateTime
) {
    constructor(city: String, temperature: String, dateTime: OffsetDateTime) : this(null, city, temperature, dateTime)
}