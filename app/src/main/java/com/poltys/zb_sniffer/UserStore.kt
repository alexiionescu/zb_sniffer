package com.poltys.zb_sniffer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserStore(private val context: Context) {
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("zb_sniffer_user_data")
        private val ZB_CHANNEL_KEY = intPreferencesKey("zb_channel_key")
        private val ZB_LQI_KEY = floatPreferencesKey("zb_lqi_key")
        private val ZB_MIN_KEY = intPreferencesKey("zb_min_key")

        const val DEFAULT_LQI = 60F
        const val DEFAULT_CHANNEL = 16
        const val DEFAULT_MIN = 10
    }

    val getChannel: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[ZB_CHANNEL_KEY] ?: DEFAULT_CHANNEL
    }

    suspend fun saveChannel(ch: Int) {
        context.dataStore.edit { preferences ->
            preferences[ZB_CHANNEL_KEY] = ch
        }
    }

    val getMinutes: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[ZB_MIN_KEY] ?: DEFAULT_MIN
    }

    suspend fun saveMinutes(min: Int) {
        context.dataStore.edit { preferences ->
            preferences[ZB_MIN_KEY] = min
        }
    }

    val getMinLQI: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[ZB_LQI_KEY] ?: DEFAULT_LQI
    }

    suspend fun saveMinLQI(lqi: Float) {
        context.dataStore.edit { preferences ->
            preferences[ZB_LQI_KEY] = lqi
        }
    }
}