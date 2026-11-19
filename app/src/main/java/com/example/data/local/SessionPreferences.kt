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
}
