package com.weather.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Погодный репозиторий без API-ключа:
 *  - основной источник: Open-Meteo (https://open-meteo.com)
 *  - запасной источник: MET Norway / api.met.no (WMO-коды)
 *  - геокодирование: Open-Meteo Geocoding + обратное — BigDataCloud
 */
class WeatherRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    class WeatherException(message: String) : Exception(message)

    // ------------------------- публичные методы -------------------------

    suspend fun searchCity(query: String): List<GeoResult> = withContext(Dispatchers.IO) {
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=${enc(query)}&count=8&language=ru&format=json"
        val obj = JSONObject(get(url))
        val results = mutableListOf<GeoResult>()
        val arr = obj.optJSONArray("results") ?: return@withContext results
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            results += GeoResult(
                name = r.optString("name"),
                country = r.optString("country", ""),
                admin1 = r.optString("admin1", ""),
                latitude = r.getDouble("latitude"),
                longitude = r.getDouble("longitude")
            )
        }
        results
    }

    /** Обратное геокодирование по координатам; при ошибке — «Моё местоположение». */
    suspend fun reverseGeocode(lat: Double, lon: Double): Pair<String, String> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.bigdatacloud.net/data/reverse-geocode-client" +
                    "?latitude=$lat&longitude=$lon&localityLanguage=ru"
                val obj = JSONObject(get(url))
                val city = listOf(
                    obj.optString("city"),
                    obj.optString("locality"),
                    obj.optString("principalSubdivision")
                ).firstOrNull { it.isNotBlank() } ?: "Моё местоположение"
                city to obj.optString("countryName", "")
            } catch (e: Exception) {
                "Моё местоположение" to ""
            }
        }

    suspend fun fetchWeather(
        lat: Double,
        lon: Double,
        placeName: String,
        country: String
    ): WeatherData = withContext(Dispatchers.IO) {
        try {
            fetchOpenMeteo(lat, lon, placeName, country)
        } catch (e: Exception) {
            fetchMetNo(lat, lon, placeName, country)
        }
    }

    // ------------------------- Open-Meteo -------------------------

    private fun fetchOpenMeteo(
        lat: Double, lon: Double, placeName: String, country: String
    ): WeatherData {
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon")
            append("&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,")
            append("precipitation,weather_code,pressure_msl,wind_speed_10m,visibility")
            append("&hourly=temperature_2m,weather_code,is_day,precipitation_probability")
            append("&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max")
            append("&timezone=auto&forecast_days=7")
        }
        val root = JSONObject(get(url))
        val tz = TimeZone.getTimeZone(root.getString("timezone"))

        val current = root.getJSONObject("current")
        val hourly = parseHourly(root.getJSONObject("hourly"), tz)
        val daily = parseDaily(root.getJSONObject("daily"), tz)

        return WeatherData(
            placeName = placeName,
            country = country,
            latitude = lat,
            longitude = lon,
            tempC = current.getDouble("temperature_2m"),
            feelsLikeC = current.getDouble("apparent_temperature"),
            code = current.getInt("weather_code"),
            isDay = current.getInt("is_day") == 1,
            windKmh = current.getDouble("wind_speed_10m"),
            humidity = current.getInt("relative_humidity_2m"),
            pressureHpa = (current.getDouble("pressure_msl")).toInt(),
            uvIndex = root.getJSONObject("daily").optJSONArray("uv_index_max")
                ?.optDouble(0) ?: 0.0,
            visibilityKm = current.optDouble("visibility", 0.0) / 1000.0,
            precipitationMm = current.getDouble("precipitation"),
            hourly = hourly,
            daily = daily
        )
    }

    private fun parseHourly(h: JSONObject, tz: TimeZone): List<HourlyEntry> {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = tz }
        val times = h.getJSONArray("time")
        val temps = h.getJSONArray("temperature_2m")
        val codes = h.getJSONArray("weather_code")
        val days = h.getJSONArray("is_day")
        val probs = h.optJSONArray("precipitation_probability")

        val now = System.currentTimeMillis()
        val out = mutableListOf<HourlyEntry>()
        var i = 0
        // найти ближайший прошедший час
        while (i < times.length() && fmt.parse(times.getString(i))!!.time < now - 3600_000) i++
        if (i > 0) i--
        while (i < times.length() && out.size < 24) {
            out += HourlyEntry(
                time = Calendar.getInstance(tz).apply { timeInMillis = fmt.parse(times.getString(i))!!.time },
                tempC = temps.getDouble(i),
                code = codes.getInt(i),
                isDay = days.getInt(i) == 1,
                precipProb = probs?.optInt(i, 0) ?: 0
            )
            i++
        }
        return out
    }

    private fun parseDaily(d: JSONObject, tz: TimeZone): List<DailyEntry> {
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
        val dtFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = tz }
        val times = d.getJSONArray("time")
        val codes = d.getJSONArray("weather_code")
        val maxs = d.getJSONArray("temperature_2m_max")
        val mins = d.getJSONArray("temperature_2m_min")
        val sunrises = d.optJSONArray("sunrise")
        val sunsets = d.optJSONArray("sunset")

        val out = mutableListOf<DailyEntry>()
        for (i in 0 until times.length()) {
            out += DailyEntry(
                date = Calendar.getInstance(tz).apply { timeInMillis = dayFmt.parse(times.getString(i))!!.time },
                tempMax = maxs.getDouble(i),
                tempMin = mins.getDouble(i),
                code = codes.getInt(i),
                sunrise = sunrises?.optString(i)?.takeIf { it.isNotBlank() }
                    ?.let { Calendar.getInstance(tz).apply { timeInMillis = dtFmt.parse(it)!!.time } },
                sunset = sunsets?.optString(i)?.takeIf { it.isNotBlank() }
                    ?.let { Calendar.getInstance(tz).apply { timeInMillis = dtFmt.parse(it)!!.time } }
            )
        }
        return out
    }

    // ------------------------- запасной: api.met.no -------------------------

    private fun fetchMetNo(
        lat: Double, lon: Double, placeName: String, country: String
    ): WeatherData {
        val url = "https://api.met.no/weatherapi/locationforecast/2.0/compact" +
            "?lat=%.4f&lon=%.4f".format(lat, lon)
        val root = JSONObject(get(url, "weather-app-android/1.0 github.com"))
        val ts = root.getJSONObject("properties").getJSONArray("timeseries")
        if (ts.length() == 0) throw WeatherException("met.no: пустой ответ")

        val first = ts.getJSONObject(0).getJSONObject("data").getJSONObject("instant").getJSONObject("details")
        val next1h = ts.optJSONObject(1)?.getJSONObject("data")
            ?.getJSONObject("next_1_hours")?.getJSONObject("summary")

        val code = wmoFromSymbol(next1h?.optString("symbol_code")
            ?: ts.getJSONObject(0).getJSONObject("data")
                .getJSONObject("next_6_hours").getJSONObject("summary")
                .optString("symbol_code"))

        val hourly = mutableListOf<HourlyEntry>()
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
        var i = 0
        while (i < ts.length() && hourly.size < 24) {
            val item = ts.getJSONObject(i)
            val data = item.getJSONObject("data")
            val det = data.getJSONObject("instant").getJSONObject("details")
            val sum = data.optJSONObject("next_1_hours")?.getJSONObject("summary")
                ?: data.optJSONObject("next_6_hours")?.getJSONObject("summary")
            hourly += HourlyEntry(
                time = Calendar.getInstance().apply { timeInMillis = iso.parse(item.getString("time"))!!.time },
                tempC = det.getDouble("air_temperature"),
                code = wmoFromSymbol(sum?.optString("symbol_code") ?: ""),
                isDay = (sum?.optString("symbol_code") ?: "").contains("day"),
                precipProb = (((sum?.optJSONObject("details")
                    ?.optDouble("probability_of_precipitation", -1.0)
                    ?: -1.0).takeIf { it >= 0 }
                    ?: (det.optDouble("precipitation_amount", 0.0) * 20)).coerceAtMost(100.0)).toInt()
            )
            i++
        }

        // группировка по дням
        val daily = hourly.groupBy { ymd(it.time) }.map { (day, list) ->
            val c = Calendar.getInstance().apply {
                timeInMillis = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day)!!.time
            }
            DailyEntry(
                date = c,
                tempMax = list.maxOf { it.tempC },
                tempMin = list.minOf { it.tempC },
                code = list.first().code,
                sunrise = null,
                sunset = null
            )
        }

        val hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isDayNow = hourNow in 6..20

        return WeatherData(
            placeName = placeName,
            country = country,
            latitude = lat,
            longitude = lon,
            tempC = first.getDouble("air_temperature"),
            feelsLikeC = first.optDouble("wind_from_direction", first.getDouble("air_temperature")),
            code = code,
            isDay = isDayNow,
            windKmh = first.optDouble("wind_speed", 0.0) * 3.6,
            humidity = first.optInt("relative_humidity", 0),
            pressureHpa = first.optInt("air_pressure_at_sea_level", 0),
            uvIndex = 0.0,
            visibilityKm = first.optDouble("fog_area_fraction", 0.0),
            precipitationMm = next1h?.let { 0.0 } ?: 0.0,
            hourly = hourly,
            daily = daily
        )
    }

    private fun ymd(c: Calendar): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)

    /** Перевод symbol_code мет.но в WMO-код. */
    private fun wmoFromSymbol(s: String): Int {
        val core = s.removeSuffix("_night").removeSuffix("_day").removeSuffix("_polartwilight")
        val heavy = core.contains("heavy")
        val thunder = core.startsWith("thunder") || core.contains("thunder")
        return when {
            thunder -> if (core.contains("hail") || core.contains("thunderstorm")) 95 else 95
            core == "clearsky" -> 0
            core == "fair" -> 1
            core == "partlycloudy" -> 2
            core == "cloudy" -> 3
            core == "fog" || core == "sleet" && !heavy -> 45
            core.contains("drizzle") -> if (heavy) 55 else 51
            core.contains("rain") && core.contains("snow") -> if (heavy) 67 else 66
            core.contains("sleet") -> if (heavy) 67 else 66
            core.startsWith("snow") -> if (heavy) 75 else 71
            core.startsWith("rain") -> when {
                core.startsWith("lightrain") -> 61
                heavy -> 65
                else -> 63
            }
            else -> 3
        }
    }

    // ------------------------- HTTP -------------------------

    private fun get(url: String, ua: String = "WeatherApp/1.0"): String {
        val request = Request.Builder().url(url).header("User-Agent", ua).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw WeatherException("HTTP ${resp.code}")
            return resp.body?.string() ?: throw WeatherException("Пустой ответ")
        }
    }

    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")
}
