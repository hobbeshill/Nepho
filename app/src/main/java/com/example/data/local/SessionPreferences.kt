package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.ShadeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.Locale

data class StoredWeatherPrefs(
    val locationName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val useFahrenheit: Boolean = defaultUseFahrenheit(),
    val shortcutPackage: String? = null,
    val cachedTempC: Double? = null,
    val cachedHighC: Double? = null,
    val cachedLowC: Double? = null,
    val cachedWeatherCode: Int? = null,
    val cachedIsDay: Boolean = true,
    val cachedUpdatedAt: Long = 0L
) {
    companion object {
        private val fahrenheitCountries = setOf("US", "BS", "BZ", "KY", "PW")
        fun defaultUseFahrenheit(): Boolean = Locale.getDefault().country.uppercase() in fahrenheitCountries
    }
}

/** Which optional home screen sections the user wants rendered. */
data class HomeSectionPrefs(
    val showClock: Boolean = true,
    val showMusic: Boolean = true,
    val showCalendar: Boolean = true,
    val showAppDrawer: Boolean = true,
    val showShortcutDock: Boolean = true
)

private val Context.pulseSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nepho_session_prefs"
)

class SessionPreferences(private val context: Context) {

    private object Keys {
        val SESSION_OPENED_AT = longPreferencesKey("session_opened_at")
        val BANNER_DISMISSED = booleanPreferencesKey("onboarding_banner_dismissed")
        val LOCAL_ALERTS_ENABLED = booleanPreferencesKey("local_alerts_enabled")
        val PER_APP_SHADE_MODES = stringPreferencesKey("per_app_shade_modes_json")
        val DEFAULT_SHADE_MODE = stringPreferencesKey("default_shade_mode")
        val OBSERVED_APPS = stringPreferencesKey("observed_apps_json")
        val OPTED_OUT_APPS = stringPreferencesKey("opted_out_apps_json")
        val UPDATE_GITHUB_REPO = stringPreferencesKey("update_github_repo")
        val PINNED_SHORTCUTS_JSON = stringPreferencesKey("pinned_shortcuts_json")
        val SHORTCUTS_CUSTOMIZED = booleanPreferencesKey("shortcuts_customized")
        val WEATHER_LOCATION_NAME = stringPreferencesKey("weather_location_name")
        val WEATHER_LAT_LON = stringPreferencesKey("weather_lat_lon")
        val WEATHER_USE_FAHRENHEIT = booleanPreferencesKey("weather_use_fahrenheit")
        val WEATHER_SHORTCUT_PACKAGE = stringPreferencesKey("weather_shortcut_package")
        val WEATHER_CACHE_JSON = stringPreferencesKey("weather_cache_json")
        val PREFERRED_MUSIC_PACKAGE = stringPreferencesKey("preferred_music_package")
        val HOME_SHOW_CLOCK = booleanPreferencesKey("home_show_clock")
        val HOME_SHOW_MUSIC = booleanPreferencesKey("home_show_music")
        val HOME_SHOW_CALENDAR = booleanPreferencesKey("home_show_calendar")
        val HOME_SHOW_APP_DRAWER = booleanPreferencesKey("home_show_app_drawer")
        val HOME_SHOW_SHORTCUT_DOCK = booleanPreferencesKey("home_show_shortcut_dock")
    }

    val sessionOpenedAtFlow: Flow<Long> = context.pulseSessionDataStore.data.map { prefs ->
        prefs[Keys.SESSION_OPENED_AT] ?: 0L
    }.distinctUntilChanged()

    val bannerDismissedFlow: Flow<Boolean> = context.pulseSessionDataStore.data.map { prefs ->
        prefs[Keys.BANNER_DISMISSED] ?: false
    }

    val localAlertsEnabledFlow: Flow<Boolean> = context.pulseSessionDataStore.data.map { prefs ->
        prefs[Keys.LOCAL_ALERTS_ENABLED] ?: false
    }

    val defaultShadeModeFlow: Flow<ShadeMode> = context.pulseSessionDataStore.data.map { prefs ->
        parseShadeMode(prefs[Keys.DEFAULT_SHADE_MODE])
    }

    val perAppShadeModesFlow: Flow<Map<String, ShadeMode>> = context.pulseSessionDataStore.data.map { prefs ->
        decodeShadeModeMap(prefs[Keys.PER_APP_SHADE_MODES])
    }

    val observedAppsFlow: Flow<Map<String, String>> = context.pulseSessionDataStore.data.map { prefs ->
        decodeStringMap(prefs[Keys.OBSERVED_APPS])
    }

    val optedOutAppsFlow: Flow<Set<String>> = context.pulseSessionDataStore.data.map { prefs ->
        decodeStringSet(prefs[Keys.OPTED_OUT_APPS])
    }

    val updateGithubRepoFlow: Flow<String> = context.pulseSessionDataStore.data.map { prefs ->
        prefs[Keys.UPDATE_GITHUB_REPO].orEmpty()
    }.distinctUntilChanged()

    val pinnedShortcutsFlow: Flow<List<String>> = context.pulseSessionDataStore.data.map { prefs ->
        decodeStringList(prefs[Keys.PINNED_SHORTCUTS_JSON])
    }.distinctUntilChanged()

    val shortcutsCustomizedFlow: Flow<Boolean> = context.pulseSessionDataStore.data.map { prefs ->
        prefs[Keys.SHORTCUTS_CUSTOMIZED] ?: false
    }.distinctUntilChanged()

    val weatherPrefsFlow: Flow<StoredWeatherPrefs> = context.pulseSessionDataStore.data.map { prefs ->
        val latLonParts = prefs[Keys.WEATHER_LAT_LON]?.split(",")
        val lat = latLonParts?.getOrNull(0)?.toDoubleOrNull()
        val lon = latLonParts?.getOrNull(1)?.toDoubleOrNull()
        val cacheJson = prefs[Keys.WEATHER_CACHE_JSON]?.let { raw ->
            runCatching { JSONObject(raw) }.getOrNull()
        }
        StoredWeatherPrefs(
            locationName = prefs[Keys.WEATHER_LOCATION_NAME].orEmpty(),
            latitude = lat,
            longitude = lon,
            useFahrenheit = prefs[Keys.WEATHER_USE_FAHRENHEIT] ?: StoredWeatherPrefs.defaultUseFahrenheit(),
            shortcutPackage = prefs[Keys.WEATHER_SHORTCUT_PACKAGE]?.takeIf { it.isNotBlank() },
            cachedTempC = cacheJson?.optDouble("tempC")?.takeIf { !it.isNaN() },
            cachedHighC = cacheJson?.optDouble("highC")?.takeIf { !it.isNaN() },
            cachedLowC = cacheJson?.optDouble("lowC")?.takeIf { !it.isNaN() },
            cachedWeatherCode = if (cacheJson?.has("code") == true) cacheJson.optInt("code") else null,
            cachedIsDay = cacheJson?.optBoolean("isDay", true) ?: true,
            cachedUpdatedAt = cacheJson?.optLong("updatedAt", 0L) ?: 0L
        )
    }.distinctUntilChanged()

    val preferredMusicPackageFlow: Flow<String?> = context.pulseSessionDataStore.data.map { prefs ->
        prefs[Keys.PREFERRED_MUSIC_PACKAGE]?.takeIf { it.isNotBlank() }
    }.distinctUntilChanged()

    val homeSectionsFlow: Flow<HomeSectionPrefs> = context.pulseSessionDataStore.data.map { prefs ->
        HomeSectionPrefs(
            showClock = prefs[Keys.HOME_SHOW_CLOCK] ?: true,
            showMusic = prefs[Keys.HOME_SHOW_MUSIC] ?: true,
            showCalendar = prefs[Keys.HOME_SHOW_CALENDAR] ?: true,
            showAppDrawer = prefs[Keys.HOME_SHOW_APP_DRAWER] ?: true,
            showShortcutDock = prefs[Keys.HOME_SHOW_SHORTCUT_DOCK] ?: true
        )
    }.distinctUntilChanged()

    suspend fun setHomeSections(sections: HomeSectionPrefs) {
        context.pulseSessionDataStore.edit { prefs ->
            prefs[Keys.HOME_SHOW_CLOCK] = sections.showClock
            prefs[Keys.HOME_SHOW_MUSIC] = sections.showMusic
            prefs[Keys.HOME_SHOW_CALENDAR] = sections.showCalendar
            prefs[Keys.HOME_SHOW_APP_DRAWER] = sections.showAppDrawer
            prefs[Keys.HOME_SHOW_SHORTCUT_DOCK] = sections.showShortcutDock
        }
    }

    suspend fun ensureSessionOpenedAt(): Long {
        val current = context.pulseSessionDataStore.data.first()[Keys.SESSION_OPENED_AT]
        if (current != null && current > 0L) {
            return current
        }
        val now = System.currentTimeMillis()
        context.pulseSessionDataStore.edit { prefs ->
            prefs[Keys.SESSION_OPENED_AT] = now
        }
        return now
    }

    suspend fun resetSessionOpenedAt(now: Long = System.currentTimeMillis()): Long {
        context.pulseSessionDataStore.edit { prefs ->
            prefs[Keys.SESSION_OPENED_AT] = now
        }
        return now
    }

    suspend fun setBannerDismissed(dismissed: Boolean) {
        context.pulseSessionDataStore.edit { prefs ->
            prefs[Keys.BANNER_DISMISSED] = dismissed
        }
    }

    suspend fun setLocalAlertsEnabled(enabled: Boolean) {
        context.pulseSessionDataStore.edit { prefs ->
            prefs[Keys.LOCAL_ALERTS_ENABLED] = enabled
        }
    }

    suspend fun setDefaultShadeMode(mode: ShadeMode) {
        context.pulseSessionDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_SHADE_MODE] = mode.name
        }
    }

    suspend fun setAppShadeMode(packageName: String, mode: ShadeMode) {
        context.pulseSessionDataStore.edit { prefs ->
            val current = decodeShadeModeMap(prefs[Keys.PER_APP_SHADE_MODES]).toMutableMap()
            current[packageName] = mode
            prefs[Keys.PER_APP_SHADE_MODES] = encodeShadeModeMap(current)
        }
    }

    suspend fun setAppOptedIn(packageName: String, optedIn: Boolean) {
        context.pulseSessionDataStore.edit { prefs ->
            val current = decodeStringSet(prefs[Keys.OPTED_OUT_APPS]).toMutableSet()
            if (optedIn) {
                current.remove(packageName)
            } else {
                current.add(packageName)
            }
            prefs[Keys.OPTED_OUT_APPS] = encodeStringSet(current)
        }
    }

    suspend fun isAppOptedIn(packageName: String): Boolean {
        val prefs = context.pulseSessionDataStore.data.first()
        val optedOut = decodeStringSet(prefs[Keys.OPTED_OUT_APPS])
        return packageName !in optedOut
    }

    suspend fun recordObservedApp(packageName: String, appLabel: String) {
        if (packageName.isBlank() || appLabel.isBlank()) return
        context.pulseSessionDataStore.edit { prefs ->
            val current = decodeStringMap(prefs[Keys.OBSERVED_APPS]).toMutableMap()
            if (current[packageName] != appLabel) {
                current[packageName] = appLabel
                prefs[Keys.OBSERVED_APPS] = encodeStringMap(current)
            }
        }
    }

    suspend fun getEffectiveShadeMode(packageName: String): ShadeMode {
        val prefs = context.pulseSessionDataStore.data.first()
        val perApp = decodeShadeModeMap(prefs[Keys.PER_APP_SHADE_MODES])
        return perApp[packageName] ?: parseShadeMode(prefs[Keys.DEFAULT_SHADE_MODE])
    }

    suspend fun setUpdateGithubRepo(repoSlug: String) {
        context.pulseSessionDataStore.edit { prefs ->
            prefs[Keys.UPDATE_GITHUB_REPO] = repoSlug.trim()
        }
    }

    suspend fun setPinnedShortcuts(packages: List<String>, markCustomized: Boolean = true) {
        val cleaned = packages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        context.pulseSessionDataStore.edit { prefs ->
            prefs[Keys.PINNED_SHORTCUTS_JSON] = encodeStringList(cleaned)
            if (markCustomized) {
                prefs[Keys.SHORTCUTS_CUSTOMIZED] = true
            }
        }
    }

    suspend fun setWeatherLocation(name: String, latitude: Double, longitude: Double) {
        context.pulseSessionDataStore.edit { prefs ->
            prefs[Keys.WEATHER_LOCATION_NAME] = name.trim()
            prefs[Keys.WEATHER_LAT_LON] = "$latitude,$longitude"
        }
    }

    suspend fun setWeatherUseFahrenheit(useFahrenheit: Boolean) {
        context.pulseSessionDataStore.edit { prefs ->
            prefs[Keys.WEATHER_USE_FAHRENHEIT] = useFahrenheit
        }
    }

    suspend fun setWeatherShortcutPackage(packageName: String?) {
        context.pulseSessionDataStore.edit { prefs ->
            if (packageName.isNullOrBlank()) {
                prefs.remove(Keys.WEATHER_SHORTCUT_PACKAGE)
            } else {
                prefs[Keys.WEATHER_SHORTCUT_PACKAGE] = packageName.trim()
            }
        }
    }

    suspend fun setPreferredMusicPackage(packageName: String?) {
        context.pulseSessionDataStore.edit { prefs ->
            if (packageName.isNullOrBlank()) {
                prefs.remove(Keys.PREFERRED_MUSIC_PACKAGE)
            } else {
                prefs[Keys.PREFERRED_MUSIC_PACKAGE] = packageName.trim()
            }
        }
    }

    suspend fun saveWeatherSnapshot(
        tempC: Double,
        highC: Double?,
        lowC: Double?,
        weatherCode: Int,
        isDay: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val json = JSONObject().apply {
            put("tempC", tempC)
            if (highC != null) put("highC", highC)
            if (lowC != null) put("lowC", lowC)
            put("code", weatherCode)
            put("isDay", isDay)
            put("updatedAt", updatedAt)
        }
        context.pulseSessionDataStore.edit { prefs ->
            prefs[Keys.WEATHER_CACHE_JSON] = json.toString()
        }
    }

    private fun parseShadeMode(raw: String?): ShadeMode {
        return raw?.let { runCatching { ShadeMode.valueOf(it) }.getOrNull() } ?: ShadeMode.SILENT
    }

    private fun encodeShadeModeMap(map: Map<String, ShadeMode>): String {
        val json = JSONObject()
        map.forEach { (pkg, mode) -> json.put(pkg, mode.name) }
        return json.toString()
    }

    private fun decodeShadeModeMap(raw: String?): Map<String, ShadeMode> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val json = JSONObject(raw)
            val result = LinkedHashMap<String, ShadeMode>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                result[pkg] = parseShadeMode(json.optString(pkg))
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun encodeStringMap(map: Map<String, String>): String {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        return json.toString()
    }

    private fun decodeStringMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val json = JSONObject(raw)
            val result = LinkedHashMap<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = json.optString(k)
                if (v.isNotBlank()) result[k] = v
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun encodeStringSet(items: Set<String>): String {
        val array = org.json.JSONArray()
        items.forEach { array.put(it) }
        return array.toString()
    }

    private fun decodeStringSet(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return try {
            val array = org.json.JSONArray(raw)
            val result = LinkedHashSet<String>()
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (value.isNotBlank()) result.add(value)
            }
            result
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun encodeStringList(items: List<String>): String {
        val array = org.json.JSONArray()
        items.forEach { array.put(it) }
        return array.toString()
    }

    private fun decodeStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = org.json.JSONArray(raw)
            val result = ArrayList<String>(array.length())
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (value.isNotBlank()) result.add(value)
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }
}
