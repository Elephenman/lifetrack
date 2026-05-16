package com.elephenman.lifetrack.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.elephenman.lifetrack.databinding.FragmentCalendarBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
