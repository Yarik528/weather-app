package com.weather.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weather.app.data.GeoResult
import com.weather.app.data.WeatherData
import com.weather.app.data.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ScreenState {
    data object Loading : ScreenState
    data class Ready(val weather: WeatherData) : ScreenState
    data class Error(val message: String) : ScreenState
}

data class UiState(
    val screen: ScreenState = ScreenState.Loading,
    val searching: Boolean = false,
    val searchResults: List<GeoResult> = emptyList(),
    val savedCities: List<Pair<String, Pair<Double, Double>>> = emptyList()
)

class WeatherViewModel(
    private val appContext: Context,
    private val repo: WeatherRepository = WeatherRepository()
) : ViewModel() {

    private val prefs = appContext.getSharedPreferences("weather", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        val saved = loadCities()
        _state.value = _state.value.copy(savedCities = saved)
        val lastLat = prefs.getFloat("lat", 55.7558f).toDouble()   // Москва по умолчанию
        val lastLon = prefs.getFloat("lon", 37.6173f).toDouble()
        val name = prefs.getString("name", "Москва") ?: "Москва"
        val country = prefs.getString("country", "Россия") ?: "Россия"
        loadWeather(lastLat, lastLon, name, country)
    }

    fun refresh() {
        val w = (_state.value.screen as? ScreenState.Ready)?.weather ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(screen = ScreenState.Loading)
            runCatching {
                repo.fetchWeather(w.latitude, w.longitude, w.placeName, w.country)
            }.onSuccess {
                _state.value = _state.value.copy(screen = ScreenState.Ready(it))
            }.onFailure {
                _state.value = _state.value.copy(screen = ScreenState.Error(readableError(it)))
            }
        }
    }

    private fun loadWeather(lat: Double, lon: Double, name: String, country: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(screen = ScreenState.Loading)
            runCatching { repo.fetchWeather(lat, lon, name, country) }
                .onSuccess { _state.value = _state.value.copy(screen = ScreenState.Ready(it)) }
                .onFailure { _state.value = _state.value.copy(screen = ScreenState.Error(readableError(it))) }
        }
    }

    fun useMyLocation() {
        viewModelScope.launch {
            _state.value = _state.value.copy(screen = ScreenState.Loading)
            try {
                if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    throw SecurityException("Разрешение на геолокацию не выдано")
                }
                val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val loc = try {
                    lm.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                } catch (e: SecurityException) {
                    null
                } ?: throw Exception("Не удалось определить местоположение. Попробуйте поиск города.")

                val (city, country) = repo.reverseGeocode(loc.latitude, loc.longitude)
                savePlace(city, country, loc.latitude, loc.longitude)
                val w = repo.fetchWeather(loc.latitude, loc.longitude, city, country)
                _state.value = _state.value.copy(
                    screen = ScreenState.Ready(w),
                    savedCities = loadCities()
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(screen = ScreenState.Error(readableError(e)))
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _state.value = _state.value.copy(searchResults = emptyList(), searching = false)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(searching = true)
            runCatching { repo.searchCity(query.trim()) }
                .onSuccess {
                    _state.value = _state.value.copy(searchResults = it, searching = false)
                }
                .onFailure { _state.value = _state.value.copy(searching = false, searchResults = emptyList()) }
        }
    }

    fun clearSearch() {
        _state.value = _state.value.copy(searchResults = emptyList(), searching = false)
    }

    fun selectCity(result: GeoResult) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                screen = ScreenState.Loading,
                searchResults = emptyList(),
                searching = false
            )
            savePlace(result.name, result.country, result.latitude, result.longitude)
            runCatching {
                repo.fetchWeather(result.latitude, result.longitude, result.name, result.country)
            }.onSuccess {
                _state.value = _state.value.copy(
                    screen = ScreenState.Ready(it),
                    savedCities = loadCities()
                )
            }.onFailure {
                _state.value = _state.value.copy(screen = ScreenState.Error(readableError(it)))
            }
        }
    }

    fun selectSaved(name: String, coords: Pair<Double, Double>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(screen = ScreenState.Loading)
            runCatching { repo.fetchWeather(coords.first, coords.second, name, "") }
                .onSuccess {
                    _state.value = _state.value.copy(screen = ScreenState.Ready(it))
                }
                .onFailure {
                    _state.value = _state.value.copy(screen = ScreenState.Error(readableError(it)))
                }
        }
    }

    private fun savePlace(name: String, country: String, lat: Double, lon: Double) {
        prefs.edit()
            .putString("name", name)
            .putString("country", country)
            .putFloat("lat", lat.toFloat())
            .putFloat("lon", lon.toFloat())
            .apply()
        val cities = loadCities().toMutableList()
        cities.removeAll { it.first == name }
        cities.add(0, name to (lat to lon))
        while (cities.size > 8) cities.removeAt(cities.lastIndex)
        saveCities(cities)
    }

    private fun loadCities(): List<Pair<String, Pair<Double, Double>>> {
        val raw = prefs.getString("cities", "") ?: ""
        return raw.split(";").mapNotNull {
            val p = it.split(",")
            if (p.size == 3) {
                try {
                    Triple(p[0], p[1].toDouble(), p[2].toDouble()).let { t -> t.first to (t.second to t.third) }
                } catch (e: Exception) {
                    null
                }
            } else null
        }
    }

    private fun saveCities(cities: List<Pair<String, Pair<Double, Double>>>) {
        val raw = cities.joinToString(";") { "${it.first},${it.second.first},${it.second.second}" }
        prefs.edit().putString("cities", raw).apply()
    }

    private fun readableError(t: Throwable): String = when {
        t is SecurityException -> "Нет разрешения на определение местоположения"
        t.message?.contains("Unable to resolve host") == true ||
            t.message?.contains("Failed to connect") == true ||
            t is java.io.IOException -> "Нет соединения с интернетом"
        else -> t.message ?: "Не удалось загрузить погоду"
    }
}
