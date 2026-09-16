package com.example

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI State for Daily Mobile Data Tracker.
 */
data class DataUsageUiState(
    val isLoading: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val hasPhoneStatePermission: Boolean = false,
    val dataUsage: DailyMobileDataUsage = DailyMobileDataUsage(),
    val dailyLimitBytes: Long = 2L * 1024L * 1024L * 1024L, // Default 2.0 GB daily threshold
    val lastUpdatedMillis: Long = 0L,
    val errorMessage: String? = null
) {
    /**
     * Progress proportion relative to daily limit (clamped between 0f and 1f).
     */
    val usageProgress: Float
        get() = if (dailyLimitBytes > 0) {
            (dataUsage.totalBytes.toFloat() / dailyLimitBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    /**
     * Download percentage of total consumed data.
     */
    val downloadPercent: Int
        get() = if (dataUsage.totalBytes > 0) {
            ((dataUsage.rxBytes.toDouble() / dataUsage.totalBytes.toDouble()) * 100).toInt()
        } else {
            0
        }

    /**
     * Upload percentage of total consumed data.
     */
    val uploadPercent: Int
        get() = if (dataUsage.totalBytes > 0) {
            ((dataUsage.txBytes.toDouble() / dataUsage.totalBytes.toDouble()) * 100).toInt()
        } else {
            0
        }

    /**
     * Tethering percentage of total consumed data.
     */
    val tetheringPercent: Int
        get() = if (dataUsage.totalBytes > 0) {
            ((dataUsage.tetheringTotalBytes.toDouble() / dataUsage.totalBytes.toDouble()) * 100).toInt()
        } else {
            0
        }
}

/**
 * MainViewModel for handling permission checks, midnight timestamp logic, and exposing UI state.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dataUsageManager = DataUsageManager(application)

    private val _uiState = MutableStateFlow(DataUsageUiState(isLoading = true))
    val uiState: StateFlow<DataUsageUiState> = _uiState.asStateFlow()

    init {
        checkPermissionsAndLoad()
    }

    /**
     * Evaluates system permissions and initiates data loading if authorized.
     */
    fun checkPermissionsAndLoad() {
        viewModelScope.launch {
            val hasUsage = dataUsageManager.hasUsageAccessPermission()
            val hasPhoneState = dataUsageManager.hasPhoneStatePermission()

            _uiState.update { current ->
                current.copy(
                    hasUsageAccess = hasUsage,
                    hasPhoneStatePermission = hasPhoneState
                )
            }

            if (hasUsage) {
                loadUsageData()
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Queries NetworkStatsManager asynchronously on Dispatchers.IO.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val hasUsage = dataUsageManager.hasUsageAccessPermission()
            val hasPhoneState = dataUsageManager.hasPhoneStatePermission()

            if (!hasUsage) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasUsageAccess = false,
                        hasPhoneStatePermission = hasPhoneState
                    )
                }
                return@launch
            }

            loadUsageData()
        }
    }

    private suspend fun loadUsageData() {
        try {
            val usage = withContext(Dispatchers.IO) {
                dataUsageManager.getDailyMobileDataUsage()
            }
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    dataUsage = usage,
                    lastUpdatedMillis = System.currentTimeMillis(),
                    errorMessage = null
                )
            }
            // Notify home screen widget to refresh its views with latest stats
            DailyDataAppWidgetProvider.sendRefreshBroadcast(getApplication())
        } catch (e: Exception) {
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Failed to read network stats"
                )
            }
        }
    }

    /**
     * Updates daily data allowance limit for progress gauge calculations.
     */
    fun setDailyLimit(bytes: Long) {
        if (bytes > 0) {
            _uiState.update { it.copy(dailyLimitBytes = bytes) }
        }
    }

    /**
     * Provides navigation intent for Usage Access Settings.
     */
    fun getUsageAccessSettingsIntent(): Intent {
        return dataUsageManager.getUsageAccessSettingsIntent()
    }
}
