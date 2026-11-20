package com.weather.app.data

import java.util.Calendar

/** Погода по часам (Open-Meteo hourly). */
data class HourlyEntry(
    val time: Calendar,
    val tempC: Double,
    val code: Int,
    val isDay: Boolean,
    val precipProb: Int
)

/** Погода по дням (Open-Meteo daily). */
data class DailyEntry(
    val date: Calendar,
    val tempMax: Double,
    val tempMin: Double,
    val code: Int,
    val sunrise: Calendar?,
    val sunset: Calendar?
)

/** Полный ответ погоды для одного места. */
data class WeatherData(
    val placeName: String,
    val country: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val tempC: Double,
    val feelsLikeC: Double,
    val code: Int,
    val isDay: Boolean,
    val windKmh: Double,
    val humidity: Int,
    val pressureHpa: Int,
    val uvIndex: Double,
    val visibilityKm: Double,
    val precipitationMm: Double,
    val hourly: List<HourlyEntry>,
    val daily: List<DailyEntry>
)

/** Результат геокодирования названия города. */
data class GeoResult(
    val name: String,
    val country: String,
    val admin1: String,
    val latitude: Double,
    val longitude: Double
)
