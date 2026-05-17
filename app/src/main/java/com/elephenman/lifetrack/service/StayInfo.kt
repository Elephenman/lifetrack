package com.elephenman.lifetrack.service

data class StayInfo(
    val latCenter: Double,
    val lngCenter: Double,
    val enterTimeMs: Long,       // 进入当前位置的时间戳(ms)
    val isStaying: Boolean       // 是否处于停留状态(>5min)
)