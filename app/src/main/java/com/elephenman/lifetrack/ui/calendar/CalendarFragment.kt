package com.elephenman.lifetrack.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.elephenman.lifetrack.databinding.FragmentCalendarBinding
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CalendarViewModel by viewModels()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormat = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCalendar()
        observeData()
    }

    private fun setupCalendar() {
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance().apply {
                set(year, month, dayOfMonth)
            }
            val dateStr = dateFormat.format(calendar.time)
            viewModel.loadDate(dateStr)
        }
    }

    private fun observeData() {
        viewModel.selectedDateSummary.observe(viewLifecycleOwner) { summary ->
            summary?.let {
                val distance = it.totalDistance?.let { d ->
                    if (d >= 1000) String.format("%.1fkm", d / 1000) else "${d.toInt()}m"
                } ?: "0km"
                val outdoor = it.totalOutdoorMin?.let { m ->
                    val h = m / 60; val min = m % 60
                    if (h > 0) "${h}h${min}m" else "${min}min"
                } ?: "0min"

                binding.tvSummary.text = "出行 $distance | 外出 $outdoor | ${it.stayCount ?: 0}地"
            } ?: run {
                binding.tvSummary.text = "当天无数据"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
