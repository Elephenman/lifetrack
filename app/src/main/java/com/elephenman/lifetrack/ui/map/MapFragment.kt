package com.elephenman.lifetrack.ui.map

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.elephenman.lifetrack.databinding.FragmentMapBinding
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
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
    private lateinit var tvCoordInfo: TextView
    private lateinit var tvAccuracy: TextView

    /**
     * 自定义瓦片源：支持URL模板替换 {x}, {y}, {z}
     * OSMDroid 6.1.x中 getTileURLString 参数是 long (MapTileIndex)
     * getBaseUrl()只返回单个String，需要自己保存URL数组
     */
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
                .replace("{x}", x.toString())
                .replace("{y}", y.toString())
                .replace("{z}", z.toString())
        }
    }

    // OSM Mapnik（备选，国内可能慢）
    private val osmTileSource = object : OnlineTileSourceBase(
        "Mapnik", 0, 19, 256, ".png",
        arrayOf("https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
        "© OpenStreetMap"
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "https://tile.openstreetmap.org/$z/$x/$y.png"
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)

        // 初始化OSMDroid配置
        Configuration.getInstance().load(requireContext(), requireContext().getSharedPreferences("osmdroid", 0))
        Configuration.getInstance().userAgentValue = "com.elephenman.lifetrack"

        mapView = binding.mapView
        // 使用高德地图瓦片（国内可直连，中文标注）
        Log.d("LifeTrack", "Setting tile source to GaodeMap")
        mapView.setTileSource(gaodeTileSource)
        // 强制覆盖OSMDroid默认tile source（避免Configuration缓存旧值）
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)

        tvCoordInfo = binding.tvCoordInfo
        tvAccuracy = binding.tvAccuracy

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
            enableAutoStop = false  // 持续追踪
            setPersonAnchor(0.5f, 0.5f)
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
                    updateCoordDisplay(myLocation.latitude, myLocation.longitude, locationOverlay?.lastFix?.accuracy)
                }
            }
        }
    }

    /**
     * 更新坐标信息显示
     */
    private fun updateCoordDisplay(lat: Double, lng: Double, accuracy: Float?) {
        tvCoordInfo.text = String.format("%.6f, %.6f", lat, lng)
        if (accuracy != null) {
            tvAccuracy.text = String.format("±%.0fm", accuracy)
            tvAccuracy.setTextColor(
                when {
                    accuracy < 10 -> android.graphics.Color.parseColor("#4CAF50")  // 绿色 - 精度高
                    accuracy < 30 -> android.graphics.Color.parseColor("#FF9800")  // 橙色 - 中等
                    else -> android.graphics.Color.parseColor("#F44336")           // 红色 - 精度低
                }
            )
        } else {
            tvAccuracy.text = ""
        }
    }

    private fun observeData() {
        viewModel.locationPoints.observe(viewLifecycleOwner) { points ->
            redrawMap(points, viewModel.stayPoints.value ?: emptyList())
        }

        viewModel.stayPoints.observe(viewLifecycleOwner) { stays ->
            redrawMap(viewModel.locationPoints.value ?: emptyList(), stays)
        }
    }

    private fun redrawMap(points: List<com.elephenman.lifetrack.data.entity.LocationPoint>, stays: List<com.elephenman.lifetrack.data.entity.StayPoint>) {
        // 保留位置图层，清除其他覆盖物
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
        locationOverlay?.enableMyLocation()

        // 持续更新坐标显示
        locationOverlay?.runOnFirstFix {
            activity?.runOnUiThread {
                val loc = locationOverlay?.myLocation
                if (loc != null) {
                    updateCoordDisplay(loc.latitude, loc.longitude, locationOverlay?.lastFix?.accuracy)
                }
            }
        }
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
