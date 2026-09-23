package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class BillingCycle {
    DAILY, WEEKLY, MONTHLY
}

enum class UnitPreference {
    MB_GB, BITS_BYTES
}

enum class AppThemePreference {
    SYSTEM, LIGHT, DARK
}

class DataTrackerPrefs(private val context: Context) {

    companion object {
        val BILLING_CYCLE = stringPreferencesKey("billing_cycle")
        val DATA_LIMIT = longPreferencesKey("data_limit")
        val ANCHOR_DAY = intPreferencesKey("anchor_day")
        val ROLLOVER_ENABLED = booleanPreferencesKey("rollover_enabled")

        // SIM & Wi-Fi Specific Limits
        val SIM1_DAILY_LIMIT = longPreferencesKey("sim1_daily_limit")
        val SIM1_MONTHLY_LIMIT = longPreferencesKey("sim1_monthly_limit")
        val SIM2_DAILY_LIMIT = longPreferencesKey("sim2_daily_limit")
        val SIM2_MONTHLY_LIMIT = longPreferencesKey("sim2_monthly_limit")
        val WIFI_DAILY_LIMIT = longPreferencesKey("wifi_daily_limit")
        val WIFI_MONTHLY_LIMIT = longPreferencesKey("wifi_monthly_limit")

        // App Preferences
        val UNIT_PREFERENCE = stringPreferencesKey("unit_preference")
        val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        
        // Notification Alert Tracking
        val LAST_80_ALERT_KEY = stringPreferencesKey("last_80_alert_key")
        val LAST_100_ALERT_KEY = stringPreferencesKey("last_100_alert_key")
    }

    val billingCycle: Flow<BillingCycle> = context.dataStore.data.map { preferences ->
        val name = preferences[BILLING_CYCLE] ?: BillingCycle.DAILY.name
        try { BillingCycle.valueOf(name) } catch (_: Exception) { BillingCycle.DAILY }
    }

    val dataLimit: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[DATA_LIMIT] ?: 2147483648L // Default 2GB
    }

    val anchorDay: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[ANCHOR_DAY] ?: 1
    }

    val rolloverEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ROLLOVER_ENABLED] ?: false
    }

    // Limits
    val sim1DailyLimit: Flow<Long> = context.dataStore.data.map { it[SIM1_DAILY_LIMIT] ?: 2147483648L } // 2 GB
    val sim1MonthlyLimit: Flow<Long> = context.dataStore.data.map { it[SIM1_MONTHLY_LIMIT] ?: 32212254720L } // 30 GB
    val sim2DailyLimit: Flow<Long> = context.dataStore.data.map { it[SIM2_DAILY_LIMIT] ?: 2147483648L }
    val sim2MonthlyLimit: Flow<Long> = context.dataStore.data.map { it[SIM2_MONTHLY_LIMIT] ?: 32212254720L }
    val wifiDailyLimit: Flow<Long> = context.dataStore.data.map { it[WIFI_DAILY_LIMIT] ?: 10737418240L } // 10 GB
    val wifiMonthlyLimit: Flow<Long> = context.dataStore.data.map { it[WIFI_MONTHLY_LIMIT] ?: 107374182400L } // 100 GB

    // Unit & Theme
    val unitPreference: Flow<UnitPreference> = context.dataStore.data.map { prefs ->
        val name = prefs[UNIT_PREFERENCE] ?: UnitPreference.MB_GB.name
        try { UnitPreference.valueOf(name) } catch (_: Exception) { UnitPreference.MB_GB }
    }

    val themePreference: Flow<AppThemePreference> = context.dataStore.data.map { prefs ->
        val name = prefs[THEME_PREFERENCE] ?: AppThemePreference.SYSTEM.name
        try { AppThemePreference.valueOf(name) } catch (_: Exception) { AppThemePreference.SYSTEM }
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }

    val last80AlertKey: Flow<String> = context.dataStore.data.map { it[LAST_80_ALERT_KEY] ?: "" }
    val last100AlertKey: Flow<String> = context.dataStore.data.map { it[LAST_100_ALERT_KEY] ?: "" }

    suspend fun setBillingCycle(cycle: BillingCycle) {
        context.dataStore.edit { it[BILLING_CYCLE] = cycle.name }
    }

    suspend fun setDataLimit(limit: Long) {
        context.dataStore.edit { it[DATA_LIMIT] = limit }
    }

    suspend fun setAnchorDay(day: Int) {
        context.dataStore.edit { it[ANCHOR_DAY] = day.coerceIn(1, 31) }
    }

    suspend fun setRolloverEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ROLLOVER_ENABLED] = enabled }
    }

    suspend fun setSim1DailyLimit(limit: Long) {
        context.dataStore.edit { it[SIM1_DAILY_LIMIT] = limit }
    }

    suspend fun setSim1MonthlyLimit(limit: Long) {
        context.dataStore.edit { it[SIM1_MONTHLY_LIMIT] = limit }
    }

    suspend fun setSim2DailyLimit(limit: Long) {
        context.dataStore.edit { it[SIM2_DAILY_LIMIT] = limit }
    }

    suspend fun setSim2MonthlyLimit(limit: Long) {
        context.dataStore.edit { it[SIM2_MONTHLY_LIMIT] = limit }
    }

    suspend fun setWifiDailyLimit(limit: Long) {
        context.dataStore.edit { it[WIFI_DAILY_LIMIT] = limit }
    }

    suspend fun setWifiMonthlyLimit(limit: Long) {
        context.dataStore.edit { it[WIFI_MONTHLY_LIMIT] = limit }
    }

    suspend fun setUnitPreference(pref: UnitPreference) {
        context.dataStore.edit { it[UNIT_PREFERENCE] = pref.name }
    }

    suspend fun setThemePreference(pref: AppThemePreference) {
        context.dataStore.edit { it[THEME_PREFERENCE] = pref.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setLast80AlertKey(key: String) {
        context.dataStore.edit { it[LAST_80_ALERT_KEY] = key }
    }

    suspend fun setLast100AlertKey(key: String) {
        context.dataStore.edit { it[LAST_100_ALERT_KEY] = key }
    }
}

