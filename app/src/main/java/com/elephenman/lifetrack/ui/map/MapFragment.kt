package com.elephenman.lifetrack.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.elephenman.lifetrack.databinding.FragmentMapBinding
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@AndroidEntryPoint
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapViewModel by viewModels()
    private lateinit var mapView: MapView
    private var locationOverlay: MyLocationNewOverlay? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)

        // 初始化OSMDroid配置
        Configuration.getInstance().load(requireContext(), requireContext().getSharedPreferences("osmdroid", 0))
        Configuration.getInstance().userAgentValue = "com.elephenman.lifetrack"

        mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)

        // 添加实时位置图层
        setupLocationOverlay()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeData()
    }

    private fun setupLocationOverlay() {
        val provider = GpsMyLocationProvider(requireContext())
        locationOverlay = MyLocationNewOverlay(provider, mapView).apply {
            enableMyLocation()
            enableAutoStop = false  // 持续追踪，不自动停止
            // 设置定位图标的锚点
            setPersonAnchor(0.5f, 0.5f)
            // 方向箭头
            isDrawAccuracyEnabled = true
        }
        mapView.overlays.add(locationOverlay)

        // 首次定位成功后移动到当前位置
        locationOverlay?.runOnFirstFix {
            activity?.runOnUiThread {
                val myLocation = locationOverlay?.myLocation
                if (myLocation != null) {
                    mapView.controller.animateTo(GeoPoint(myLocation.latitude, myLocation.longitude))
                    mapView.controller.setZoom(16)
                }
            }
        }
    }

    private fun observeData() {
        // 独立 observe 轨迹点
        viewModel.locationPoints.observe(viewLifecycleOwner) { points ->
            redrawMap(points, viewModel.stayPoints.value ?: emptyList())
        }

        // 独立 observe 停留点
        viewModel.stayPoints.observe(viewLifecycleOwner) { stays ->
            redrawMap(viewModel.locationPoints.value ?: emptyList(), stays)
        }
    }

    private fun redrawMap(points: List<com.elephenman.lifetrack.data.entity.LocationPoint>, stays: List<com.elephenman.lifetrack.data.entity.StayPoint>) {
        // 保留位置图层，清除其他覆盖物
        val locationOverlayIndex = mapView.overlays.indexOf(locationOverlay)
        mapView.overlays.clear()
        if (locationOverlay != null) {
            mapView.overlays.add(locationOverlay)
        }

        // 绘制轨迹线
        if (points.isNotEmpty()) {
            val trajectoryPoints = points.map { GeoPoint(it.latitude, it.longitude) }
            val polyline = Polyline().apply {
                setPoints(trajectoryPoints)
                color = android.graphics.Color.parseColor("#2196F3")
                width = 4f
            }
            mapView.overlays.add(polyline)

            // 移动到轨迹中心
            val center = trajectoryPoints[trajectoryPoints.size / 2]
            mapView.controller.setCenter(center)
            mapView.controller.setZoom(15)
        }

        // 绘制停留点标记
        stays.forEach { stay ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(stay.latCenter, stay.lngCenter)
                title = stay.poiName ?: "未知地点"
                snippet = "停留${formatDuration(stay.exitTime - stay.enterTime)}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(marker)
        }

        mapView.invalidate()
    }

    private fun formatDuration(ms: Long): String {
        val minutes = ms / 60000
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h${m}m" else "${m}min"
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // 确保位置追踪继续
        locationOverlay?.enableMyLocation()
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
