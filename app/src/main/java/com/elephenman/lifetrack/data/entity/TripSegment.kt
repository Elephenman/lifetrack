package com.elephenman.lifetrack.data.entity

import androidx.room.*

/**
 * 行程段 - 两个停留点之间的移动
 */
@Entity(
    tableName = "trip_segment",
    foreignKeys = [
        ForeignKey(entity = StayPoint::class, parentColumns = ["id"], childColumns = ["fromStayId"]),
        ForeignKey(entity = StayPoint::class, parentColumns = ["id"], childColumns = ["toStayId"])
    ],
    indices = [Index("date"), Index("fromStayId"), Index("toStayId")]
)
data class TripSegment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val date: String,                 // YYYY-MM-DD
    val startTime: Long,              // 开始时间戳(ms)
    val endTime: Long,                // 结束时间戳(ms)
    val fromStayId: Long? = null,     // 起点停留点ID
    val toStayId: Long? = null,       // 终点停留点ID
    val distanceM: Float? = null,     // 距离(m)
    val transportMode: String? = null,// walk/bike/bus/car/unknown
    val avgSpeed: Float? = null       // 平均速度(m/s)
)
