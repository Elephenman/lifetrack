package com.elephenman.lifetrack.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.elephenman.lifetrack.databinding.FragmentStatsBinding
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
            if (stats.isNullOrEmpty()) {
                binding.tvWeeklySummary.text = "暂无数据，开始记录后这里会显示统计"
                return@observe
            }

            val totalDistance = stats.sumOf { (it.totalDistance ?: 0f).toDouble() }
            val totalOutdoor = stats.sumOf { (it.totalOutdoorMin ?: 0) }
            val totalPlaces = stats.sumOf { it.stayCount ?: 0 }
            val activeDays = stats.count { (it.totalOutdoorMin ?: 0) > 0 }

            val distStr = if (totalDistance >= 1000) String.format("%.1fkm", totalDistance / 1000) else "${totalDistance.toInt()}m"
            val outStr = if (totalOutdoor >= 60) "${totalOutdoor / 60}h${totalOutdoor % 60}m" else "${totalOutdoor}min"

            binding.tvWeeklySummary.text = "近7天统计\n" +
                "活跃天数：${activeDays}天\n" +
                "总出行距离：$distStr\n" +
                "总外出时长：$outStr\n" +
                "停留地点：${totalPlaces}个"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
