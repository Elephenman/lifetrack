package com.elephenman.lifetrack.ui.calendar

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elephenman.lifetrack.data.entity.DailySummary
import com.elephenman.lifetrack.data.repository.LocationRepository
import com.elephenman.lifetrack.engine.DailySummaryComputer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: LocationRepository,
    private val dailySummaryComputer: DailySummaryComputer
) : ViewModel() {

    private val _selectedDateSummary = MutableLiveData<DailySummary?>()
    val selectedDateSummary: LiveData<DailySummary?> = _selectedDateSummary

    fun loadDate(dateStr: String) {
        viewModelScope.launch {
            val existing = repository.getDailySummary(dateStr)
            if (existing == null) {
                val computed = dailySummaryComputer.computeAndSaveDailySummary(dateStr)
                _selectedDateSummary.postValue(computed)
            } else {
                _selectedDateSummary.postValue(existing)
            }
        }
    }
}