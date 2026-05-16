package com.elephenman.lifetrack.data.entity

import androidx.room.*

/**
 * GPS定位原始记录
 */
@Entity(tableName = "location_point", indices = [Index("timestamp")])
data class LocationPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val timestamp: Long,          // Unix时间戳(ms)
    val latitude: Double,         // 纬度
    val longitude: Double,        // 经度
    val altitude: Double? = null, // 海拔(m)
    val accuracy: Float? = null,  // 精度(m)
    val speed: Float? = null,     // 速度(m/s)
    val provider: String,         // gps/network/passive
    val batteryPct: Int? = null   // 记录时电量(%)
)
