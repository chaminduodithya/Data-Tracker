package com.example

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

/**
 * Data model representing daily mobile data consumption metrics.
 */
data class DailyMobileDataUsage(
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val tetheringRxBytes: Long = 0L,
    val tetheringTxBytes: Long = 0L,
    val tetheringTotalBytes: Long = 0L,
    val startTimeMillis: Long = 0L,
    val endTimeMillis: Long = 0L,
    val isCellularConnected: Boolean = false,
    val carrierName: String? = null
)

/**
 * Data model for per-app usage information.
 */
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val rxBytes: Long,
    val txBytes: Long,
    val totalBytes: Long,
    val foregroundBytes: Long = 0L,
    val backgroundBytes: Long = 0L
)

/**
 * Helper class wrapping NetworkStatsManager queries and system permission checks.
 */
class DataUsageManager(private val context: Context) {

    private val networkStatsManager: NetworkStatsManager? by lazy {
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
    }

    private val subscriptionManager: SubscriptionManager? by lazy {
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
    }

    /**
     * Checks whether the user has granted Usage Data Access (PACKAGE_USAGE_STATS).
     */
    fun hasUsageAccessPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Checks whether READ_PHONE_STATE permission is granted.
     */
    fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Creates an Intent to navigate the user to Usage Access Settings.
     */
    fun getUsageAccessSettingsIntent(): Intent {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val canResolve = intent.resolveActivity(context.packageManager) != null
        return if (canResolve) {
            intent
        } else {
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

    fun getStartOfDayMidnightMillis(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    /**
     * Queries daily usage for a specific network type and optional subscriber ID.
     */
    fun getDailyUsage(networkType: Int, subscriberId: String? = null): DailyMobileDataUsage {
        val startTime = getStartOfDayMidnightMillis()
        val endTime = System.currentTimeMillis()

        val nsm = networkStatsManager
            ?: return DailyMobileDataUsage(startTimeMillis = startTime, endTimeMillis = endTime)

        var rx = 0L
        var tx = 0L

        try {
            val bucket = nsm.querySummaryForDevice(networkType, subscriberId, startTime, endTime)
            rx = bucket.rxBytes.coerceAtLeast(0L)
            tx = bucket.txBytes.coerceAtLeast(0L)
        } catch (_: Exception) {
            // Retry with null subscriberId if it fails for mobile
            if (networkType == ConnectivityManager.TYPE_MOBILE && subscriberId != null) {
                try {
                    val bucket = nsm.querySummaryForDevice(networkType, null, startTime, endTime)
                    rx = bucket.rxBytes.coerceAtLeast(0L)
                    tx = bucket.txBytes.coerceAtLeast(0L)
                } catch (_: Exception) {}
            }
        }

        // Tethering stats (only for mobile)
        var tetheringTotal = 0L
        if (networkType == ConnectivityManager.TYPE_MOBILE) {
            try {
                val tetheringStats = nsm.queryDetailsForUid(
                    networkType, subscriberId, startTime, endTime, NetworkStats.Bucket.UID_TETHERING
                )
                val tBucket = NetworkStats.Bucket()
                while (tetheringStats.hasNextBucket()) {
                    tetheringStats.getNextBucket(tBucket)
                    tetheringTotal += (tBucket.rxBytes + tBucket.txBytes).coerceAtLeast(0L)
                }
                tetheringStats.close()
            } catch (_: Exception) {}
        }

        val total = rx + tx
        val isCellular = isCellularNetworkActive()
        val carrier = if (networkType == ConnectivityManager.TYPE_MOBILE) getCarrierName() else "Wi-Fi"

        return DailyMobileDataUsage(
            rxBytes = rx,
            txBytes = tx,
            totalBytes = total,
            tetheringTotalBytes = tetheringTotal,
            startTimeMillis = startTime,
            endTimeMillis = endTime,
            isCellularConnected = isCellular,
            carrierName = carrier
        )
    }

    /**
     * Queries usage for a custom time range.
     */
    fun getUsageForRange(
        networkType: Int,
        subscriberId: String? = null,
        startTime: Long,
        endTime: Long
    ): DailyMobileDataUsage {
        val nsm = networkStatsManager
            ?: return DailyMobileDataUsage(startTimeMillis = startTime, endTimeMillis = endTime)

        var rx = 0L
        var tx = 0L

        try {
            val bucket = nsm.querySummaryForDevice(networkType, subscriberId, startTime, endTime)
            rx = bucket.rxBytes.coerceAtLeast(0L)
            tx = bucket.txBytes.coerceAtLeast(0L)
        } catch (_: Exception) {
            if (networkType == ConnectivityManager.TYPE_MOBILE && subscriberId != null) {
                try {
                    val bucket = nsm.querySummaryForDevice(networkType, null, startTime, endTime)
                    rx = bucket.rxBytes.coerceAtLeast(0L)
                    tx = bucket.txBytes.coerceAtLeast(0L)
                } catch (_: Exception) {}
            }
        }

        var tetheringTotal = 0L
        if (networkType == ConnectivityManager.TYPE_MOBILE) {
            try {
                val tetheringStats = nsm.queryDetailsForUid(
                    networkType, subscriberId, startTime, endTime, NetworkStats.Bucket.UID_TETHERING
                )
                val tBucket = NetworkStats.Bucket()
                while (tetheringStats.hasNextBucket()) {
                    tetheringStats.getNextBucket(tBucket)
                    tetheringTotal += (tBucket.rxBytes + tBucket.txBytes).coerceAtLeast(0L)
                }
                tetheringStats.close()
            } catch (_: Exception) {}
        }

        val total = rx + tx
        val isCellular = isCellularNetworkActive()
        val carrier = if (networkType == ConnectivityManager.TYPE_MOBILE) getCarrierName() else "Wi-Fi"

        return DailyMobileDataUsage(
            rxBytes = rx,
            txBytes = tx,
            totalBytes = total,
            tetheringTotalBytes = tetheringTotal,
            startTimeMillis = startTime,
            endTimeMillis = endTime,
            isCellularConnected = isCellular,
            carrierName = carrier
        )
    }

    /**
     * Legacy method for daily mobile usage (backward compatibility).
     */
    fun getDailyMobileDataUsage(): DailyMobileDataUsage {
        return getDailyUsage(ConnectivityManager.TYPE_MOBILE, getSubscriberId())
    }

    /**
     * Queries per-app data usage breakdown for a custom time range.
     * Uses package scanning and UID aggregation to ensure all user-installed social media and third-party apps are included.
     */
    fun getPerAppUsageForRange(
        networkType: Int,
        subscriberId: String?,
        startTime: Long,
        endTime: Long
    ): List<AppUsageInfo> {
        val nsm = networkStatsManager ?: return emptyList()
        val pm = context.packageManager

        // 1. Collect all installed packages (both user-installed and system)
        val installedPackages = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        // Map UID to ApplicationInfo (prefer non-system or primary app if multiple apps share UID)
        val uidToAppMap = mutableMapOf<Int, ApplicationInfo>()
        for (pkg in installedPackages) {
            val appInfo = pkg.applicationInfo ?: continue
            val existing = uidToAppMap[appInfo.uid]
            if (existing == null) {
                uidToAppMap[appInfo.uid] = appInfo
            } else {
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val existingIsSystem = (existing.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (existingIsSystem && !isSystem) {
                    uidToAppMap[appInfo.uid] = appInfo
                }
            }
        }

        val appUsageMap = mutableMapOf<Int, AppUsageInfoBuilder>()

        fun processStats(stats: NetworkStats?) {
            if (stats == null) return
            try {
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val uid = bucket.uid
                    val rx = bucket.rxBytes.coerceAtLeast(0L)
                    val tx = bucket.txBytes.coerceAtLeast(0L)
                    if (rx + tx <= 0) continue

                    val builder = appUsageMap.getOrPut(uid) { AppUsageInfoBuilder(uid) }
                    builder.rxBytes += rx
                    builder.txBytes += tx

                    if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) {
                        builder.foregroundBytes += (rx + tx)
                    } else {
                        builder.backgroundBytes += (rx + tx)
                    }
                }
                stats.close()
            } catch (_: Exception) {}
        }

        // 2. Query summary statistics across all UIDs
        try {
            val stats = nsm.querySummary(networkType, subscriberId, startTime, endTime)
            processStats(stats)
        } catch (_: Exception) {}

        // Retry with null subscriberId if mobile & initial summary was empty
        if (appUsageMap.isEmpty() && networkType == ConnectivityManager.TYPE_MOBILE && subscriberId != null) {
            try {
                val stats = nsm.querySummary(networkType, null, startTime, endTime)
                processStats(stats)
            } catch (_: Exception) {}
        }

        // 3. Direct UID fallback query for all scanned user-installed apps if querySummary missed any
        if (appUsageMap.isEmpty()) {
            for (appInfo in uidToAppMap.values) {
                val uid = appInfo.uid
                try {
                    val stats = nsm.queryDetailsForUid(networkType, subscriberId, startTime, endTime, uid)
                    val bucket = NetworkStats.Bucket()
                    var rx = 0L
                    var tx = 0L
                    var fg = 0L
                    var bg = 0L
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(bucket)
                        val r = bucket.rxBytes.coerceAtLeast(0L)
                        val t = bucket.txBytes.coerceAtLeast(0L)
                        rx += r
                        tx += t
                        if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) {
                            fg += (r + t)
                        } else {
                            bg += (r + t)
                        }
                    }
                    stats.close()

                    if (rx + tx > 0) {
                        val builder = appUsageMap.getOrPut(uid) { AppUsageInfoBuilder(uid) }
                        builder.rxBytes = rx
                        builder.txBytes = tx
                        builder.foregroundBytes = fg
                        builder.backgroundBytes = bg
                    }
                } catch (_: Exception) {}
            }
        }

        // 4. Construct AppUsageInfo result list
        val result = mutableListOf<AppUsageInfo>()
        for ((uid, builder) in appUsageMap) {
            val total = builder.rxBytes + builder.txBytes
            if (total <= 0) continue

            val appInfo = uidToAppMap[uid] ?: run {
                val pkgs = pm.getPackagesForUid(uid)
                if (!pkgs.isNullOrEmpty()) {
                    try { pm.getApplicationInfo(pkgs[0], 0) } catch (_: Exception) { null }
                } else null
            }

            if (appInfo != null) {
                val appName = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    appInfo.packageName
                }
                val icon = try {
                    pm.getApplicationIcon(appInfo)
                } catch (_: Exception) {
                    null
                }
                result.add(
                    AppUsageInfo(
                        packageName = appInfo.packageName,
                        appName = appName,
                        icon = icon,
                        rxBytes = builder.rxBytes,
                        txBytes = builder.txBytes,
                        totalBytes = total,
                        foregroundBytes = builder.foregroundBytes,
                        backgroundBytes = builder.backgroundBytes
                    )
                )
            }
        }

        // 5. Return all apps with totalBytes > 0 sorted in descending order
        return result.sortedByDescending { it.totalBytes }
    }

    /**
     * Queries per-app data usage breakdown for today.
     */
    fun getPerAppUsage(networkType: Int, subscriberId: String?): List<AppUsageInfo> {
        val startTime = getStartOfDayMidnightMillis()
        val endTime = System.currentTimeMillis()
        return getPerAppUsageForRange(networkType, subscriberId, startTime, endTime)
    }

    /**
     * Queries hourly usage breakdown (0-23 hours) for a specific app package.
     */
    fun getHourlyUsageForApp(
        networkType: Int,
        subscriberId: String?,
        targetPackageName: String,
        startTime: Long,
        endTime: Long
    ): List<Pair<Int, Long>> {
        val nsm = networkStatsManager ?: return List(24) { Pair(it, 0L) }
        val pm = context.packageManager

        var targetUid = -1
        try {
            targetUid = pm.getApplicationInfo(targetPackageName, 0).uid
        } catch (_: Exception) {
            return List(24) { Pair(it, 0L) }
        }

        val hourlyBytes = LongArray(24) { 0L }

        try {
            val stats = nsm.queryDetailsForUid(networkType, subscriberId, startTime, endTime, targetUid)
            val bucket = NetworkStats.Bucket()
            val cal = Calendar.getInstance()

            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                cal.timeInMillis = bucket.startTimeStamp
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                hourlyBytes[hour] += (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
            }
            stats.close()
        } catch (_: Exception) {}

        return hourlyBytes.mapIndexed { hour, bytes -> Pair(hour, bytes) }
    }

    /**
     * Returns daily usage for each of the last N days (oldest to newest).
     */
    fun getDailyUsageHistory(
        networkType: Int,
        subscriberId: String?,
        days: Int
    ): List<Pair<Long, Long>> {
        val nsm = networkStatsManager ?: return emptyList()
        val result = mutableListOf<Pair<Long, Long>>()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Collect N days
        for (i in (days - 1) downTo 0) {
            val dayCal = (calendar.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val startMs = dayCal.timeInMillis
            val endMs = (dayCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis - 1

            var total = 0L
            try {
                val bucket = nsm.querySummaryForDevice(networkType, subscriberId, startMs, endMs)
                total = (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
            } catch (_: Exception) {
                if (networkType == ConnectivityManager.TYPE_MOBILE && subscriberId != null) {
                    try {
                        val bucket = nsm.querySummaryForDevice(networkType, null, startMs, endMs)
                        total = (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
                    } catch (_: Exception) {}
                }
            }

            result.add(Pair(startMs, total))
        }

        return result
    }

    /**
     * Calculates accumulated leftover rollover data across past completed days.
     * For each past day d:
     *   surplus = (dailyLimitBytes - dayBytes)
     * Sums all surplus/deficit across past days to determine available rollover pool.
     */
    fun calculateRolloverPool(
        networkType: Int,
        subscriberId: String?,
        dailyLimitBytes: Long,
        daysToAudit: Int = 30
    ): Long {
        if (dailyLimitBytes <= 0) return 0L
        val history = getDailyUsageHistory(networkType, subscriberId, daysToAudit)
        if (history.size <= 1) return 0L

        // Exclude today (the last entry in history)
        val pastDays = history.dropLast(1)
        var accumulatedPool = 0L

        for ((_, dayBytes) in pastDays) {
            val daySurplus = dailyLimitBytes - dayBytes
            accumulatedPool += daySurplus
        }

        return accumulatedPool.coerceAtLeast(0L)
    }

    /**
     * Calculates the start time millis for the current monthly billing cycle based on anchor reset day.
     */
    fun calculateMonthlyStartMillis(anchorDay: Int): Long {
        val now = Calendar.getInstance()
        val targetDay = anchorDay.coerceIn(1, 31)

        val cycleStart = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            val maxDaysInCurrentMonth = getActualMaximum(Calendar.DAY_OF_MONTH)
            val effectiveDay = targetDay.coerceAtMost(maxDaysInCurrentMonth)
            
            if (get(Calendar.DAY_OF_MONTH) >= effectiveDay) {
                set(Calendar.DAY_OF_MONTH, effectiveDay)
            } else {
                add(Calendar.MONTH, -1)
                val maxDaysInPrevMonth = getActualMaximum(Calendar.DAY_OF_MONTH)
                set(Calendar.DAY_OF_MONTH, targetDay.coerceAtMost(maxDaysInPrevMonth))
            }
        }

        return cycleStart.timeInMillis
    }

    /**
     * Exports usage history as a CSV string.
     */
    fun exportCsvString(networkType: Int, subscriberId: String?, days: Int): String {
        val history = getDailyUsageHistory(networkType, subscriberId, days)
        val sb = StringBuilder()
        sb.append("Date,Day of Week,Bytes Used,Formatted Usage\n")

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfDay = SimpleDateFormat("EEEE", Locale.US)

        for ((timestamp, bytes) in history) {
            val dateStr = sdfDate.format(Date(timestamp))
            val dayStr = sdfDay.format(Date(timestamp))
            val formatted = formatBytes(bytes)
            sb.append("$dateStr,$dayStr,$bytes,\"$formatted\"\n")
        }

        return sb.toString()
    }

    private class AppUsageInfoBuilder(val uid: Int) {
        var rxBytes: Long = 0L
        var txBytes: Long = 0L
        var foregroundBytes: Long = 0L
        var backgroundBytes: Long = 0L
    }

    /**
     * Data model for real-time network speed.
     */
    data class NetworkSpeed(
        val downSpeedBytes: Long,
        val upSpeedBytes: Long
    )

    /**
     * Calculates network speed based on two snapshots.
     */
    fun calculateSpeed(oldUsage: DailyMobileDataUsage, newUsage: DailyMobileDataUsage, intervalMs: Long): NetworkSpeed {
        if (intervalMs <= 0) return NetworkSpeed(0, 0)
        val down = ((newUsage.rxBytes - oldUsage.rxBytes).coerceAtLeast(0L) * 1000) / intervalMs
        val up = ((newUsage.txBytes - oldUsage.txBytes).coerceAtLeast(0L) * 1000) / intervalMs
        return NetworkSpeed(down, up)
    }

    /**
     * Gets subscriber IDs for active SIM cards.
     */
    fun getActiveSubscriptionIds(): List<String> {
        if (!hasPhoneStatePermission()) return emptyList()
        return try {
            @Suppress("MissingPermission")
            subscriptionManager?.activeSubscriptionInfoList?.mapNotNull { it.subscriptionId.toString() }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getSubscriberId(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return null
        return try {
            if (hasPhoneStatePermission()) {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                @Suppress("DEPRECATION")
                tm?.subscriberId
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun isCellularNetworkActive(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun getCarrierName(): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val name = tm?.networkOperatorName
        return if (!name.isNullOrBlank()) name else tm?.simOperatorName
    }

    companion object {
        fun formatBytes(bytes: Long, isBits: Boolean = false): String {
            if (bytes <= 0) return if (isBits) "0.00 Mb" else "0.00 MB"
            val valToUse = if (isBits) bytes * 8.0 else bytes.toDouble()
            val units = if (isBits) arrayOf("b", "Kb", "Mb", "Gb", "Tb") else arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(valToUse) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val value = valToUse / Math.pow(1024.0, digitGroups.toDouble())
            return String.format(Locale.US, "%.2f %s", value, units[digitGroups])
        }

        fun formatParts(bytes: Long, isBits: Boolean = false): Pair<String, String> {
            if (bytes <= 0) return Pair("0.00", if (isBits) "Mb" else "MB")
            val valToUse = if (isBits) bytes * 8.0 else bytes.toDouble()
            val units = if (isBits) arrayOf("b", "Kb", "Mb", "Gb", "Tb") else arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(valToUse) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val value = valToUse / Math.pow(1024.0, digitGroups.toDouble())
            return Pair(String.format(Locale.US, "%.2f", value), units[digitGroups])
        }

        fun formatSpeed(bytesPerSec: Long, isBits: Boolean = false): String {
            return "${formatBytes(bytesPerSec, isBits)}/s"
        }

        fun formatTime(millis: Long): String {
            if (millis <= 0) return "--:--"
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            return sdf.format(Date(millis))
        }

        fun formatDate(millis: Long): String {
            if (millis <= 0) return ""
            val sdf = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
            return sdf.format(Date(millis))
        }
    }
}

