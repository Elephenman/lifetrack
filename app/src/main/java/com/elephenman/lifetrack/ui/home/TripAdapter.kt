package com.elephenman.lifetrack.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.elephenman.lifetrack.databinding.ItemTripBinding
import java.text.SimpleDateFormat
import java.util.*

class TripAdapter : ListAdapter<TripItem, TripAdapter.TripViewHolder>(TripDiffCallback()) {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val binding = ItemTripBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TripViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TripViewHolder(private val binding: ItemTripBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TripItem) {
            binding.tvIcon.text = item.icon
            binding.tvLabel.text = item.label
            binding.tvTime.text = "${timeFormat.format(Date(item.startTime))} - ${timeFormat.format(Date(item.endTime))}"
            binding.tvDuration.text = item.duration

            // 停留点用不同背景色
            val bgColor = when (item.type) {
                TripItem.Type.STAY -> 0x1A4CAF50  // 浅绿
                TripItem.Type.TRIP -> 0x1AFF9800  // 浅橙
            }
            binding.root.setBackgroundColor(bgColor)
        }
    }
}

class TripDiffCallback : DiffUtil.ItemCallback<TripItem>() {
    override fun areItemsTheSame(oldItem: TripItem, newItem: TripItem) = oldItem.startTime == newItem.startTime && oldItem.type == newItem.type
    override fun areContentsTheSame(oldItem: TripItem, newItem: TripItem) = oldItem == newItem
}
