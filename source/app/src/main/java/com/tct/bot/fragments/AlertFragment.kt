package com.tct.bot.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.tct.bot.R
import com.tct.bot.databinding.FragmentAlertBinding

class AlertFragment : Fragment(R.layout.fragment_alert) {
    private var _binding: FragmentAlertBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(errorMsg: String) = AlertFragment().apply {
            arguments = Bundle().apply { putString("error", errorMsg) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentAlertBinding.bind(view)
        
        binding.tvAlertMessage.text = arguments?.getString("error") ?: "An unknown error occurred."
        
        binding.btnDismissAlert.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
