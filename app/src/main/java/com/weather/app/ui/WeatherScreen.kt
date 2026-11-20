package com.weather.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Air
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weather.app.data.DailyEntry
import com.weather.app.data.GeoResult
import com.weather.app.data.HourlyEntry
import com.weather.app.data.WeatherData
import com.weather.app.data.WeatherInfo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private val Glass = Color.White.copy(alpha = 0.14f)
private val GlassBorder = Color.White.copy(alpha = 0.22f)
private val TextPrimary = Color.White
private val TextSecondary = Color.White.copy(alpha = 0.75f)

@Composable
fun WeatherScreen(vm: WeatherViewModel, onRequestLocation: () -> Unit) {
    val state by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }

    // градиент зависит от текущей погоды; при загрузке — нейтральный ночной
    val gradient = when (val s = state.screen) {
        is ScreenState.Ready -> WeatherInfo.info(s.weather.code, s.weather.isDay).gradient
        else -> listOf(Color(0xFF0B1026), Color(0xFF203A72), Color(0xFF3C6CA8))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradient))
    ) {
        // декоративные «световые пятна»
        Box(
            Modifier
                .size(320.dp)
                .align(Alignment.TopEnd)
                .blur(90.dp)
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
        )
        Box(
            Modifier
                .size(240.dp)
                .align(Alignment.BottomStart)
                .blur(80.dp)
                .background(Color.Black.copy(alpha = 0.12f), CircleShape)
        )

        Column(Modifier.fillMaxSize()) {
            TopBar(
                searchOpen = searchOpen,
                query = query,
                onQueryChange = { q ->
                    query = q
                    vm.search(q)
                },
                onToggleSearch = {
                    searchOpen = !searchOpen
                    if (!searchOpen) {
                        query = ""
                        vm.clearSearch()
                    }
                },
                onRefresh = { vm.refresh() },
                onMyLocation = {
                    searchOpen = false
                    query = ""
                    vm.clearSearch()
                    onRequestLocation()
                }
            )

            AnimatedVisibility(visible = searchOpen && (query.isNotBlank() || state.searching)) {
                SearchResults(
                    results = state.searchResults,
                    loading = state.searching,
                    onSelect = {
                        searchOpen = false
                        query = ""
                        vm.selectCity(it)
                    }
                )
            }

            AnimatedVisibility(visible = !searchOpen && state.savedCities.size > 1) {
                SavedCitiesRow(state.savedCities) { name, coords -> vm.selectSaved(name, coords) }
            }

            Box(Modifier.fillMaxSize()) {
                when (val s = state.screen) {
                    is ScreenState.Loading -> LoadingView()
                    is ScreenState.Error -> ErrorView(s.message) { vm.refresh() }
                    is ScreenState.Ready -> WeatherContent(s.weather)
                }
            }
        }
    }
}

// ------------------------------ верхняя панель ------------------------------

@Composable
private fun TopBar(
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onRefresh: () -> Unit,
    onMyLocation: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (searchOpen) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp)),
                placeholder = { Text("Город…", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextSecondary) },
                trailingIcon = {
                    IconButton(onClick = onToggleSearch) {
                        Icon(Icons.Filled.Close, "Закрыть", tint = TextPrimary)
                    }
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Glass,
                    unfocusedContainerColor = Glass,
                    cursorColor = TextPrimary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        } else {
            Spacer(Modifier.weight(1f))
            GlassIconButton(onClick = onMyLocation, content = {
                Icon(Icons.Filled.AddLocationAlt, "Моё местоположение", tint = TextPrimary)
            })
            Spacer(Modifier.width(8.dp))
            GlassIconButton(onClick = onToggleSearch, content = {
                Icon(Icons.Filled.Search, "Поиск", tint = TextPrimary)
            })
            Spacer(Modifier.width(8.dp))
            GlassIconButton(onClick = onRefresh, content = {
                Icon(Icons.Filled.Refresh, "Обновить", tint = TextPrimary)
            })
        }
    }
}

@Composable
private fun GlassIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Glass,
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
        content = content
    )
}

// ------------------------------ поиск / города ------------------------------

@Composable
private fun SearchResults(results: List<GeoResult>, loading: Boolean, onSelect: (GeoResult) -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.10f),
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        Column {
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }
            } else if (results.isEmpty()) {
                Text(
                    "Ничего не найдено",
                    color = TextSecondary,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                results.forEach { r ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(r) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            val sub = listOf(r.admin1, r.country).filter { it.isNotBlank() }.joinToString(", ")
                            if (sub.isNotBlank()) Text(sub, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedCitiesRow(
    cities: List<Pair<String, Pair<Double, Double>>>,
    onSelect: (String, Pair<Double, Double>) -> Unit
) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        cities.forEach { (name, coords) ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Glass)
                    .border(androidx.compose.foundation.BorderStroke(1.dp, GlassBorder), RoundedCornerShape(50))
                    .clickable { onSelect(name, coords) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(name, color = TextPrimary, fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

// ------------------------------ состояния ------------------------------

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = TextPrimary, strokeWidth = 3.dp)
            Spacer(Modifier.height(16.dp))
            Text("Загружаем погоду…", color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("😔", fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            Text(message, color = TextPrimary, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Glass)
                    .border(androidx.compose.foundation.BorderStroke(1.dp, GlassBorder), RoundedCornerShape(50))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Text("Повторить", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ------------------------------ контент ------------------------------

@Composable
private fun WeatherContent(w: WeatherData) {
    val info = WeatherInfo.info(w.code, w.isDay)
    val appear = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appear.value = true }
    val alpha by animateFloatAsState(
        if (appear.value) 1f else 0f,
        animationSpec = tween(600),
        label = "alpha"
    )

    LazyColumn(
        Modifier
            .fillMaxSize()
            .alpha(alpha)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)
    ) {
        item { Hero(w, info.icon, info.description) }
        item { HourlyCard(w.hourly) }
        item { DailyCard(w.daily) }
        item { DetailsGrid(w) }
        item {
            Text(
                "Данные: Open-Meteo.com",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun Hero(w: WeatherData, icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            w.placeName + if (w.country.isNotBlank()) ", ${w.country}" else "",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(w.daily.firstOrNull()?.date?.time ?: Calendar.getInstance().time),
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(6.dp))
        Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(96.dp))
        Text(
            "${w.tempC.roundToInt()}°",
            color = TextPrimary,
            fontSize = 88.sp,
            fontWeight = FontWeight.Thin,
            lineHeight = 96.sp
        )
        Text(desc, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text(
            "Ощущается как ${w.feelsLikeC.roundToInt()}°   •   " +
                "↑${w.daily.firstOrNull()?.tempMax?.roundToInt()}° ↓${w.daily.firstOrNull()?.tempMin?.roundToInt()}°",
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun glassCard(content: @Composable () -> Unit) {
    Surface(
        color = Glass,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(Locale("ru")),
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun HourlyCard(hours: List<HourlyEntry>) {
    if (hours.isEmpty()) return
    glassCard {
        SectionTitle("Почасовой прогноз")
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .fillMaxWidth()
        ) {
            hours.forEachIndexed { i, h ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(58.dp)
                ) {
                    Text(
                        when (i) {
                            0 -> "Сейчас"
                            else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(h.time.time)
                        },
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Icon(
                        WeatherInfo.info(h.code, h.isDay).icon,
                        null,
                        tint = TextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    if (h.precipProb > 0) {
                        Text("${h.precipProb}%", color = Color(0xFF9FD8FF), fontSize = 10.sp)
                        Spacer(Modifier.height(2.dp))
                    } else {
                        Spacer(Modifier.height(12.dp))
                    }
                    Text("${h.tempC.roundToInt()}°", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun DailyCard(days: List<DailyEntry>) {
    if (days.isEmpty()) return
    val globalMin = days.minOf { it.tempMin }
    val globalMax = days.maxOf { it.tempMax }
    val span = (globalMax - globalMin).coerceAtLeast(1.0)
    val fmtDay = SimpleDateFormat("EEE", Locale("ru"))

    glassCard {
        SectionTitle("Прогноз на 7 дней")
        days.forEachIndexed { i, d ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (i == 0) "Сегодня" else fmtDay.format(d.date.time),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.width(72.dp)
                )
                Icon(
                    WeatherInfo.info(d.code, true).icon,
                    null,
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text("${d.tempMin.roundToInt()}°", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.width(34.dp))
                // температурная шкала
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                ) {
                    val startFrac = ((d.tempMin - globalMin) / span).toFloat().coerceIn(0f, 1f)
                    val widthFrac = ((d.tempMax - d.tempMin) / span).toFloat().coerceIn(0.08f, 1f)
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val trackWidthPx = with(density) { 200.dp.toPx() } // приблизительная ширина трека
                    Box(
                        Modifier
                            .offset(x = with(density) { (startFrac * trackWidthPx).toDp() })
                            .fillMaxWidth(widthFrac)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF7FC7FF), Color(0xFFFFD766))
                                )
                            )
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("${d.tempMax.roundToInt()}°", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(34.dp))
            }
        }
    }
}

@Composable
private fun DetailsGrid(w: WeatherData) {
    val sun = w.daily.firstOrNull()
    val uvFraction = (w.uvIndex / 11.0).toFloat().coerceIn(0f, 1f)

    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(1f)) {
                DetailTile("Ветер", "${w.windKmh.roundToInt()} км/ч", Icons.Filled.Air, null)
            }
            Box(Modifier.weight(1f)) {
                DetailTile("Влажность", "${w.humidity}%", Icons.Filled.WaterDrop, null)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(1f)) {
                DetailTile("Давление", if (w.pressureHpa > 0) "${w.pressureHpa} гПа" else "—", Icons.Filled.DarkMode, null)
            }
            Box(Modifier.weight(1f)) {
                DetailTile(
                    "УФ-индекс",
                    if (w.uvIndex > 0) w.uvIndex.roundToInt().toString() else "—",
                    Icons.Filled.Bolt,
                    uvFraction
                )
            }
        }
        if (sun?.sunrise != null && sun.sunset != null) {
            Spacer(Modifier.height(14.dp))
            glassCard {
                SectionTitle("Солнце")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Восход", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(sun.sunrise!!.time),
                            color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Закат", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(sun.sunset!!.time),
                            color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTile(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    sliderFraction: Float?
) {
    glassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(title.uppercase(Locale("ru")), color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Light)
        if (sliderFraction != null) {
            Slider(
                value = sliderFraction,
                onValueChange = {},
                enabled = false,
                colors = SliderDefaults.colors(
                    thumbColor = TextPrimary,
                    activeTrackColor = Color(0xFFFFD766),
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier.height(20.dp)
            )
        }
    }
}
