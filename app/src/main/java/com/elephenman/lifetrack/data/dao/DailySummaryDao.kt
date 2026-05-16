package com.elephenman.lifetrack.data.dao

import androidx.room.*
import com.elephenman.lifetrack.data.entity.DailySummary
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: DailySummary)

    @Query("SELECT * FROM daily_summary WHERE date = :date")
    suspend fun getByDate(date: String): DailySummary?

    @Query("SELECT * FROM daily_summary WHERE date = :date")
    fun getByDateFlow(date: String): Flow<DailySummary?>

    @Query("SELECT * FROM daily_summary WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getByDateRange(startDate: String, endDate: String): List<DailySummary>

    @Query("SELECT * FROM daily_summary ORDER BY date DESC")
    fun getAllFlow(): Flow<List<DailySummary>>

    @Query("DELETE FROM daily_summary WHERE date < :beforeDate")
    suspend fun deleteBefore(beforeDate: String): Int
}
