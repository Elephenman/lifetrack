package com.elephenman.lifetrack.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elephenman.lifetrack.data.entity.DailySummary
import com.elephenman.lifetrack.data.entity.StayPoint
import com.elephenman.lifetrack.data.entity.TripSegment
import com.elephenman.lifetrack.data.repository.LocationRepository
import com.elephenman.lifetrack.engine.DailySummaryComputer
import com.elephenman.lifetrack.service.LocationTrackingService
import com.elephenman.lifetrack.service.StayInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.*

data class TripItem(
    val type: Type,
    val startTime: Long,
    val endTime: Long,
    val label: String,
    val duration: String,
    val icon: String,
    val transportMode: String? = null
) {
    enum class Type { STAY, TRIP }
}

data class TimelineSegment(
    val startTime: Long,
    val endTime: Long,
    val label: String,
    val color: Int,
    val type: TripItem.Type
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: LocationRepository,
    private val dailySummaryComputer: DailySummaryComputer,
    private val placeNameResolver: com.elephenman.lifetrack.util.PlaceNameResolver
) : ViewModel() {

    private val _date = MutableLiveData<Date>()
    val date: LiveData<Date> = _date

    private val _dailySummary = MutableLiveData<DailySummary?>()
    val dailySummary: LiveData<DailySummary?> = _dailySummary

    private val _tripItems = MutableLiveData<List<TripItem>>()
    val tripItems: LiveData<List<TripItem>> = _tripItems

    private val _timelineData = MutableLiveData<List<TimelineSegment>>()
    val timelineData: LiveData<List<TimelineSegment>> = _timelineData

    private val _currentStayInfo = MutableLiveData<StayInfo?>()
    val currentStayInfo: LiveData<StayInfo?> = _currentStayInfo

    private val _stayDurationText = MutableLiveData<String>()
    val stayDurationText: LiveData<String> = _stayDurationText

    private var stayTimerJob: Job? = null

    private val dateStrFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init { loadToday() }

    fun loadToday() {
        _date.value = Date()
        loadDate(Date())
    }

    fun loadDate(date: Date) {
        val dateStr = dateStrFormat.format(date)
        val isToday = isSameDay(date, Date())

        viewModelScope.launch {
            val existing = repository.getDailySummary(dateStr)
            if (existing == null || isToday) {
                dailySummaryComputer.computeAndSaveDailySummary(dateStr)
            }
            repository.getDailySummaryFlow(dateStr).collect { _dailySummary.postValue(it) }
        }
        viewModelScope.launch {
            combine(
                repository.getStayPointsByDateFlow(dateStr),
                repository.getTripSegmentsByDateFlow(dateStr)
            ) { stays, trips ->
                // 回填 poiName=null 的停留点
                val patched = stays.map { stay ->
                    if (stay.poiName == null) {
                        val name = placeNameResolver.resolve(stay.latCenter, stay.lngCenter)
                        if (name != null) {
                            val p = stay.copy(poiName = name)
                            repository.updateStayPoint(p)
                            p
                        } else stay
                    } else stay
                }
                buildTripItems(patched, trips)
            }.collect { items ->
                _tripItems.postValue(items)
                _timelineData.postValue(items.map { buildTimelineSegment(it) })
            }
        }

        if (isToday) {
            viewModelScope.launch {
                LocationTrackingService.stayInfoFlow.collect { info ->
                    _currentStayInfo.postValue(info)
                    stayTimerJob?.cancel()
                    if (info != null && info.enterTimeMs > 0) {
                        stayTimerJob = viewModelScope.launch {
                            while (true) {
                                _stayDurationText.postValue(formatElapsed(System.currentTimeMillis() - info.enterTimeMs))
                                delay(1000)
                            }
                        }
                    } else {
                        _stayDurationText.postValue("")
                    }
                }
            }
        }
    }

    private fun isSameDay(d1: Date, d2: Date): Boolean {
        val c1 = Calendar.getInstance().apply { time = d1 }
        val c2 = Calendar.getInstance().apply { time = d2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * 合并100m内的停留点为一个条目，显示地名
     */
    private fun buildTripItems(stays: List<StayPoint>, trips: List<TripSegment>): List<TripItem> {
        if (stays.isEmpty()) return emptyList()

        // 合并100m内的相邻停留点
        val merged = mutableListOf<MergedStay>()
        var current = MergedStay(stays[0])

        for (i in 1 until stays.size) {
            val s = stays[i]
            val dist = haversine(current.latCenter, current.lngCenter, s.latCenter, s.lngCenter)
            if (dist <= 100.0) {
                // 合并：扩展时间范围
                current = MergedStay(
                    enterTime = current.enterTime,
                    exitTime = s.exitTime,
                    latCenter = (current.latCenter + s.latCenter) / 2,
                    lngCenter = (current.lngCenter + s.lngCenter) / 2,
                    name = s.poiName ?: current.name
                )
            } else {
                merged.add(current)
                current = MergedStay(s)
            }
        }
        merged.add(current)

        val items = mutableListOf<TripItem>()

        for (stay in merged) {
            val coord = String.format("%.4f, %.4f", stay.latCenter, stay.lngCenter)
            val label = if (stay.name != null) "${stay.name} ($coord)" else coord
            items.add(TripItem(
                type = TripItem.Type.STAY,
                startTime = stay.enterTime,
                endTime = stay.exitTime,
                label = label,
                duration = formatDuration(stay.exitTime - stay.enterTime),
                icon = "📍"
            ))
        }

        // 行程段
        for (trip in trips) {
            items.add(TripItem(
                type = TripItem.Type.TRIP,
                startTime = trip.startTime,
                endTime = trip.endTime,
                label = getTransportLabel(trip.transportMode),
                duration = formatDuration(trip.endTime - trip.startTime),
                icon = getTransportIcon(trip.transportMode),
                transportMode = trip.transportMode
            ))
        }

        return items.sortedBy { it.startTime }
    }

    private data class MergedStay(
        val enterTime: Long,
        val exitTime: Long,
        val latCenter: Double,
        val lngCenter: Double,
        val name: String?
    ) {
        constructor(s: StayPoint) : this(s.enterTime, s.exitTime, s.latCenter, s.lngCenter, s.poiName)
    }

    private fun buildTimelineSegment(item: TripItem): TimelineSegment {
        val color = when (item.type) {
            TripItem.Type.STAY -> android.graphics.Color.parseColor("#4CAF50")
            TripItem.Type.TRIP -> when (item.transportMode) {
                "walk" -> android.graphics.Color.parseColor("#FF9800")
                "bike" -> android.graphics.Color.parseColor("#2196F3")
                "car", "bus" -> android.graphics.Color.parseColor("#9C27B0")
                else -> android.graphics.Color.parseColor("#607D8B")
            }
        }
        return TimelineSegment(item.startTime, item.endTime, item.label, color, item.type)
    }

    private fun formatDuration(ms: Long): String {
        val min = ms / 60000; val h = min / 60; val m = min % 60
        return if (h > 0) "${h}h${m}m" else "${m}min"
    }

    private fun getTransportLabel(mode: String?): String = when (mode) {
        "walk" -> "步行"; "bike" -> "骑行"; "car", "bus" -> "乘车"; "train" -> "火车"; else -> "移动"
    }

    private fun getTransportIcon(mode: String?): String = when (mode) {
        "walk" -> "🚶"; "bike" -> "🚴"; "car", "bus" -> "🚌"; "train" -> "🚄"; else -> "➡️"
    }

    private fun formatElapsed(ms: Long): String {
        val sec = ms / 1000; val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
               else if (m > 0) String.format("%d:%02d", m, s) else "${s}秒"
    }

    private fun haversine(la1: Double, ln1: Double, la2: Double, ln2: Double): Double {
        val r = 6371000.0
        val dLa = Math.toRadians(la2 - la1); val dLn = Math.toRadians(ln2 - ln1)
        val a = sin(dLa/2).let{it*it} + cos(Math.toRadians(la1))*cos(Math.toRadians(la2))*sin(dLn/2).let{it*it}
        return r * 2 * atan2(sqrt(a), sqrt(1-a))
    }
}