package com.elephenman.lifetrack.data.entity

import androidx.room.*

/**
 * 停留点 - 算法处理后生成
 */
@Entity(tableName = "stay_point", indices = [Index("date"), Index("enterTime")])
data class StayPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val date: String,             // YYYY-MM-DD
    val enterTime: Long,          // 进入时间戳(ms)
    val exitTime: Long,           // 离开时间戳(ms)
    val latCenter: Double,        // 停留区域中心纬度
    val lngCenter: Double,        // 停留区域中心经度
    val radius: Float,            // 停留区域半径(m)
    val poiName: String? = null,  // 地点名称
    val poiAddress: String? = null // 地址文本
)
