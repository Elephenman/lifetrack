package com.elephenman.lifetrack.ui.calendar

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elephenman.lifetrack.data.entity.DailySummary
import com.elephenman.lifetrack.data.entity.StayPoint
import com.elephenman.lifetrack.data.entity.TripSegment
import com.elephenman.lifetrack.data.repository.LocationRepository
import com.elephenman.lifetrack.engine.DailySummaryComputer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DayTimeline(
    val stays: List<StayPoint>,
    val trips: List<TripSegment>,
    val summary: DailySummary?
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: LocationRepository,
    private val dailySummaryComputer: DailySummaryComputer
) : ViewModel() {

    private val _dayTimeline = MutableLiveData<DayTimeline?>()
    val dayTimeline: LiveData<DayTimeline?> = _dayTimeline

    fun loadDate(dateStr: String) {
        viewModelScope.launch {
            val summary = repository.getDailySummary(dateStr)
                ?: dailySummaryComputer.computeAndSaveDailySummary(dateStr)
            val stays = repository.getStayPointsByDate(dateStr)
            val trips = repository.getTripSegmentsByDate(dateStr)
            _dayTimeline.postValue(DayTimeline(stays, trips, summary))
        }
    }
}