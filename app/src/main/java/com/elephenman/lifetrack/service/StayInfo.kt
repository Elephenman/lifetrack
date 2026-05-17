package com.elephenman.lifetrack.service

data class StayInfo(
    val latCenter: Double,
    val lngCenter: Double,
    val enterTimeMs: Long,
    val isStaying: Boolean,
    val placeName: String? = null
)