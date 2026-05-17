package com.elephenman.lifetrack.ui.map

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elephenman.lifetrack.data.entity.LocationPoint
import com.elephenman.lifetrack.data.entity.StayPoint
import com.elephenman.lifetrack.data.entity.TripSegment
import com.elephenman.lifetrack.data.repository.LocationRepository
import com.elephenman.lifetrack.util.PlaceNameResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: LocationRepository,
    private val placeNameResolver: PlaceNameResolver
) : ViewModel() {

    private val _locationPoints = MutableLiveData<List<LocationPoint>>()
    val locationPoints: LiveData<List<LocationPoint>> = _locationPoints

    private val _stayPoints = MutableLiveData<List<StayPoint>>()
    val stayPoints: LiveData<List<StayPoint>> = _stayPoints

    private val _tripSegments = MutableLiveData<List<TripSegment>>()
    val tripSegments: LiveData<List<TripSegment>> = _tripSegments

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init { loadToday() }

    fun loadToday() = loadDate(Date())

    fun loadDate(date: Date) {
        val dateStr = dateFormat.format(date)
        viewModelScope.launch {
            val stays = repository.getStayPointsByDate(dateStr)
            _tripSegments.value = repository.getTripSegmentsByDate(dateStr)

            // 回填 poiName=null 的停留点
            val updated = mutableListOf<StayPoint>()
            for (stay in stays) {
                if (stay.poiName == null) {
                    val name = placeNameResolver.resolve(stay.latCenter, stay.lngCenter)
                    if (name != null) {
                        val patched = stay.copy(poiName = name)
                        repository.updateStayPoint(patched)
                        updated.add(patched)
                    } else {
                        updated.add(stay)
                    }
                } else {
                    updated.add(stay)
                }
            }
            _stayPoints.value = updated
        }
    }
}