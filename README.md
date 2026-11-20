# ☁️ Погода — Weather App for Android

Красивое погодное приложение на **Android (Jetpack Compose, Material 3)** с прогнозом погоды.
Без рекламы, без аккаунтов, **без API-ключа** — использует открытые API [Open-Meteo](https://open-meteo.com)
(запасной источник — [MET Norway](https://api.met.no)).

## Возможности
- 🌇 Динамический градиентный интерфейс: фон меняется в зависимости от погоды и времени суток
- 📍 Погода по геолокации (с запросом разрешения) или поиск любого города мира (включая кириллицу)
- 🕐 Почасовой прогноз на 24 часа с вероятностью осадков
- 📅 Прогноз на 7 дней с температурными шкалами min/max
- 💨 Детали: ощущается как, ветер, влажность, давление, УФ-индекс, восход/закат
- ⭐ Сохранённые города (быстрые чипы сверху), кнопка обновления
- 🔋 Лёгкая сборка: Compose + OkHttp, без тяжёлых библиотек

## Скриншоты интерфейса
Тёмная ночная тема при ясной ночи, голубая — днём, серая — пасмурно, синяя — дождь, фиолетово-штормовая — гроза.

## Сборка из исходников
```bash
git clone https://github.com/<you>/weather-app.git
cd weather-app
python3 scripts/gen_icons.py     # генерирует PNG-иконки лаунчера
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK (подписан debug-ключом, устанавливается сразу)
```
Требуется JDK 17 и Android SDK 34 (или просто GitHub Actions — см. ниже).

## Релизы
Каждый тег `v*` собирает APK в GitHub Actions (`.github/workflows/android.yml`)
и публикует его в [Releases](../../releases). Скачайте `weather-app-vX.Y.Z.apk`
и установите на устройство (разрешите установку из неизвестных источников).

> Примечание: релизный APK подписан debug-ключом для простоты распространения через GitHub Releases.
> Для публикации в Google Play подключите собственный keystore в `app/build.gradle.kts`.

## Технологии
| | |
|---|---|
| UI | Jetpack Compose, Material 3, glassmorphism-карточки, анимации |
| Архитектура | MVVM (ViewModel + StateFlow) |
| Сеть | OkHttp, JSON (org.json) |
| Погода | Open-Meteo forecast + geocoding API, fallback MET Norway, обратное геокодирование BigDataCloud |
| CI/CD | GitHub Actions → artifact + GitHub Release |

## Лицензия
MIT. Погодные данные предоставлены [Open-Meteo.com](https://open-meteo.com) (CC BY 4.0).
