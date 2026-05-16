package com.elephenman.lifetrack.data.dao

import androidx.room.*
import com.elephenman.lifetrack.data.entity.TripSegment
import kotlinx.coroutines.flow.Flow

@Dao
interface TripSegmentDao {

    @Insert
    suspend fun insert(segment: TripSegment): Long

    @Query("SELECT * FROM trip_segment WHERE date = :date ORDER BY startTime ASC")
    suspend fun getByDate(date: String): List<TripSegment>

    @Query("SELECT * FROM trip_segment WHERE date = :date ORDER BY startTime ASC")
    fun getByDateFlow(date: String): Flow<List<TripSegment>>

    @Query("SELECT * FROM trip_segment WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC, startTime ASC")
    suspend fun getByDateRange(startDate: String, endDate: String): List<TripSegment>

    @Query("DELETE FROM trip_segment WHERE date < :beforeDate")
    suspend fun deleteBefore(beforeDate: String): Int
}
