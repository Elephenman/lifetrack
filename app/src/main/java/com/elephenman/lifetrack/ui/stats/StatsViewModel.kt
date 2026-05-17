package com.elephenman.lifetrack.ui.stats

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elephenman.lifetrack.data.entity.DailySummary
import com.elephenman.lifetrack.data.entity.StayPoint
import com.elephenman.lifetrack.data.repository.LocationRepository
import com.elephenman.lifetrack.engine.DailySummaryComputer
import com.elephenman.lifetrack.data.dao.PoiVisitCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class WeeklyStats(
    val weekLabel: String,
    val activeDays: Int,
    val totalDistance: Float,
    val totalOutdoorMin: Int,
    val stayPlaceCount: Int,
    val topPlaces: List<PoiVisitCount>,
    val dailyBreakdown: List<DailySummary>
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: LocationRepository,
    private val dailySummaryComputer: DailySummaryComputer
) : ViewModel() {

    private val _weeklyStats = MutableLiveData<WeeklyStats?>()
    val weeklyStats: LiveData<WeeklyStats?> = _weeklyStats

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init { loadThisWeek() }

    fun loadThisWeek() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val startStr = dateFormat.format(cal.time)
        val endCal = Calendar.getInstance()
        endCal.set(Calendar.HOUR_OF_DAY, 23)
        endCal.set(Calendar.MINUTE, 59)
        val endStr = dateFormat.format(endCal.time)
        loadWeek(startStr, endStr)
    }

    fun loadWeek(startDate: String, endDate: String) {
        viewModelScope.launch {
            val summaries = repository.getDailySummariesByDateRange(startDate, endDate)
            if (summaries.isEmpty()) {
                // Compute missing summaries for each day in the range
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val start = sdf.parse(startDate)!!
                val end = sdf.parse(endDate)!!
                val cal = Calendar.getInstance()
                cal.time = start
                while (!cal.time.after(end)) {
                    val ds = dateFormat.format(cal.time)
                    dailySummaryComputer.computeAndSaveDailySummary(ds)
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
                val refreshed = repository.getDailySummariesByDateRange(startDate, endDate)
                buildWeeklyStats(startDate, endDate, refreshed)
            } else {
                buildWeeklyStats(startDate, endDate, summaries)
            }
        }
    }

    private suspend fun buildWeeklyStats(startDate: String, endDate: String, summaries: List<DailySummary>) {
        val activeDays = summaries.size
        val totalDist = summaries.sumOf { (it.totalDistance ?: 0f).toDouble() }.toFloat()
        val totalOutdoor = summaries.sumOf { it.totalOutdoorMin ?: 0 }
        val stayCount = summaries.sumOf { it.stayCount ?: 0 }

        val topPlaces = repository.getTopPoi(startDate, 5)

        val weekLabel = "$startDate ~ $endDate"
        _weeklyStats.postValue(WeeklyStats(
            weekLabel = weekLabel,
            activeDays = activeDays,
            totalDistance = totalDist,
            totalOutdoorMin = totalOutdoor,
            stayPlaceCount = stayCount,
            topPlaces = topPlaces,
            dailyBreakdown = summaries
        ))
    }
}