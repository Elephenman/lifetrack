package com.elephenman.lifetrack.data.entity

import androidx.room.*

/**
 * 每日汇总
 */
@Entity(tableName = "daily_summary")
data class DailySummary(
    @PrimaryKey
    val date: String,                     // YYYY-MM-DD
    val totalDistance: Float? = null,      // 总出行距离(m)
    val totalOutdoorMin: Int? = null,      // 外出时长(min)
    val stayCount: Int? = null,            // 停留地点数
    val firstMoveTime: Long? = null,       // 首次移动时间戳
    val lastMoveTime: Long? = null,        // 最后移动时间戳
    val gpxFilePath: String? = null        // 归档GPX文件路径
)
