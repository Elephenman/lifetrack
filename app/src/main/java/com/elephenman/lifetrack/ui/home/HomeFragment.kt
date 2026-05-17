package com.elephenman.lifetrack.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.elephenman.lifetrack.databinding.FragmentHomeBinding
import com.elephenman.lifetrack.service.StayInfo
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var tripAdapter: TripAdapter

    private val dateFormat = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeData()
    }

    private fun setupRecyclerView() {
        tripAdapter = TripAdapter()
        binding.rvTripList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = tripAdapter
        }
    }

    private fun observeData() {
        viewModel.date.observe(viewLifecycleOwner) { date ->
            binding.tvDate.text = dateFormat.format(date)
        }

        viewModel.dailySummary.observe(viewLifecycleOwner) { summary ->
            summary?.let {
                binding.tvDistance.text = formatDistance(it.totalDistance)
                binding.tvOutdoorTime.text = formatDuration(it.totalOutdoorMin)
                binding.tvPlaceCount.text = "${it.stayCount ?: 0}地"
            } ?: run {
                binding.tvDistance.text = "0km"
                binding.tvOutdoorTime.text = "0h"
                binding.tvPlaceCount.text = "0地"
            }
        }

        viewModel.tripItems.observe(viewLifecycleOwner) { items ->
            tripAdapter.submitList(items)
        }

        viewModel.timelineData.observe(viewLifecycleOwner) { segments ->
            binding.timelineView.setData(segments)
        }

        // 实时停留信息 + 每秒计时
        viewModel.currentStayInfo.observe(viewLifecycleOwner) { stayInfo ->
            if (stayInfo != null) {
                binding.cardCurrentStay.visibility = View.VISIBLE
                binding.tvCurrentStayDetail.text = String.format("%.4f, %.4f", stayInfo.latCenter, stayInfo.lngCenter)
                binding.tvCurrentStayTitle.text = if (stayInfo.isStaying) "正在停留" else "定位中"
            } else {
                binding.cardCurrentStay.visibility = View.GONE
            }
        }

        viewModel.stayDurationText.observe(viewLifecycleOwner) { durationText ->
            if (durationText.isNotEmpty()) {
                binding.tvCurrentStayDuration.text = durationText
            }
        }
    }

    private fun formatDistance(distanceM: Float?): String {
        if (distanceM == null) return "0km"
        return if (distanceM >= 1000) String.format("%.1fkm", distanceM / 1000) else "${distanceM.toInt()}m"
    }

    private fun formatDuration(minutes: Int?): String {
        if (minutes == null) return "0h"
        val h = minutes / 60; val m = minutes % 60
        return if (h > 0) "${h}h${m}m" else "${m}m"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}