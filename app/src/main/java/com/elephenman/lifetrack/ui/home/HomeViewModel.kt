package com.elephenman.lifetrack.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elephenman.lifetrack.data.entity.DailySummary
import com.elephenman.lifetrack.data.entity.StayPoint
import com.elephenman.lifetrack.data.entity.TripSegment
import com.elephenman.lifetrack.data.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val repository: LocationRepository
) : ViewModel() {

    private val _date = MutableLiveData<Date>()
    val date: LiveData<Date> = _date

    private val _dailySummary = MutableLiveData<DailySummary?>()
    val dailySummary: LiveData<DailySummary?> = _dailySummary

    private val _tripItems = MutableLiveData<List<TripItem>>()
    val tripItems: LiveData<List<TripItem>> = _tripItems

    private val _timelineData = MutableLiveData<List<TimelineSegment>>()
    val timelineData: LiveData<List<TimelineSegment>> = _timelineData

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
        viewModelScope.launch {
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
    }

    private fun buildTripItems(stays: List<StayPoint>, trips: List<TripSegment>, date: Date): List<TripItem> {
        val items = mutableListOf<TripItem>()
        val calendar = Calendar.getInstance().apply { time = date }

        // 开头：0:00到第一个停留点进入
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
            // 添加停留点
            items.add(TripItem(
                type = TripItem.Type.STAY,
                startTime = stay.enterTime,
                endTime = stay.exitTime,
                label = stay.poiName ?: formatCoord(stay.latCenter, stay.lngCenter),
                duration = formatDuration(stay.exitTime - stay.enterTime),
                icon = "📍"
            ))

            // 如果有对应的行程段，添加行程
            val trip = trips.find { it.fromStayId == stay.id || (it.startTime in stay.exitTime..stay.exitTime + 60000) }
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

        // 结尾：最后一个停留点到24:00
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

        // 按时间排序
        return items.sortedBy { it.startTime }
    }

    private fun buildTimelineSegment(item: TripItem, date: Date): TimelineSegment {
        val color = when (item.type) {
            TripItem.Type.STAY -> android.graphics.Color.parseColor("#4CAF50")  // 绿色
            TripItem.Type.TRIP -> when (item.transportMode) {
                "walk" -> android.graphics.Color.parseColor("#FF9800")   // 橙色
                "bike" -> android.graphics.Color.parseColor("#2196F3")   // 蓝色
                "car", "bus" -> android.graphics.Color.parseColor("#9C27B0")  // 紫色
                else -> android.graphics.Color.parseColor("#607D8B")     // 灰蓝
            }
        }
        return TimelineSegment(item.startTime, item.endTime, item.label, color, item.type)
    }

    // --- 工具方法 ---

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
}
