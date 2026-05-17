package com.elephenman.lifetrack.ui.map

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.elephenman.lifetrack.databinding.FragmentMapBinding
import com.elephenman.lifetrack.service.LocationTrackingService
import com.elephenman.lifetrack.service.StayInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@AndroidEntryPoint
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapViewModel by viewModels()
    private lateinit var mapView: MapView
    private var locationOverlay: MyLocationNewOverlay? = null
    private lateinit var tvCoordInfo: TextView
    private lateinit var tvAccuracy: TextView

    private val gaodeUrls = arrayOf(
        "https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x={x}&y={y}&z={z}",
        "https://webrd02.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x={x}&y={y}&z={z}",
        "https://webrd03.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x={x}&y={y}&z={z}",
        "https://webrd04.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x={x}&y={y}&z={z}"
    )

    private val gaodeTileSource = object : OnlineTileSourceBase(
        "GaodeMap", 0, 18, 256, ".png", gaodeUrls, "© 高德地图"
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return gaodeUrls[x % gaodeUrls.size]
                .replace("{x}", x.toString()).replace("{y}", y.toString()).replace("{z}", z.toString())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        Configuration.getInstance().load(requireContext(), requireContext().getSharedPreferences("osmdroid", 0))
        Configuration.getInstance().userAgentValue = "com.elephenman.lifetrack"

        mapView = binding.mapView
        mapView.setTileSource(gaodeTileSource)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)

        tvCoordInfo = binding.tvCoordInfo
        tvAccuracy = binding.tvAccuracy

        setupLocationOverlay()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.fabMyLocation.setOnClickListener { centerOnMyLocation() }
        observeData()
    }

    private fun setupLocationOverlay() {
        val provider = GpsMyLocationProvider(requireContext())
        locationOverlay = MyLocationNewOverlay(provider, mapView).apply {
            enableMyLocation()
            enableAutoStop = false
            setPersonAnchor(0.5f, 0.5f)
            isDrawAccuracyEnabled = true
        }
        mapView.overlays.add(locationOverlay)
        locationOverlay?.runOnFirstFix {
            activity?.runOnUiThread {
                val loc = locationOverlay?.myLocation
                if (loc != null) {
                    mapView.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
                    mapView.controller.setZoom(16)
                    updateCoordDisplay(loc.latitude, loc.longitude, locationOverlay?.lastFix?.accuracy)
                }
            }
        }
    }

    private fun updateCoordDisplay(lat: Double, lng: Double, accuracy: Float?) {
        tvCoordInfo.text = String.format("%.6f, %.6f", lat, lng)
        if (accuracy != null) {
            tvAccuracy.text = String.format("±%.0fm", accuracy)
            tvAccuracy.setTextColor(
                when {
                    accuracy < 10 -> Color.parseColor("#4CAF50")
                    accuracy < 30 -> Color.parseColor("#FF9800")
                    else -> Color.parseColor("#F44336")
                }
            )
        } else { tvAccuracy.text = "" }
    }

    private fun observeData() {
        // 停留点 + 行程段 → 绘制足迹
        viewModel.stayPoints.observe(viewLifecycleOwner) { stays ->
            drawFootprint(stays, viewModel.tripSegments.value ?: emptyList())
        }
        viewModel.tripSegments.observe(viewLifecycleOwner) { trips ->
            drawFootprint(viewModel.stayPoints.value ?: emptyList(), trips)
        }
        // GPS轨迹线
        viewModel.locationPoints.observe(viewLifecycleOwner) { points ->
            drawTrajectory(points)
        }

        // 实时停留信息 → 坐标显示
        viewLifecycleOwner.lifecycleScope.launch {
            LocationTrackingService.stayInfoFlow.collect { info ->
                if (info != null) {
                    updateCoordDisplay(info.latCenter, info.lngCenter, null)
                }
            }
        }
    }

    private fun drawFootprint(stays: List<com.elephenman.lifetrack.data.entity.StayPoint>,
                               trips: List<com.elephenman.lifetrack.data.entity.TripSegment>) {
        // 保留位置图层 + GPS轨迹线
        val trajectory = mapView.overlays.find { it is Polyline && it.id == "gps_trajectory" }
        mapView.overlays.clear()
        if (locationOverlay != null) mapView.overlays.add(locationOverlay)
        if (trajectory != null) mapView.overlays.add(trajectory)

        if (stays.isEmpty()) { mapView.invalidate(); return }

        val allPoints = mutableListOf<GeoPoint>()

        // 先画行程轨迹线（底层）
        for (trip in trips) {
            val from = stays.find { it.id == trip.fromStayId }
            val to = stays.find { it.id == trip.toStayId }
            if (from != null && to != null) {
                val line = Polyline().apply {
                    setPoints(listOf(
                        GeoPoint(from.latCenter, from.lngCenter),
                        GeoPoint(to.latCenter, to.lngCenter)
                    ))
                    outlinePaint.apply {
                        color = when (trip.transportMode) {
                            "walk" -> Color.parseColor("#E65100")
                            "bike" -> Color.parseColor("#1565C0")
                            "car", "bus" -> Color.parseColor("#6A1B9A")
                            else -> Color.parseColor("#37474F")
                        }
                        strokeWidth = 8f
                        isAntiAlias = true
                    }
                }
                mapView.overlays.add(line)
            }
        }

        // 再画停留点标记（上层，更醒目）
        for (stay in stays) {
            val pt = GeoPoint(stay.latCenter, stay.lngCenter)
            allPoints.add(pt)
            val coord = String.format("%.4f, %.4f", stay.latCenter, stay.lngCenter)
            val name = if (stay.poiName != null) "${stay.poiName} ($coord)" else coord
            val durMin = (stay.exitTime - stay.enterTime) / 60000

            val marker = Marker(mapView).apply {
                position = pt
                title = name
                snippet = "停留${durMin}分钟"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(marker)
        }

        // 缩放到包含所有停留点
        if (allPoints.size >= 2) {
            val bb = BoundingBox.fromGeoPoints(allPoints)
            mapView.zoomToBoundingBox(bb, false, 80)
        }

        mapView.invalidate()
    }

    private fun drawTrajectory(points: List<com.elephenman.lifetrack.data.entity.LocationPoint>) {
        // 移除旧轨迹线
        val old = mapView.overlays.find { it is Polyline && it.id == "gps_trajectory" }
        if (old != null) mapView.overlays.remove(old)
        if (points.size < 2) { mapView.invalidate(); return }

        val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
        val line = Polyline().apply {
            id = "gps_trajectory"
            setPoints(geoPoints)
            outlinePaint.apply {
                color = Color.parseColor("#AA2196F3")
                strokeWidth = 5f
                isAntiAlias = true
                alpha = 180
            }
        }
        // 插入到位置图层下方、停留点下方
        val insertIdx = if (mapView.overlays.any { it is Marker }) {
            mapView.overlays.indexOfLast { it is Marker }
        } else {
            mapView.overlays.size
        }
        mapView.overlays.add(insertIdx, line)
        mapView.invalidate()
    }

    private fun centerOnMyLocation() {
        val loc = locationOverlay?.myLocation
        if (loc != null) {
            mapView.controller.animateTo(loc)
            mapView.controller.setZoom(16)
        } else {
            android.widget.Toast.makeText(requireContext(), "暂未获取定位", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        locationOverlay?.enableMyLocation()
        locationOverlay?.runOnFirstFix {
            activity?.runOnUiThread {
                val loc = locationOverlay?.myLocation
                if (loc != null) updateCoordDisplay(loc.latitude, loc.longitude, locationOverlay?.lastFix?.accuracy)
            }
        }
        viewModel.loadToday()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationOverlay?.disableMyLocation()
        locationOverlay = null
        _binding = null
    }
}