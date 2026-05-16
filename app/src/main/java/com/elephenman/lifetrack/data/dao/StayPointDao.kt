package com.elephenman.lifetrack.data.dao

import androidx.room.*
import com.elephenman.lifetrack.data.entity.StayPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface StayPointDao {

    @Insert
    suspend fun insert(stayPoint: StayPoint): Long

    @Query("SELECT * FROM stay_point WHERE date = :date ORDER BY enterTime ASC")
    suspend fun getByDate(date: String): List<StayPoint>

    @Query("SELECT * FROM stay_point WHERE date = :date ORDER BY enterTime ASC")
    fun getByDateFlow(date: String): Flow<List<StayPoint>>

    @Query("SELECT * FROM stay_point WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC, enterTime ASC")
    suspend fun getByDateRange(startDate: String, endDate: String): List<StayPoint>

    @Query("SELECT poiName, COUNT(*) as visitCount FROM stay_point WHERE date >= :sinceDate GROUP BY poiName ORDER BY visitCount DESC LIMIT :limit")
    suspend fun getTopPoi(sinceDate: String, limit: Int = 5): List<PoiVisitCount>

    @Update
    suspend fun update(stayPoint: StayPoint)

    @Query("DELETE FROM stay_point WHERE date < :beforeDate")
    suspend fun deleteBefore(beforeDate: String): Int
}

data class PoiVisitCount(
    val poiName: String?,
    val visitCount: Int
)
