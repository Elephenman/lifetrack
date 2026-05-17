package com.elephenman.lifetrack.ui.stats

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.elephenman.lifetrack.databinding.FragmentStatsBinding
import com.elephenman.lifetrack.data.dao.PoiVisitCount
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StatsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeData()
    }

    private fun observeData() {
        viewModel.weeklyStats.observe(viewLifecycleOwner) { stats ->
            if (stats == null) {
                binding.tvWeekLabel.text = "暂无数据"
                binding.tvActiveDays.text = "0"
                binding.tvTotalDist.text = "0m"
                binding.tvTotalOutdoor.text = "0min"
                binding.tvStayCount.text = "0"
                binding.layoutTopPlaces.removeAllViews()
                binding.layoutDailyBreakdown.removeAllViews()
                return@observe
            }

            binding.tvWeekLabel.text = stats.weekLabel

            binding.tvActiveDays.text = "${stats.activeDays}"

            val distStr = if (stats.totalDistance >= 1000)
                String.format("%.1fkm", stats.totalDistance / 1000) else "${stats.totalDistance.toInt()}m"
            binding.tvTotalDist.text = distStr

            val outStr = if (stats.totalOutdoorMin >= 60)
                "${stats.totalOutdoorMin / 60}h${stats.totalOutdoorMin % 60}m" else "${stats.totalOutdoorMin}min"
            binding.tvTotalOutdoor.text = outStr

            binding.tvStayCount.text = "${stats.stayPlaceCount}"

            // 常去地点
            binding.layoutTopPlaces.removeAllViews()
            if (stats.topPlaces.isEmpty()) {
                addTextRow(binding.layoutTopPlaces, "暂无记录", false)
            } else {
                for (place in stats.topPlaces) {
                    val name = place.poiName ?: "未知地点"
                    addTextRow(binding.layoutTopPlaces, "$name  (${place.visitCount}次)", true)
                }
            }

            // 每日明细
            binding.layoutDailyBreakdown.removeAllViews()
            if (stats.dailyBreakdown.isEmpty()) {
                addTextRow(binding.layoutDailyBreakdown, "暂无记录", false)
            } else {
                for (day in stats.dailyBreakdown) {
                    val dDist = if ((day.totalDistance ?: 0f) >= 1000)
                        String.format("%.1fkm", (day.totalDistance ?: 0f) / 1000) else "${(day.totalDistance ?: 0f).toInt()}m"
                    val dOut = if ((day.totalOutdoorMin ?: 0) >= 60)
                        "${(day.totalOutdoorMin ?: 0) / 60}h${(day.totalOutdoorMin ?: 0) % 60}m" else "${day.totalOutdoorMin ?: 0}min"
                    addTextRow(binding.layoutDailyBreakdown,
                        "${day.date}  出行$dDist | 外出$dOut | ${(day.stayCount ?: 0)}地", true)
                }
            }
        }
    }

    private fun addTextRow(parent: LinearLayout, text: String, isData: Boolean) {
        val tv = TextView(requireContext()).apply {
            this.text = text
            textSize = if (isData) 13f else 12f
            setTextColor(resources.getColor(com.elephenman.lifetrack.R.color.text_secondary, null))
            if (isData) setTypeface(typeface, Typeface.NORMAL)
            setPadding(0, dp(4), 0, dp(4))
        }
        parent.addView(tv)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}