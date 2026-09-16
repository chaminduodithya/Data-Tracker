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
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
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
 * Helper class wrapping NetworkStatsManager queries and system permission checks.
 */
class DataUsageManager(private val context: Context) {

    private val networkStatsManager: NetworkStatsManager? by lazy {
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
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
            // Attempt to point directly to this application's settings if supported
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        // Verify that the package-specific intent can resolve; if not, fallback to general usage settings
        val canResolve = intent.resolveActivity(context.packageManager) != null
        return if (canResolve) {
            intent
        } else {
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

    /**
     * Calculates the start time of the current calendar day at 00:00:00.000 AM.
     */
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
     * Queries daily mobile data usage between midnight and currentTimeMillis.
     */
    fun getDailyMobileDataUsage(): DailyMobileDataUsage {
        val startTime = getStartOfDayMidnightMillis()
        val endTime = System.currentTimeMillis()

        val nsm = networkStatsManager
            ?: return DailyMobileDataUsage(
                startTimeMillis = startTime,
                endTimeMillis = endTime
            )

        val subscriberId = getSubscriberId()
        var rx = 0L
        var tx = 0L

        // Query total mobile device traffic summary
        try {
            val bucket = nsm.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE,
                subscriberId,
                startTime,
                endTime
            )
            rx = bucket.rxBytes.coerceAtLeast(0L)
            tx = bucket.txBytes.coerceAtLeast(0L)
        } catch (e: Exception) {
            // If subscriberId was used and failed, retry with null subscriberId
            if (subscriberId != null) {
                try {
                    val bucket = nsm.querySummaryForDevice(
                        ConnectivityManager.TYPE_MOBILE,
                        null,
                        startTime,
                        endTime
                    )
                    rx = bucket.rxBytes.coerceAtLeast(0L)
                    tx = bucket.txBytes.coerceAtLeast(0L)
                } catch (_: Exception) {
                    // Ignore and keep 0L
                }
            }
        }

        // Query Hotspot & Tethering traffic using UID_TETHERING (-5)
        var tetheringRx = 0L
        var tetheringTx = 0L

        try {
            val tetheringStats = nsm.queryDetailsForUid(
                ConnectivityManager.TYPE_MOBILE,
                subscriberId,
                startTime,
                endTime,
                NetworkStats.Bucket.UID_TETHERING
            )
            val tBucket = NetworkStats.Bucket()
            while (tetheringStats.hasNextBucket()) {
                tetheringStats.getNextBucket(tBucket)
                tetheringRx += tBucket.rxBytes.coerceAtLeast(0L)
                tetheringTx += tBucket.txBytes.coerceAtLeast(0L)
            }
            tetheringStats.close()
        } catch (_: Exception) {
            // Fallback: iterate querySummary if queryDetailsForUid fails
            try {
                val summaryStats = nsm.querySummary(
                    ConnectivityManager.TYPE_MOBILE,
                    subscriberId,
                    startTime,
                    endTime
                )
                val sBucket = NetworkStats.Bucket()
                while (summaryStats.hasNextBucket()) {
                    summaryStats.getNextBucket(sBucket)
                    if (sBucket.uid == NetworkStats.Bucket.UID_TETHERING) {
                        tetheringRx += sBucket.rxBytes.coerceAtLeast(0L)
                        tetheringTx += sBucket.txBytes.coerceAtLeast(0L)
                    }
                }
                summaryStats.close()
            } catch (_: Exception) {
                // Ignore if unavailable
            }
        }

        val total = rx + tx
        val tetheringTotal = tetheringRx + tetheringTx
        val isCellular = isCellularNetworkActive()
        val carrier = getCarrierName()

        return DailyMobileDataUsage(
            rxBytes = rx,
            txBytes = tx,
            totalBytes = total,
            tetheringRxBytes = tetheringRx,
            tetheringTxBytes = tetheringTx,
            tetheringTotalBytes = tetheringTotal,
            startTimeMillis = startTime,
            endTimeMillis = endTime,
            isCellularConnected = isCellular,
            carrierName = carrier
        )
    }

    private fun getSubscriberId(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ requires null for carrier-agnostic device queries
            return null
        }
        return try {
            if (hasPhoneStatePermission()) {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                @Suppress("DEPRECATION")
                telephonyManager?.subscriberId
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isCellularNetworkActive(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (_: Exception) {
            false
        }
    }

    private fun getCarrierName(): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val name = telephonyManager?.networkOperatorName
            if (!name.isNullOrBlank()) name else telephonyManager?.simOperatorName
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /**
         * Formats bytes into human-readable string with 2 decimal places (e.g. 12.34 MB).
         */
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0.00 MB"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format(Locale.US, "%.2f %s", value, units[digitGroups])
        }

        /**
         * Returns value and unit separately (e.g. "1.45" and "GB") for large typography styling.
         */
        fun formatParts(bytes: Long): Pair<String, String> {
            if (bytes <= 0) return Pair("0.00", "MB")
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return Pair(String.format(Locale.US, "%.2f", value), units[digitGroups])
        }

        /**
         * Formats timestamp into readable time string (e.g., "12:00 AM" or "02:30 PM").
         */
        fun formatTime(millis: Long): String {
            if (millis <= 0) return "--:--"
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            return sdf.format(Date(millis))
        }

        /**
         * Formats timestamp into readable date string (e.g., "Tuesday, Sep 15").
         */
        fun formatDate(millis: Long): String {
            if (millis <= 0) return ""
            val sdf = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
            return sdf.format(Date(millis))
        }
    }
}
