package com.elephenman.lifetrack.ui.calendar

import android.graphics.Color
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
            viewModel.loadDate(dateFormat.format(cal.time))
        }
        viewModel.loadDate(dateFormat.format(Date()))
        observeData()
    }

    private fun observeData() {
        viewModel.dayTimeline.observe(viewLifecycleOwner) { timeline ->
            binding.timelineContainer.removeAllViews()
            if (timeline == null || (timeline.stays.isEmpty() && timeline.trips.isEmpty())) {
                binding.timelineContainer.addView(makeEmptyView())
                return@observe
            }

            val events = mutableListOf<TimelineEvent>()

            for (stay in timeline.stays) {
                val label = stay.poiName ?: String.format("%.4f, %.4f", stay.latCenter, stay.lngCenter)
                val durMin = (stay.exitTime - stay.enterTime) / 60000
                events.add(TimelineEvent(stay.enterTime, stay.exitTime, label, "停留${durMin}分钟", Color.parseColor("#4CAF50")))
            }

            for (trip in timeline.trips) {
                val mode = when (trip.transportMode) {
                    "walk" -> "步行"; "bike" -> "骑行"; "car", "bus" -> "乘车"; "train" -> "火车"; else -> "移动"
                }
                val durMin = (trip.endTime - trip.startTime) / 60000
                val distStr = if ((trip.distanceM ?: 0f) >= 1000)
                    String.format("%.1fkm", (trip.distanceM ?: 0f) / 1000) else "${(trip.distanceM ?: 0f).toInt()}m"
                val color = when (trip.transportMode) {
                    "walk" -> Color.parseColor("#FF9800"); "bike" -> Color.parseColor("#2196F3")
                    "car", "bus" -> Color.parseColor("#9C27B0"); else -> Color.parseColor("#607D8B")
                }
                events.add(TimelineEvent(trip.startTime, trip.endTime, mode, "${durMin}分钟 $distStr", color))
            }

            events.sortBy { it.startTime }

            for (event in events) {
                binding.timelineContainer.addView(makeEventRow(event))
            }
        }
    }

    private fun makeEmptyView(): TextView {
        return TextView(requireContext()).apply {
            text = "当天无记录"
            textSize = 14f
            setPadding(48, 48, 48, 48)
            setTextColor(Color.parseColor("#999999"))
        }
    }

    private fun makeEventRow(event: TimelineEvent): LinearLayout {
        val dp8 = dp(8)
        val dp12 = dp(12)
        val dp16 = dp(16)

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp8, 0, dp8) }
        }

        // 时间列：开始-结束
        val timeCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        timeCol.addView(TextView(requireContext()).apply {
            text = formatTime(event.startTime)
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            typeface = Typeface.MONOSPACE
        })
        timeCol.addView(TextView(requireContext()).apply {
            text = formatTime(event.endTime)
            textSize = 13f
            setTextColor(Color.parseColor("#999999"))
            typeface = Typeface.MONOSPACE
        })

        // 色条
        val bar = View(requireContext()).apply {
            setBackgroundColor(event.color)
            layoutParams = LinearLayout.LayoutParams(dp(4), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                setMargins(dp8, 0, dp12, 0)
            }
        }

        // 内容列
        val contentCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        contentCol.addView(TextView(requireContext()).apply {
            text = event.label
            textSize = 15f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
        })
        contentCol.addView(TextView(requireContext()).apply {
            text = event.detail
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, dp(4), 0, 0)
        })

        row.addView(timeCol)
        row.addView(bar)
        row.addView(contentCol)
        return row
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun formatTime(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class TimelineEvent(
    val startTime: Long,
    val endTime: Long,
    val label: String,
    val detail: String,
    val color: Int
)