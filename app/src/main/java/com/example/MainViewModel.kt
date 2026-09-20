package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat

data class DataUsageUiState(
    val isLoading: Boolean = false,
    val networkFilter: NetworkFilter = NetworkFilter.CELLULAR_SIM1,
    val billingCycle: String = "DAILY", // DAILY, WEEKLY, MONTHLY
    val planAllowanceBytes: Long = 6L * 1024L * 1024L * 1024L,
    val totalUsageBytes: Long = 0L,
    val unifiedReport: DataUsageReport? = null,
    val appUsageList: List<AppUsageInfo> = emptyList(),
    val hourlyTimeline: List<HourlyUsageBucket> = emptyList(),
    val historicalData: List<DateTotalProjection> = emptyList(),
    val liveSpeed: LiveSpeedInfo = LiveSpeedInfo(0L, 0L),
    val hasUsageAccess: Boolean = false,
    val hasPhoneStatePermission: Boolean = false,
    val isRolloverEnabled: Boolean = false,
    val selectedApp: AppUsageInfo? = null
) {
    val progress: Float
        get() = if (planAllowanceBytes > 0) (totalUsageBytes.toFloat() / planAllowanceBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dataUsageManager = DataUsageManager(application)
    private val database = AppDatabase.getDatabase(application)
    private val dao = database.appDataUsageDao()

    private val _uiState = MutableStateFlow(DataUsageUiState(isLoading = true))
    val uiState: StateFlow<DataUsageUiState> = _uiState.asStateFlow()

    init {
        checkPermissions()
        startLiveSpeedTracker()
        loadHistoricalData()
    }

    private fun checkPermissions() {
        _uiState.update { it.copy(
            hasUsageAccess = dataUsageManager.hasUsageAccessPermission(),
            hasPhoneStatePermission = dataUsageManager.hasPhoneStatePermission()
        )}
    }

    fun startLiveSpeedTracker() {
        viewModelScope.launch {
            while (true) {
                val speed = dataUsageManager.getLiveSpeed()
                _uiState.update { it.copy(liveSpeed = speed) }
                delay(1000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            checkPermissions()
            if (_uiState.value.hasUsageAccess) {
                loadUsage()
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateFilter(filter: NetworkFilter) {
        _uiState.update { it.copy(networkFilter = filter) }
        refresh()
    }

    fun updateBillingCycle(cycle: String) {
        _uiState.update { it.copy(billingCycle = cycle) }
        refresh()
    }

    private suspend fun loadUsage() = withContext(Dispatchers.IO) {
        val midnight = dataUsageManager.getStartOfDayMidnightMillis()
        val now = System.currentTimeMillis()
        
        val total = dataUsageManager.getTotalUsage(_uiState.value.networkFilter, midnight, now)
        val perApp = dataUsageManager.getPerAppUsage(_uiState.value.networkFilter, midnight, now)
        val unified = dataUsageManager.getUnifiedUsageReport(_uiState.value.networkFilter)
        val timeline = dataUsageManager.getHourlyUsageTimeline(_uiState.value.networkFilter)
        
        // Cache current app usage in Room for daily history
        val dateStr = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val entities = perApp.take(10).map { app ->
            AppDataUsageEntity(
                packageName = app.packageName,
                dateString = dateStr,
                foregroundBytes = app.foregroundBytes,
                backgroundBytes = app.backgroundBytes,
                totalBytes = app.totalBytes,
                networkType = _uiState.value.networkFilter.name
            )
        }
        dao.insertAll(entities)

        _uiState.update { it.copy(
            totalUsageBytes = total,
            appUsageList = perApp,
            unifiedReport = unified,
            hourlyTimeline = timeline,
            isLoading = false
        )}
    }

    fun selectApp(app: AppUsageInfo?) {
        _uiState.update { it.copy(selectedApp = app) }
    }

    private fun loadHistoricalData() {
        viewModelScope.launch {
            dao.getHistoricalTotals(7).collect { totals ->
                _uiState.update { it.copy(historicalData = totals) }
            }
        }
    }

    fun toggleRollover(enabled: Boolean) {
        _uiState.update { it.copy(isRolloverEnabled = enabled) }
    }
}
