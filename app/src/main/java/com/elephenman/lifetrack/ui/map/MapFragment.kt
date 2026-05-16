package com.elephenman.lifetrack.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.elephenman.lifetrack.databinding.FragmentMapBinding
import com.elephenman.lifetrack.ui.home.TimelineSegment
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker

@AndroidEntryPoint
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapViewModel by viewModels()
    private lateinit var mapView: MapView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)

        // 初始化OSMDroid配置
        Configuration.getInstance().load(requireContext(), requireContext().getSharedPreferences("osmdroid", 0))
        Configuration.getInstance().userAgentValue = "com.elephenman.lifetrack"

        mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeData()
    }

    private fun observeData() {
        viewModel.locationPoints.observe(viewLifecycleOwner) { points ->
            if (points.isEmpty()) return@observe

            // 清除旧覆盖物
            mapView.overlays.clear()

            // 绘制轨迹线
            val trajectoryPoints = points.map { GeoPoint(it.latitude, it.longitude) }
            val polyline = Polyline().apply {
                setPoints(trajectoryPoints)
                color = android.graphics.Color.parseColor("#2196F3")
                width = 4f
            }
            mapView.overlays.add(polyline)

            // 绘制停留点标记
            viewModel.stayPoints.observe(viewLifecycleOwner) { stays ->
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

            // 移动到轨迹中心
            val center = trajectoryPoints[trajectoryPoints.size / 2]
            mapView.controller.setCenter(center)
            mapView.controller.setZoom(15)
        }
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
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
