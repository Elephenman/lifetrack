package com.elephenman.lifetrack.ui.map

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elephenman.lifetrack.data.entity.LocationPoint
import com.elephenman.lifetrack.data.entity.StayPoint
import com.elephenman.lifetrack.data.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: LocationRepository
) : ViewModel() {

    private val _locationPoints = MutableLiveData<List<LocationPoint>>()
    val locationPoints: LiveData<List<LocationPoint>> = _locationPoints

    private val _stayPoints = MutableLiveData<List<StayPoint>>()
    val stayPoints: LiveData<List<StayPoint>> = _stayPoints

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        loadToday()
    }

    fun loadToday() {
        val dateStr = dateFormat.format(Date())
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val dayStart = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val dayEnd = calendar.timeInMillis

        viewModelScope.launch {
            _locationPoints.value = repository.getLocationPoints(dayStart, dayEnd)
            _stayPoints.value = repository.getStayPointsByDate(dateStr)
        }
    }

    fun loadDate(date: Date) {
        val dateStr = dateFormat.format(date)
        val calendar = Calendar.getInstance().apply { time = date }
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
        val dayStart = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59); calendar.set(Calendar.SECOND, 59)
        val dayEnd = calendar.timeInMillis

        viewModelScope.launch {
            _locationPoints.value = repository.getLocationPoints(dayStart, dayEnd)
            _stayPoints.value = repository.getStayPointsByDate(dateStr)
        }
    }
}
