package com.elephenman.lifetrack.ui.stats

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elephenman.lifetrack.data.entity.DailySummary
import com.elephenman.lifetrack.data.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: LocationRepository
) : ViewModel() {

    private val _weeklyStats = MutableLiveData<List<DailySummary>>()
    val weeklyStats: LiveData<List<DailySummary>> = _weeklyStats

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        loadWeeklyStats()
    }

    fun loadWeeklyStats() {
        viewModelScope.launch {
            val endCal = Calendar.getInstance()
            val startCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -6)
            }
            val startDate = dateFormat.format(startCal.time)
            val endDate = dateFormat.format(endCal.time)

            repository.getAllDailySummariesFlow().collect { allSummaries ->
                val weekly = allSummaries.filter {
                    it.date >= startDate && it.date <= endDate
                }
                _weeklyStats.postValue(weekly)
            }
        }
    }
}
