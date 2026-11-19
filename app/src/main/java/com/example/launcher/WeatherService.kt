package com.example.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

enum class WeatherConditionKind {
    CLEAR_DAY,
    CLEAR_NIGHT,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    RAIN,
    SNOW,
    THUNDERSTORM
}

data class WeatherLocationOption(
    val displayName: String,
    val latitude: Double,
    val longitude: Double
)

data class WeatherForecastResult(
    val tempC: Double,
    val highC: Double?,
    val lowC: Double?,
    val weatherCode: Int,
    val isDay: Boolean
)

data class WeatherUiState(
    val locationName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val tempC: Double? = null,
    val highC: Double? = null,
    val lowC: Double? = null,
    val weatherCode: Int? = null,
    val isDay: Boolean = true,
    val useFahrenheit: Boolean = true,
    val shortcutApp: LauncherAppInfo? = null,
    val isLoading: Boolean = false,
    val updatedAt: Long = 0L
) {
    val hasWeatherData: Boolean
        get() = tempC != null && weatherCode != null

    val displayTemp: Int?
        get() = tempC?.let { WeatherService.convertTemp(it, useFahrenheit) }

    val displayHigh: Int?
        get() = highC?.let { WeatherService.convertTemp(it, useFahrenheit) }

    val displayLow: Int?
        get() = lowC?.let { WeatherService.convertTemp(it, useFahrenheit) }

    val unitSuffix: String
        get() = if (useFahrenheit) "°F" else "°C"

    val conditionLabel: String
        get() = weatherCode?.let { WeatherService.describeWeatherCode(it) }.orEmpty()

    val conditionKind: WeatherConditionKind
        get() = weatherCode?.let { WeatherService.mapConditionKind(it, isDay) }
            ?: WeatherConditionKind.PARTLY_CLOUDY
}

object WeatherService {

    private const val TAG = "WeatherService"
    private const val TIMEOUT_MS = 8_000

    private val knownWeatherKeywords = listOf(
        "weather",
        "accuweather",
        "carrot",
        "wunderground",
        "myradar",
        "windy",
        "clime",
        "noaa",
        "forecast",
        "breezy",
        "overdrop",
        "1weather",
        "shadowweather"
    )

    fun convertTemp(tempC: Double, useFahrenheit: Boolean): Int {
        return if (useFahrenheit) {
            ((tempC * 9.0 / 5.0) + 32.0).roundToInt()
        } else {
            tempC.roundToInt()
        }
    }

    fun describeWeatherCode(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly Clear"
        2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing Drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Showers"
        85, 86 -> "Snow Showers"
        95 -> "Thunderstorm"
        96, 99 -> "Severe Storm"
        else -> "Cloudy"
    }

    fun mapConditionKind(code: Int, isDay: Boolean): WeatherConditionKind = when (code) {
        0, 1 -> if (isDay) WeatherConditionKind.CLEAR_DAY else WeatherConditionKind.CLEAR_NIGHT
        2 -> WeatherConditionKind.PARTLY_CLOUDY
        3 -> WeatherConditionKind.CLOUDY
        45, 48 -> WeatherConditionKind.FOG
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherConditionKind.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherConditionKind.SNOW
        95, 96, 99 -> WeatherConditionKind.THUNDERSTORM
        else -> WeatherConditionKind.PARTLY_CLOUDY
    }

    fun prioritizeWeatherApps(apps: List<LauncherAppInfo>): List<LauncherAppInfo> {
        val (likelyWeather, others) = apps.partition { app ->
            val lowerLabel = app.label.lowercase(Locale.getDefault())
            val lowerPkg = app.packageName.lowercase(Locale.getDefault())
            knownWeatherKeywords.any { kw -> lowerLabel.contains(kw) || lowerPkg.contains(kw) }
        }
        return likelyWeather + others
    }

    fun hasCoarseLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun getDeviceCoarseLocation(context: Context): WeatherLocationOption? =
        withContext(Dispatchers.IO) {
            if (!hasCoarseLocationPermission(context)) return@withContext null
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@withContext null
            val providers = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
                LocationManager.GPS_PROVIDER
            )
            val best: Location? = providers
                .mapNotNull { provider ->
                    runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
                }
                .maxByOrNull { it.time }

            if (best == null) return@withContext null

            val resolvedName = resolveCityLabel(context, best.latitude, best.longitude)
                ?: String.format(Locale.US, "%.2f, %.2f", best.latitude, best.longitude)

            WeatherLocationOption(
                displayName = resolvedName,
                latitude = best.latitude,
                longitude = best.longitude
            )
        }

    @Suppress("DEPRECATION")
    private fun resolveCityLabel(context: Context, lat: Double, lon: Double): String? {
        return runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val addr = addresses?.firstOrNull() ?: return@runCatching null
            val locality = addr.locality ?: addr.subAdminArea ?: addr.adminArea
            val region = addr.adminArea?.takeIf { it != locality }
            listOfNotNull(locality, region).joinToString(", ").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    suspend fun searchCities(query: String): List<WeatherLocationOption> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val encoded = Uri.encode(trimmed)
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=6&language=en&format=json"
            val body = httpGet(url) ?: return@withContext emptyList()
            parseGeocodingJson(body)
        }

    fun parseGeocodingJson(jsonText: String): List<WeatherLocationOption> {
        return try {
            val root = JSONObject(jsonText)
            val results = root.optJSONArray("results") ?: return emptyList()
            val list = ArrayList<WeatherLocationOption>(results.length())
            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                if (name.isEmpty()) continue
                val admin1 = obj.optString("admin1").trim()
                val countryCode = obj.optString("country_code").trim()
                val lat = obj.optDouble("latitude")
                val lon = obj.optDouble("longitude")
                if (lat.isNaN() || lon.isNaN()) continue
                val parts = listOf(name, admin1, countryCode).filter { it.isNotEmpty() }.distinct()
                list.add(
                    WeatherLocationOption(
                        displayName = parts.joinToString(", "),
                        latitude = lat,
                        longitude = lon
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse geocoding response", e)
            emptyList()
        }
    }

    suspend fun fetchForecast(latitude: Double, longitude: Double): WeatherForecastResult? =
        withContext(Dispatchers.IO) {
            val url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
                latitude,
                longitude
            )
            val body = httpGet(url) ?: return@withContext null
            parseForecastJson(body)
        }

    fun parseForecastJson(jsonText: String): WeatherForecastResult? {
        return try {
            val root = JSONObject(jsonText)
            val current = root.optJSONObject("current") ?: return null
            val tempC = current.optDouble("temperature_2m")
            if (tempC.isNaN()) return null
            val code = current.optInt("weather_code", 0)
            val isDay = current.optInt("is_day", 1) == 1
            val daily = root.optJSONObject("daily")
            val highC = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.takeIf { !it.isNaN() }
            val lowC = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.takeIf { !it.isNaN() }
            WeatherForecastResult(
                tempC = tempC,
                highC = highC,
                lowC = lowC,
                weatherCode = code,
                isDay = isDay
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse weather forecast JSON", e)
            null
        }
    }

    private fun httpGet(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Nepho-Android")
            }
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "HTTP ${connection.responseCode} for $urlString")
                return null
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Weather request failed for $urlString", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
}
