package com.example.widget

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.AppUsageInfo
import com.example.DataUsageManager
import com.example.data.DataTrackerPrefs
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class DataWidgetWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "data_tracker_widget_periodic_work"
        const val WORK_IMMEDIATE_NAME = "data_tracker_widget_immediate_work"

        val TARGET_KEY = stringPreferencesKey("target_type") // "SIM1", "SIM2", "WIFI"
        val LAYOUT_STYLE_KEY = stringPreferencesKey("layout_style") // "FULL", "GAUGE_SPEED", "GAUGE_ONLY"
        val DISPLAY_LABEL_KEY = stringPreferencesKey("display_label") // "SIM 1 • Mobile", "Wi-Fi Network", etc.
        val USED_BYTES_KEY = longPreferencesKey("widget_used_bytes")
        val LIMIT_BYTES_KEY = longPreferencesKey("widget_limit_bytes")
        val TOP_APP_1_NAME = stringPreferencesKey("top_app_1_name")
        val TOP_APP_1_BYTES = longPreferencesKey("top_app_1_bytes")
        val TOP_APP_2_NAME = stringPreferencesKey("top_app_2_name")
        val TOP_APP_2_BYTES = longPreferencesKey("top_app_2_bytes")
        val TOP_APP_3_NAME = stringPreferencesKey("top_app_3_name")
        val TOP_APP_3_BYTES = longPreferencesKey("top_app_3_bytes")

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<DataWidgetWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }

        fun enqueueImmediate(context: Context) {
            val immediateRequest = OneTimeWorkRequestBuilder<DataWidgetWorker>()
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_IMMEDIATE_NAME,
                ExistingWorkPolicy.REPLACE,
                immediateRequest
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    private fun checkUsageAccessPermission(): Boolean {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override suspend fun doWork(): Result {
        val manager = DataUsageManager(appContext)
        val prefs = DataTrackerPrefs(appContext)
        val hasUsageAccess = checkUsageAccessPermission()

        val glanceManager = GlanceAppWidgetManager(appContext)
        val glanceIds = try {
            glanceManager.getGlanceIds(DataTrackerWidget::class.java)
        } catch (_: Exception) {
            emptyList()
        }

        val startTime = manager.getStartOfDayMidnightMillis()
        val endTime = System.currentTimeMillis()
        val nsm = appContext.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager

        for (glanceId in glanceIds) {
            try {
                var targetType = "SIM1"
                var layoutStyle = "FULL"

                updateAppWidgetState(appContext, glanceId) { state: Preferences ->
                    targetType = state[TARGET_KEY] ?: "SIM1"
                    layoutStyle = state[LAYOUT_STYLE_KEY] ?: "FULL"
                    state
                }

                @Suppress("DEPRECATION")
                val networkType = if (targetType == "WIFI") ConnectivityManager.TYPE_WIFI else ConnectivityManager.TYPE_MOBILE

                val displayLabel = when (targetType) {
                    "WIFI" -> "Wi-Fi Network"
                    "SIM2" -> "SIM 2 • Mobile"
                    else -> "SIM 1 • Mobile"
                }

                val limit = when (targetType) {
                    "SIM1" -> prefs.sim1DailyLimit.first()
                    "SIM2" -> prefs.sim2DailyLimit.first()
                    "WIFI" -> prefs.wifiDailyLimit.first()
                    else -> prefs.sim1DailyLimit.first()
                }

                var totalBytes = 0L
                var topApps = emptyList<AppUsageInfo>()

                if (hasUsageAccess && nsm != null) {
                    try {
                        val bucket = nsm.querySummaryForDevice(networkType, null, startTime, endTime)
                        totalBytes = (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
                    } catch (_: Exception) {
                        try {
                            val usage = manager.getUsageForRange(networkType, null, startTime, endTime)
                            totalBytes = usage.totalBytes
                        } catch (_: Exception) {}
                    }

                    try {
                        topApps = manager.getPerAppUsageForRange(networkType, null, startTime, endTime).take(3)
                    } catch (_: Exception) {}
                }

                updateAppWidgetState(appContext, glanceId) { state ->
                    val mutable = state.toMutablePreferences()
                    mutable[USED_BYTES_KEY] = totalBytes
                    mutable[LIMIT_BYTES_KEY] = limit
                    mutable[DISPLAY_LABEL_KEY] = displayLabel
                    mutable[LAYOUT_STYLE_KEY] = layoutStyle

                    if (topApps.isNotEmpty()) {
                        mutable[TOP_APP_1_NAME] = topApps[0].appName
                        mutable[TOP_APP_1_BYTES] = topApps[0].totalBytes
                    } else {
                        mutable[TOP_APP_1_NAME] = ""
                        mutable[TOP_APP_1_BYTES] = 0L
                    }

                    if (topApps.size >= 2) {
                        mutable[TOP_APP_2_NAME] = topApps[1].appName
                        mutable[TOP_APP_2_BYTES] = topApps[1].totalBytes
                    } else {
                        mutable[TOP_APP_2_NAME] = ""
                        mutable[TOP_APP_2_BYTES] = 0L
                    }

                    if (topApps.size >= 3) {
                        mutable[TOP_APP_3_NAME] = topApps[2].appName
                        mutable[TOP_APP_3_BYTES] = topApps[2].totalBytes
                    } else {
                        mutable[TOP_APP_3_NAME] = ""
                        mutable[TOP_APP_3_BYTES] = 0L
                    }

                    mutable
                }

                DataTrackerWidget().update(appContext, glanceId)
            } catch (_: Exception) {}
        }

        return Result.success()
    }
}
