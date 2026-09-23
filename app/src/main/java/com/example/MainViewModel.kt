package com.example

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppThemePreference
import com.example.data.DataTrackerPrefs
import com.example.data.UnitPreference
import com.example.notification.DataTrackerNotificationManager
import com.example.widget.WidgetReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class TimePeriodFilter {
    TODAY, THIS_WEEK, THIS_MONTH, CUSTOM_RANGE
}

enum class AppSortOption {
    TOTAL_DATA, BACKGROUND_DATA, APP_NAME
}

/**
 * UI State for Data Tracker.
 */
data class DataUsageUiState(
    val isLoading: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val hasPhoneStatePermission: Boolean = false,
    val selectedPeriod: TimePeriodFilter = TimePeriodFilter.TODAY,
    val customStartMs: Long = 0L,
    val customEndMs: Long = 0L,
    val selectedNetworkType: Int = ConnectivityManager.TYPE_MOBILE,
    val activeSubscriberId: String? = null,

    val dataUsage: DailyMobileDataUsage = DailyMobileDataUsage(),
    val appUsageList: List<AppUsageInfo> = emptyList(),
    val historyList: List<Pair<Long, Long>> = emptyList(),
    val selectedApp: AppUsageInfo? = null,
    val selectedAppHourlyUsage: List<Pair<Int, Long>> = emptyList(),

    // Limits & Settings
    val sim1DailyLimit: Long = 2147483648L,
    val sim1MonthlyLimit: Long = 32212254720L,
    val sim2DailyLimit: Long = 2147483648L,
    val sim2MonthlyLimit: Long = 32212254720L,
    val wifiDailyLimit: Long = 10737418240L,
    val wifiMonthlyLimit: Long = 107374182400L,
    val activeLimit: Long = 2147483648L,
    val anchorDay: Int = 1,
    val unitPreference: UnitPreference = UnitPreference.MB_GB,
    val themePreference: AppThemePreference = AppThemePreference.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val rolloverEnabled: Boolean = true,

    // Rollover Audit Pool
    val accumulatedRolloverBytes: Long = 0L,
    val rolloverMessage: String = "",

    // Filter Controls
    val appSearchQuery: String = "",
    val appSortOption: AppSortOption = AppSortOption.TOTAL_DATA,

    // Smart Forecast Card
    val paceEndString: String = "",
    val remainingTodayString: String = "",

    val lastUpdatedMillis: Long = 0L,
    val errorMessage: String? = null
) {
    val filteredAppUsageList: List<AppUsageInfo>
        get() {
            var list = appUsageList
            if (appSearchQuery.isNotBlank()) {
                list = list.filter {
                    it.appName.contains(appSearchQuery, ignoreCase = true) ||
                            it.packageName.contains(appSearchQuery, ignoreCase = true)
                }
            }
            return when (appSortOption) {
                AppSortOption.TOTAL_DATA -> list.sortedByDescending { it.totalBytes }
                AppSortOption.BACKGROUND_DATA -> list.sortedByDescending { it.backgroundBytes }
                AppSortOption.APP_NAME -> list.sortedBy { it.appName.lowercase() }
            }
        }
}

/**
 * MainViewModel handling usage statistics, preferences, notifications, and navigation state.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dataUsageManager = DataUsageManager(application)
    private val prefs = DataTrackerPrefs(application)
    private val notificationManager = DataTrackerNotificationManager(application)

    private val _uiState = MutableStateFlow(DataUsageUiState(isLoading = true))
    val uiState: StateFlow<DataUsageUiState> = _uiState.asStateFlow()

    init {
        observePreferences()
        checkPermissionsAndLoad()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                prefs.sim1DailyLimit,
                prefs.sim1MonthlyLimit,
                prefs.sim2DailyLimit,
                prefs.sim2MonthlyLimit,
                prefs.wifiDailyLimit,
                prefs.wifiMonthlyLimit
            ) { array ->
                val s1d = array[0]
                val s1m = array[1]
                val s2d = array[2]
                val s2m = array[3]
                val wfd = array[4]
                val wfm = array[5]
                _uiState.update { current ->
                    current.copy(
                        sim1DailyLimit = s1d,
                        sim1MonthlyLimit = s1m,
                        sim2DailyLimit = s2d,
                        sim2MonthlyLimit = s2m,
                        wifiDailyLimit = wfd,
                        wifiMonthlyLimit = wfm
                    )
                }
            }.collect()
        }

        viewModelScope.launch {
            combine(
                prefs.anchorDay,
                prefs.unitPreference,
                prefs.themePreference,
                prefs.notificationsEnabled,
                prefs.rolloverEnabled
            ) { anchor, unit, theme, notifs, rollover ->
                _uiState.update { current ->
                    current.copy(
                        anchorDay = anchor,
                        unitPreference = unit,
                        themePreference = theme,
                        notificationsEnabled = notifs,
                        rolloverEnabled = rollover
                    )
                }
            }.collect()
        }
    }

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
                loadHistory(_uiState.value.selectedNetworkType, 7)
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            if (!dataUsageManager.hasUsageAccessPermission()) {
                _uiState.update { it.copy(isLoading = false, hasUsageAccess = false) }
                return@launch
            }
            loadUsageData()
            loadHistory(_uiState.value.selectedNetworkType, 7)
        }
    }

    fun setTimePeriodFilter(
        filter: TimePeriodFilter,
        customStartMs: Long = 0L,
        customEndMs: Long = 0L
    ) {
        _uiState.update {
            it.copy(
                selectedPeriod = filter,
                customStartMs = customStartMs,
                customEndMs = customEndMs
            )
        }
        refresh()
    }

    fun setNetworkType(networkType: Int) {
        _uiState.update { it.copy(selectedNetworkType = networkType) }
        refresh()
    }

    fun setAppSearchQuery(query: String) {
        _uiState.update { it.copy(appSearchQuery = query) }
    }

    fun setAppSortOption(sortOption: AppSortOption) {
        _uiState.update { it.copy(appSortOption = sortOption) }
    }

    fun selectAppForDetail(app: AppUsageInfo?) {
        if (app == null) {
            _uiState.update { it.copy(selectedApp = null, selectedAppHourlyUsage = emptyList()) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(selectedApp = app) }

            val (startTime, endTime) = getTimeRangeMillis()
            val hourly = withContext(Dispatchers.IO) {
                dataUsageManager.getHourlyUsageForApp(
                    _uiState.value.selectedNetworkType,
                    _uiState.value.activeSubscriberId,
                    app.packageName,
                    startTime,
                    endTime
                )
            }

            _uiState.update { it.copy(selectedAppHourlyUsage = hourly) }
        }
    }

    fun loadHistory(networkType: Int, days: Int) {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) {
                dataUsageManager.getDailyUsageHistory(
                    networkType,
                    _uiState.value.activeSubscriberId,
                    days
                )
            }
            _uiState.update { it.copy(historyList = history) }
        }
    }

    fun saveSettings(
        sim1Daily: Long,
        sim1Monthly: Long,
        sim2Daily: Long,
        sim2Monthly: Long,
        wifiDaily: Long,
        wifiMonthly: Long,
        anchorDay: Int,
        unitPref: UnitPreference,
        themePref: AppThemePreference,
        notifications: Boolean,
        rollover: Boolean = true
    ) {
        viewModelScope.launch {
            prefs.setSim1DailyLimit(sim1Daily)
            prefs.setSim1MonthlyLimit(sim1Monthly)
            prefs.setSim2DailyLimit(sim2Daily)
            prefs.setSim2MonthlyLimit(sim2Monthly)
            prefs.setWifiDailyLimit(wifiDaily)
            prefs.setWifiMonthlyLimit(wifiMonthly)
            prefs.setAnchorDay(anchorDay)
            prefs.setUnitPreference(unitPref)
            prefs.setThemePreference(themePref)
            prefs.setNotificationsEnabled(notifications)
            prefs.setRolloverEnabled(rollover)

            refresh()
        }
    }

    private fun getTimeRangeMillis(): Pair<Long, Long> {
        val state = _uiState.value
        val now = System.currentTimeMillis()

        return when (state.selectedPeriod) {
            TimePeriodFilter.TODAY -> Pair(dataUsageManager.getStartOfDayMidnightMillis(), now)
            TimePeriodFilter.THIS_WEEK -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                Pair(cal.timeInMillis, now)
            }
            TimePeriodFilter.THIS_MONTH -> {
                val startMs = dataUsageManager.calculateMonthlyStartMillis(state.anchorDay)
                Pair(startMs, now)
            }
            TimePeriodFilter.CUSTOM_RANGE -> {
                val start = if (state.customStartMs > 0) state.customStartMs else dataUsageManager.getStartOfDayMidnightMillis()
                val end = if (state.customEndMs > 0) state.customEndMs else now
                Pair(start, end)
            }
        }
    }

    private fun determineActiveLimit(): Long {
        val state = _uiState.value
        val isWifi = state.selectedNetworkType == ConnectivityManager.TYPE_WIFI

        return when (state.selectedPeriod) {
            TimePeriodFilter.TODAY -> if (isWifi) state.wifiDailyLimit else state.sim1DailyLimit
            TimePeriodFilter.THIS_WEEK -> if (isWifi) state.wifiDailyLimit * 7 else state.sim1DailyLimit * 7
            TimePeriodFilter.THIS_MONTH -> if (isWifi) state.wifiMonthlyLimit else state.sim1MonthlyLimit
            TimePeriodFilter.CUSTOM_RANGE -> if (isWifi) state.wifiDailyLimit else state.sim1DailyLimit
        }
    }

    private suspend fun loadUsageData() {
        try {
            val (startTime, endTime) = getTimeRangeMillis()
            val netType = _uiState.value.selectedNetworkType
            val subId = _uiState.value.activeSubscriberId

            val usage = withContext(Dispatchers.IO) {
                dataUsageManager.getUsageForRange(netType, subId, startTime, endTime)
            }

            val appList = withContext(Dispatchers.IO) {
                dataUsageManager.getPerAppUsageForRange(netType, subId, startTime, endTime)
            }

            val activeCap = determineActiveLimit()

            // Rollover audit pool calculation
            val isWifi = netType == ConnectivityManager.TYPE_WIFI
            val dailyCap = if (isWifi) _uiState.value.wifiDailyLimit else _uiState.value.sim1DailyLimit
            val isBits = _uiState.value.unitPreference == UnitPreference.BITS_BYTES

            val rolloverPool = withContext(Dispatchers.IO) {
                dataUsageManager.calculateRolloverPool(netType, subId, dailyCap, 30)
            }

            val todayUsage = usage.totalBytes
            val rolloverMsg = if (todayUsage <= dailyCap) {
                if (rolloverPool > 0) {
                    "You can use an extra ${DataUsageManager.formatBytes(rolloverPool, isBits)} left from previous days"
                } else {
                    "No rollover data available from previous days"
                }
            } else {
                val excess = todayUsage - dailyCap
                if (rolloverPool >= excess) {
                    val remainingPool = rolloverPool - excess
                    "Over today's daily limit by ${DataUsageManager.formatBytes(excess, isBits)} (covered by rollover pool). ${DataUsageManager.formatBytes(remainingPool, isBits)} rollover balance remaining."
                } else if (rolloverPool > 0) {
                    val uncovered = excess - rolloverPool
                    "Exceeded daily limit by ${DataUsageManager.formatBytes(excess, isBits)}. Rollover pool covered ${DataUsageManager.formatBytes(rolloverPool, isBits)} (${DataUsageManager.formatBytes(uncovered, isBits)} over limit)."
                } else {
                    "Exceeded daily limit by ${DataUsageManager.formatBytes(excess, isBits)} (no rollover balance left)."
                }
            }

            // Forecast calculations
            val elapsedMs = (endTime - startTime).coerceAtLeast(1000L)
            val calNow = Calendar.getInstance()

            val forecastPaceStr = if (_uiState.value.selectedPeriod == TimePeriodFilter.THIS_MONTH) {
                val totalDaysInMonth = calNow.getActualMaximum(Calendar.DAY_OF_MONTH)
                val dayOfMonth = calNow.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
                val dailyAvgBytes = usage.totalBytes / dayOfMonth
                val projectedMonthBytes = dailyAvgBytes * totalDaysInMonth
                "On pace for ${DataUsageManager.formatBytes(projectedMonthBytes, isBits)} by end of month"
            } else {
                val remMsInDay = (24 * 3600 * 1000L) - (calNow.get(Calendar.HOUR_OF_DAY) * 3600 * 1000L + calNow.get(Calendar.MINUTE) * 60 * 1000L)
                val paceBytes = usage.totalBytes + ((usage.totalBytes.toDouble() / elapsedMs.toDouble()) * remMsInDay).toLong()
                "On pace for ${DataUsageManager.formatBytes(paceBytes, isBits)} today"
            }

            val remainingBytes = (activeCap - usage.totalBytes).coerceAtLeast(0L)
            val remainingTodayStr = "${DataUsageManager.formatBytes(remainingBytes, isBits)} remaining in active period"

            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    dataUsage = usage,
                    appUsageList = appList,
                    activeLimit = activeCap,
                    accumulatedRolloverBytes = rolloverPool,
                    rolloverMessage = rolloverMsg,
                    paceEndString = forecastPaceStr,
                    remainingTodayString = remainingTodayStr,
                    lastUpdatedMillis = System.currentTimeMillis(),
                    errorMessage = null
                )
            }

            // Trigger Notifications if enabled
            if (_uiState.value.notificationsEnabled) {
                checkAndTriggerAlerts(usage.totalBytes, activeCap, isBits)
            }

            WidgetReceiver.sendRefreshBroadcast(getApplication())
        } catch (e: Exception) {
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Failed to read network stats"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkAndTriggerAlerts(usedBytes: Long, limitBytes: Long, isBits: Boolean) {
        if (limitBytes <= 0) return
        val ratio = usedBytes.toDouble() / limitBytes.toDouble()

        if (ratio >= 1.0) {
            notificationManager.send100PercentAlert(usedBytes, limitBytes, isBits)
        } else if (ratio >= 0.8) {
            notificationManager.send80PercentAlert(usedBytes, limitBytes, isBits)
        }
    }

    fun exportCsvData(): String {
        return dataUsageManager.exportCsvString(
            _uiState.value.selectedNetworkType,
            _uiState.value.activeSubscriberId,
            30
        )
    }

    fun getUsageAccessSettingsIntent(): Intent {
        return dataUsageManager.getUsageAccessSettingsIntent()
    }
}
