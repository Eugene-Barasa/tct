package com.tct.bot.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.tct.bot.R
import com.tct.bot.databinding.FragmentLogsBinding
import com.tct.bot.managers.LogManager

class LogsFragment : Fragment(R.layout.fragment_logs) {
    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!
    
    private val uiHandler = Handler(Looper.getMainLooper())
    private var isUpdatePending = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentLogsBinding.bind(view)
        
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
        
        binding.tvLogsContent.setTextIsSelectable(true)
        binding.tvLogsContent.movementMethod = LinkMovementMethod.getInstance()

        binding.btnClearLogs.setOnClickListener {
            LogManager.clearLogs()
            binding.tvLogsContent.text = "Logs cleared...\n"
        }

        binding.btnCopyLogs.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("TCT Logs", binding.tvLogsContent.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Logs Copied to Clipboard!", Toast.LENGTH_SHORT).show()
        }

        refreshLogsUI()
        
        LogManager.onLogUpdatedCallback = {
            if (!isUpdatePending) {
                isUpdatePending = true
                // Batch up updates and render max 4 times a second
                uiHandler.postDelayed({
                    if (_binding != null) {
                        refreshLogsUI()
                    }
                    isUpdatePending = false
                }, 250)
            }
        }
    }

    private fun refreshLogsUI() {
        val span = LogManager.getFullLogText()
        if (span.isEmpty()) {
            binding.tvLogsContent.text = "Engine is quiet. Awaiting logs...\n"
        } else {
            binding.tvLogsContent.text = span
            binding.scrollLogs.post { binding.scrollLogs.fullScroll(View.FOCUS_DOWN) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LogManager.onLogUpdatedCallback = null
        uiHandler.removeCallbacksAndMessages(null)
        _binding = null
    }
}
