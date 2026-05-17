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
    private val dailySummaryComputer: DailySummaryComputer
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

    // 实时计时器：每秒刷新停留时长
    private val _stayDurationText = MutableLiveData<String>()
    val stayDurationText: LiveData<String> = _stayDurationText

    private var stayTimerJob: Job? = null

    private val dateStrFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        loadToday()
    }

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
            repository.getDailySummaryFlow(dateStr).collect { summary ->
                _dailySummary.postValue(summary)
            }
        }
        viewModelScope.launch {
            combine(
                repository.getStayPointsByDateFlow(dateStr),
                repository.getTripSegmentsByDateFlow(dateStr)
            ) { stays, trips ->
                buildTripItems(stays, trips, date)
            }.collect { items ->
                _tripItems.postValue(items)
                _timelineData.postValue(items.map { buildTimelineSegment(it, date) })
            }
        }

        // 监听实时停留状态
        if (isToday) {
            viewModelScope.launch {
                LocationTrackingService.stayInfoFlow.collect { info ->
                    _currentStayInfo.postValue(info)
                    // 启动/停止计时器
                    stayTimerJob?.cancel()
                    if (info != null && info.enterTimeMs > 0) {
                        stayTimerJob = viewModelScope.launch {
                            while (true) {
                                val elapsed = System.currentTimeMillis() - info.enterTimeMs
                                _stayDurationText.postValue(formatElapsed(elapsed))
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
        val cal1 = Calendar.getInstance().apply { time = d1 }
        val cal2 = Calendar.getInstance().apply { time = d2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun buildTripItems(stays: List<StayPoint>, trips: List<TripSegment>, date: Date): List<TripItem> {
        val items = mutableListOf<TripItem>()
        val calendar = Calendar.getInstance().apply { time = date }

        if (stays.isNotEmpty() && stays.first().enterTime > getDayStart(date)) {
            items.add(TripItem(
                type = TripItem.Type.STAY,
                startTime = getDayStart(date),
                endTime = stays.first().enterTime,
                label = "在家",
                duration = formatDuration(stays.first().enterTime - getDayStart(date)),
                icon = "🏠"
            ))
        }

        stays.forEachIndexed { index, stay ->
            items.add(TripItem(
                type = TripItem.Type.STAY,
                startTime = stay.enterTime,
                endTime = stay.exitTime,
                label = stay.poiName ?: formatCoord(stay.latCenter, stay.lngCenter),
                duration = formatDuration(stay.exitTime - stay.enterTime),
                icon = "📍"
            ))

            val trip = trips.find { it.fromStayId == stay.id || (it.startTime >= stay.exitTime && it.startTime <= stay.exitTime + 60000) }
            if (trip != null) {
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
        }

        if (stays.isNotEmpty() && stays.last().exitTime < getDayEnd(date)) {
            items.add(TripItem(
                type = TripItem.Type.STAY,
                startTime = stays.last().exitTime,
                endTime = getDayEnd(date),
                label = "在家",
                duration = formatDuration(getDayEnd(date) - stays.last().exitTime),
                icon = "🏠"
            ))
        }

        return items.sortedBy { it.startTime }
    }

    private fun buildTimelineSegment(item: TripItem, date: Date): TimelineSegment {
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

    private fun getDayStart(date: Date): Long {
        val cal = Calendar.getInstance().apply { time = date; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return cal.timeInMillis
    }

    private fun getDayEnd(date: Date): Long {
        val cal = Calendar.getInstance().apply { time = date; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }
        return cal.timeInMillis
    }

    private fun formatDuration(ms: Long): String {
        val minutes = ms / 60000
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h${m}m" else "${m}min"
    }

    private fun formatCoord(lat: Double, lng: Double): String {
        return String.format("%.4f, %.4f", lat, lng)
    }

    private fun getTransportLabel(mode: String?): String = when (mode) {
        "walk" -> "步行"
        "bike" -> "骑行"
        "car", "bus" -> "乘车"
        "train" -> "火车"
        else -> "移动"
    }

    private fun getTransportIcon(mode: String?): String = when (mode) {
        "walk" -> "🚶"
        "bike" -> "🚴"
        "car", "bus" -> "🚌"
        "train" -> "🚄"
        else -> "➡️"
    }

    private fun formatElapsed(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
               else if (m > 0) String.format("%d:%02d", m, s)
               else "${s}秒"
    }
}