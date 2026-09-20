package com.example

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class NetworkFilter {
    CELLULAR_SIM1,
    CELLULAR_SIM2,
    WIFI
}

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val uid: Int,
    val foregroundBytes: Long,
    val backgroundBytes: Long,
    val totalBytes: Long,
    val icon: android.graphics.drawable.Drawable? = null
)

data class LiveSpeedInfo(
    val downloadSpeedBytesPerSec: Long,
    val uploadSpeedBytesPerSec: Long
)

data class DataUsageReport(
    val totalBytes: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val backgroundBytes: Long,
    val foregroundBytes: Long,
    val topAppName: String,
    val topAppIcon: android.graphics.drawable.Drawable?,
    val topAppUsage: Long,
    val bgPercentage: Float,
    val isCellular: Boolean,
    val carrierName: String?,
    val dailyLimitBytes: Long = 6L * 1024L * 1024L * 1024L // Updated to 6.0 GB
)

data class HourlyUsageBucket(
    val hourLabel: String,
    val bytes: Long
)

class DataUsageManager(private val context: Context) {

    private val networkStatsManager: NetworkStatsManager? by lazy {
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
    }

    fun hasUsageAccessPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }

    fun getUsageAccessSettingsIntent(): Intent {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            intent
        } else {
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        }
    }

    fun getStartOfDayMidnightMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getStartOfWeekMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getStartOfMonthMillis(anchorDate: Int = 1): Long {
        return Calendar.getInstance().apply {
            val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
            val targetDay = anchorDate.coerceIn(1, maxDay)
            set(Calendar.DAY_OF_MONTH, targetDay)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis > System.currentTimeMillis()) {
                add(Calendar.MONTH, -1)
            }
        }.timeInMillis
    }

    /**
     * Gets subscriber ID for a given SIM slot. Returns null if not found or unauthorized.
     */
    @Suppress("MissingPermission", "DEPRECATION")
    private fun getSubscriberIdForSlot(slotIndex: Int): String? {
        if (!hasPhoneStatePermission()) return null
        return try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val activeList = sm?.activeSubscriptionInfoList
            if (activeList != null && slotIndex < activeList.size) {
                val info = activeList[slotIndex]
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    null // Android 10+ restricts subscriberId for privacy, fallback to standard device query
                } else {
                    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    tm?.createForSubscriptionId(info.subscriptionId)?.subscriberId
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Queries total usage for the specified filter and time window.
     */
    fun getTotalUsage(filter: NetworkFilter, startTime: Long, endTime: Long): Long {
        val nsm = networkStatsManager ?: return 0L
        val networkType = when (filter) {
            NetworkFilter.WIFI -> ConnectivityManager.TYPE_WIFI
            else -> ConnectivityManager.TYPE_MOBILE
        }
        val subscriberId = when (filter) {
            NetworkFilter.CELLULAR_SIM1 -> getSubscriberIdForSlot(0)
            NetworkFilter.CELLULAR_SIM2 -> getSubscriberIdForSlot(1)
            else -> null
        }

        return try {
            val bucket = nsm.querySummaryForDevice(networkType, subscriberId, startTime, endTime)
            bucket.rxBytes + bucket.txBytes
        } catch (e: Exception) {
            // Fallback retry if subscriberId query fails
            if (networkType == ConnectivityManager.TYPE_MOBILE) {
                try {
                    val bucket = nsm.querySummaryForDevice(networkType, null, startTime, endTime)
                    bucket.rxBytes + bucket.txBytes
                } catch (_: Exception) {
                    0L
                }
            } else {
                0L
            }
        }
    }

    /**
     * Queries detailed per-app consumption (foreground vs background) sorted descending by total usage.
     */
    fun getPerAppUsage(filter: NetworkFilter, startTime: Long, endTime: Long): List<AppUsageInfo> {
        val nsm = networkStatsManager ?: return emptyList()
        val networkType = when (filter) {
            NetworkFilter.WIFI -> ConnectivityManager.TYPE_WIFI
            else -> ConnectivityManager.TYPE_MOBILE
        }
        val subscriberId = when (filter) {
            NetworkFilter.CELLULAR_SIM1 -> getSubscriberIdForSlot(0)
            NetworkFilter.CELLULAR_SIM2 -> getSubscriberIdForSlot(1)
            else -> null
        }

        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val uidMap = mutableMapOf<Int, Pair<Long, Long>>() // uid -> Pair(foregroundBytes, backgroundBytes)

        try {
            // Query detailed summaries for all UIDs on the specified network type
            val stats = nsm.querySummary(networkType, subscriberId, startTime, endTime)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                val rx = bucket.rxBytes.coerceAtLeast(0L)
                val tx = bucket.txBytes.coerceAtLeast(0L)
                val bytes = rx + tx

                val currentPair = uidMap.getOrDefault(uid, Pair(0L, 0L))
                if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) {
                    uidMap[uid] = Pair(currentPair.first + bytes, currentPair.second)
                } else {
                    uidMap[uid] = Pair(currentPair.first, currentPair.second + bytes)
                }
            }
            stats.close()
        } catch (_: Exception) { }

        val appUsageList = mutableListOf<AppUsageInfo>()
        val seenUids = mutableSetOf<Int>()

        for (app in installedApps) {
            val uid = app.uid
            if (uid < 10000) continue // Skip system UIDs usually
            
            val pair = uidMap[uid] ?: continue
            if (pair.first + pair.second <= 0) continue

            // Only add unique UIDs to avoid duplicates in the list for apps that share UIDs
            if (seenUids.contains(uid)) continue
            seenUids.add(uid)

            val appName = pm.getApplicationLabel(app).toString()
            val packageName = app.packageName
            val icon = try { pm.getApplicationIcon(app) } catch (_: Exception) { null }

            appUsageList.add(
                AppUsageInfo(
                    packageName = packageName,
                    appName = appName,
                    uid = uid,
                    foregroundBytes = pair.first,
                    backgroundBytes = pair.second,
                    totalBytes = pair.first + pair.second,
                    icon = icon
                )
            )
        }

        // Group by application packages to handle multiple apps sharing the same UID if needed, or sort directly
        return appUsageList.sortedByDescending { it.totalBytes }
    }

    /**
     * Unified data-fetching function to be used by both the App and the Widget.
     * Ensures strict 00:00:00 AM start time and identical filtering logic.
     */
    fun getUnifiedUsageReport(filter: NetworkFilter): DataUsageReport {
        val midnight = getStartOfDayMidnightMillis()
        val now = System.currentTimeMillis()

        val total = getTotalUsage(filter, midnight, now)
        val appList = getPerAppUsage(filter, midnight, now)
        
        val topApp = appList.firstOrNull()
        val totalBackground = appList.sumOf { it.backgroundBytes }
        val totalForeground = appList.sumOf { it.foregroundBytes }
        
        val bgPercent = if (total > 0) (totalBackground.toFloat() / total.toFloat()) else 0f
        
        // Simple cellular check
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isCell = cm?.activeNetwork?.let { 
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) 
        } ?: false

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val carrier = tm?.networkOperatorName

        return DataUsageReport(
            totalBytes = total,
            rxBytes = appList.sumOf { it.foregroundBytes + it.backgroundBytes },
            txBytes = 0L,
            backgroundBytes = totalBackground,
            foregroundBytes = totalForeground,
            topAppName = topApp?.appName ?: "None",
            topAppIcon = topApp?.icon,
            topAppUsage = topApp?.totalBytes ?: 0L,
            bgPercentage = bgPercent,
            isCellular = isCell,
            carrierName = carrier,
            dailyLimitBytes = 6L * 1024L * 1024L * 1024L
        )
    }

    /**
     * Queries NetworkStatsManager in 1-hour interval buckets from midnight.
     */
    fun getHourlyUsageTimeline(filter: NetworkFilter): List<HourlyUsageBucket> {
        val nsm = networkStatsManager ?: return emptyList()
        val networkType = when (filter) {
            NetworkFilter.WIFI -> ConnectivityManager.TYPE_WIFI
            else -> ConnectivityManager.TYPE_MOBILE
        }
        val subscriberId = when (filter) {
            NetworkFilter.CELLULAR_SIM1 -> getSubscriberIdForSlot(0)
            NetworkFilter.CELLULAR_SIM2 -> getSubscriberIdForSlot(1)
            else -> null
        }

        val startOfDay = getStartOfDayMidnightMillis()
        val currentTime = System.currentTimeMillis()
        val buckets = mutableListOf<HourlyUsageBucket>()

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = startOfDay

        val timeFormatter = SimpleDateFormat("h a", Locale.US)

        while (calendar.timeInMillis < currentTime) {
            val startTime = calendar.timeInMillis
            calendar.add(Calendar.HOUR_OF_DAY, 1)
            val endTime = calendar.timeInMillis.coerceAtMost(currentTime)

            var hourlyBytes = 0L
            try {
                val bucket = nsm.querySummaryForDevice(networkType, subscriberId, startTime, endTime)
                hourlyBytes = bucket.rxBytes + bucket.txBytes
            } catch (_: Exception) {
                // Retry if subscriberId was used
                if (subscriberId != null) {
                    try {
                        val bucket = nsm.querySummaryForDevice(networkType, null, startTime, endTime)
                        hourlyBytes = bucket.rxBytes + bucket.txBytes
                    } catch (_: Exception) {}
                }
            }

            buckets.add(HourlyUsageBucket(timeFormatter.format(Date(startTime)), hourlyBytes))
        }
        return buckets
    }

    /**
     * Calculates instant download/upload speed by sampling TrafficStats.
     */
    private var lastRxBytes: Long = 0L
    private var lastTxBytes: Long = 0L
    private var lastSpeedTimeMillis: Long = 0L

    fun getLiveSpeed(): LiveSpeedInfo {
        val currentRx = TrafficStats.getTotalRxBytes()
        val currentTx = TrafficStats.getTotalTxBytes()
        val currentTime = System.currentTimeMillis()

        if (lastSpeedTimeMillis == 0L) {
            lastRxBytes = currentRx
            lastTxBytes = currentTx
            lastSpeedTimeMillis = currentTime
            return LiveSpeedInfo(0L, 0L)
        }

        val timeDiffSec = (currentTime - lastSpeedTimeMillis) / 1000.0
        if (timeDiffSec <= 0) return LiveSpeedInfo(0L, 0L)

        val rxDiff = (currentRx - lastRxBytes).coerceAtLeast(0L)
        val txDiff = (currentTx - lastTxBytes).coerceAtLeast(0L)

        lastRxBytes = currentRx
        lastTxBytes = currentTx
        lastSpeedTimeMillis = currentTime

        return LiveSpeedInfo(
            downloadSpeedBytesPerSec = (rxDiff / timeDiffSec).toLong(),
            uploadSpeedBytesPerSec = (txDiff / timeDiffSec).toLong()
        )
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0.00 MB"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format(Locale.US, "%.2f %s", value, units[digitGroups])
        }

        fun formatParts(bytes: Long): Pair<String, String> {
            if (bytes <= 0) return Pair("0.00", "MB")
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return Pair(String.format(Locale.US, "%.2f", value), units[digitGroups])
        }

        fun formatSpeed(bytesPerSec: Long): String {
            if (bytesPerSec <= 0) return "0.0 B/s"
            val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
            val digitGroups = (Math.log10(bytesPerSec.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val value = bytesPerSec / Math.pow(1024.0, digitGroups.toDouble())
            return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
        }

        fun formatDate(millis: Long): String {
            val sdf = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
            return sdf.format(Date(millis))
        }

        fun formatTime(millis: Long): String {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            return sdf.format(Date(millis))
        }
    }
}
