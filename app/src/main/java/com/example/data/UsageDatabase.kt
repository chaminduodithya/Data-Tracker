package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "daily_usage", primaryKeys = ["date", "networkType"])
data class DailyUsage(
    val date: Long, // Midnight timestamp
    val networkType: Int, // ConnectivityManager.TYPE_MOBILE or TYPE_WIFI
    val rxBytes: Long,
    val txBytes: Long,
    val totalBytes: Long
)

@Entity(tableName = "app_usage", primaryKeys = ["date", "packageName", "networkType"])
data class AppUsage(
    val date: Long,
    val packageName: String,
    val networkType: Int,
    val rxBytes: Long,
    val txBytes: Long,
    val totalBytes: Long
)

@Dao
interface UsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyUsage(usage: DailyUsage)

    @Query("SELECT * FROM daily_usage WHERE date >= :since ORDER BY date ASC")
    fun getDailyUsageSince(since: Long): Flow<List<DailyUsage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppUsage(usageList: List<AppUsage>)

    @Query("SELECT * FROM app_usage WHERE date = :date AND networkType = :networkType ORDER BY totalBytes DESC")
    fun getAppUsageForDay(date: Long, networkType: Int): Flow<List<AppUsage>>
}

@Database(entities = [DailyUsage::class, AppUsage::class], version = 1, exportSchema = false)
abstract class UsageDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao

    companion object {
        @Volatile
        private var INSTANCE: UsageDatabase? = null

        fun getDatabase(context: Context): UsageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UsageDatabase::class.java,
                    "usage_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
