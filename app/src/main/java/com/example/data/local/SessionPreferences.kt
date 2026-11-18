package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.pulseSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nepho_session_prefs"
)

class SessionPreferences(private val context: Context) {

    private object Keys {
        val SESSION_OPENED_AT = longPreferencesKey("session_opened_at")
        val BANNER_DISMISSED = booleanPreferencesKey("onboarding_banner_dismissed")
        val LOCAL_ALERTS_ENABLED = booleanPreferencesKey("local_alerts_enabled")
    }

    val sessionOpenedAtFlow: Flow<Long> = context.pulseSessionDataStore.data.map { prefs ->
        prefs[Keys.SESSION_OPENED_AT] ?: 0L
    }

    val bannerDismissedFlow: Flow<Boolean> = context.pulseSessionDataStore.data.map { prefs ->
        prefs[Keys.BANNER_DISMISSED] ?: false
    }

    val localAlertsEnabledFlow: Flow<Boolean> = context.pulseSessionDataStore.data.map { prefs ->
        prefs[Keys.LOCAL_ALERTS_ENABLED] ?: false
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
}
