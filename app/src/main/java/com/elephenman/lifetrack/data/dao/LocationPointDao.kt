package com.elephenman.lifetrack.data.dao

import androidx.room.*
import com.elephenman.lifetrack.data.entity.LocationPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationPointDao {

    @Insert
    suspend fun insert(point: LocationPoint): Long

    @Insert
    suspend fun insertAll(points: List<LocationPoint>)

    @Query("SELECT * FROM location_point WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getByTimeRange(startTime: Long, endTime: Long): List<LocationPoint>

    @Query("SELECT * FROM location_point WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getByTimeRangeFlow(startTime: Long, endTime: Long): Flow<List<LocationPoint>>

    @Query("SELECT * FROM location_point ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<LocationPoint>

    @Query("SELECT COUNT(*) FROM location_point WHERE timestamp BETWEEN :startTime AND :endTime")
    suspend fun getCountByTimeRange(startTime: Long, endTime: Long): Int

    @Query("DELETE FROM location_point WHERE timestamp < :beforeTimestamp")
    suspend fun deleteBefore(beforeTimestamp: Long): Int

    @Query("DELETE FROM location_point WHERE timestamp BETWEEN :startTime AND :endTime")
    suspend fun deleteByTimeRange(startTime: Long, endTime: Long)
}
