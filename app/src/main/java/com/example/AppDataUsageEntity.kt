package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_data_usage_history")
data class AppDataUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val dateString: String, // e.g. "yyyy-MM-dd"
    val foregroundBytes: Long,
    val backgroundBytes: Long,
    val totalBytes: Long,
    val networkType: String // e.g., "CELLULAR_SIM1", "CELLULAR_SIM2", "WIFI"
)

@Dao
interface AppDataUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsage(usage: AppDataUsageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(usages: List<AppDataUsageEntity>)

    @Query("SELECT * FROM app_data_usage_history WHERE dateString = :dateString AND networkType = :networkType ORDER BY totalBytes DESC")
    fun getUsageForDate(dateString: String, networkType: String): Flow<List<AppDataUsageEntity>>

    @Query("SELECT dateString, SUM(totalBytes) as totalBytes FROM app_data_usage_history GROUP BY dateString ORDER BY dateString DESC LIMIT :limit")
    fun getHistoricalTotals(limit: Int): Flow<List<DateTotalProjection>>
}

data class DateTotalProjection(
    val dateString: String,
    val totalBytes: Long
)
