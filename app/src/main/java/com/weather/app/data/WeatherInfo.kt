package com.weather.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NotListedLocation

import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Маппинг WMO weather codes (используют Open-Meteo и Met.no)
 * на описание, иконку и градиент фона интерфейса.
 */
object WeatherInfo {

    data class Info(
        val description: String,
        val icon: ImageVector,
        val gradient: List<Color>
    )

    private val dayClear = listOf(Color(0xFF2F80ED), Color(0xFF56CCF2), Color(0xFF9BE15D))
    private val dayPartly = listOf(Color(0xFF2E6BB8), Color(0xFF5AA7E0), Color(0xFFA8D8EA))
    private val overcast = listOf(Color(0xFF4B5D67), Color(0xFF7B8D99), Color(0xFFAAB8C2))
    private val rain = listOf(Color(0xFF16324F), Color(0xFF2D5F8A), Color(0xFF4A7FA5))
    private val snow = listOf(Color(0xFF5C7A99), Color(0xFF8FB3D9), Color(0xFFCFE4F5))
    private val storm = listOf(Color(0xFF141E30), Color(0xFF243B55), Color(0xFF3A4E6E))
    private val nightClear = listOf(Color(0xFF0B1026), Color(0xFF203A72), Color(0xFF3C6CA8))
    private val nightPartly = listOf(Color(0xFF101A33), Color(0xFF27436E), Color(0xFF41668C))
    private val fog = listOf(Color(0xFF5D6D7E), Color(0xFF85929E), Color(0xFFBFC9CA))

    fun info(code: Int, isDay: Boolean): Info = when (code) {
        0 -> if (isDay) {
            Info("Ясно", Icons.Filled.WbSunny, dayClear) // солнце днём
        } else {
            Info("Ясно", Icons.Filled.NightsStay, nightClear)
        }
        1 -> if (isDay) {
            Info("Преимущественно ясно", Icons.Filled.WbSunny, dayClear)
        } else {
            Info("Преимущественно ясно", Icons.Filled.NightsStay, nightClear)
        }
        2 -> if (isDay) {
            Info("Переменная облачность", Icons.Filled.WbTwilight, dayPartly)
        } else {
            Info("Переменная облачность", Icons.Filled.WbTwilight, nightPartly)
        }
        3 -> Info("Пасмурно", Icons.Filled.Cloud, overcast)
        45, 48 -> Info("Туман", Icons.Filled.Cloud, fog)
        51 -> Info("Лёгкая морось", Icons.Filled.WaterDrop, rain)
        53 -> Info("Морось", Icons.Filled.WaterDrop, rain)
        55 -> Info("Сильная морось", Icons.Filled.WaterDrop, rain)
        56, 57 -> Info("Ледяная морось", Icons.Filled.WaterDrop, snow)
        61 -> Info("Небольшой дождь", Icons.Filled.CloudDownload, rain)
        63 -> Info("Дождь", Icons.Filled.CloudDownload, rain)
        65 -> Info("Сильный дождь", Icons.Filled.CloudDownload, rain)
        66, 67 -> Info("Ледяной дождь", Icons.Filled.CloudDownload, snow)
        71 -> Info("Небольшой снег", Icons.Filled.Grain, snow)
        73 -> Info("Снег", Icons.Filled.Grain, snow)
        75 -> Info("Сильный снегопад", Icons.Filled.Grain, snow)
        77 -> Info("Снежная крупа", Icons.Filled.Grain, snow)
        80 -> Info("Кратковременный дождь", Icons.Filled.CloudDownload, rain)
        81 -> Info("Ливень", Icons.Filled.CloudDownload, rain)
        82 -> Info("Сильный ливень", Icons.Filled.CloudDownload, rain)
        85, 86 -> Info("Снегопад", Icons.Filled.Grain, snow)
        95 -> Info("Гроза", Icons.Filled.Bolt, storm)
        96, 99 -> Info("Гроза с градом", Icons.Filled.Bolt, storm)
        else -> Info("Неизвестно", Icons.Filled.NotListedLocation, nightClear)
    }
}
