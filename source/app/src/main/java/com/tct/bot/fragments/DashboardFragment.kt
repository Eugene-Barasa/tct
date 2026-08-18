package com.tct.bot.fragments

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import com.tct.bot.BotService
import com.tct.bot.MainActivity
import com.tct.bot.R
import com.tct.bot.databinding.FragmentDashboardBinding
import com.tct.bot.managers.ConfigManager
import mobile.Mobile
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var configManager: ConfigManager
    private val handler = Handler(Looper.getMainLooper())
    private var uptimeRunnable: Runnable? = null

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getStringExtra("status")) {
                "ERROR" -> {
                    updateStatusUI(BotState.STOPPED)
                    val msg = intent.getStringExtra("message") ?: "Unknown Error"
                    (activity as MainActivity).navigateTo(AlertFragment.newInstance(msg))
                }
                "RUNNING" -> updateStatusUI(BotState.RUNNING)
                "RESTARTING" -> updateStatusUI(BotState.RESTARTING)
                "STOPPED" -> updateStatusUI(BotState.STOPPED)
            }
        }
    }

    enum class BotState { RUNNING, STOPPED, RESTARTING }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentDashboardBinding.bind(view)
        configManager = ConfigManager(requireContext())

        checkAndRunAnimation()
        startUptimeTracker()
        fetchSocialLinks()

        binding.tvVersion.text = "Engine v${getVersionSafe()}"
        binding.switchDashboardHome.isChecked = configManager.enableDashboard

        binding.btnStart.setOnClickListener {
            val activeBots = configManager.getBots().filter { it.sessionId.isNotEmpty() }
            if (activeBots.isEmpty()) {
                (activity as MainActivity).navigateTo(AlertFragment.newInstance("No Session ID found! Please add at least one session in Configuration."))
                return@setOnClickListener
            }
            updateStatusUI(BotState.RUNNING)
            val intent = Intent(requireContext(), BotService::class.java)
            ContextCompat.startForegroundService(requireContext(), intent)
        }

        binding.btnStop.setOnClickListener {
            updateStatusUI(BotState.STOPPED)
            requireContext().startService(Intent(requireContext(), BotService::class.java).apply { action = "STOP" })
        }

        binding.btnRestart.setOnClickListener {
            val activeBots = configManager.getBots().filter { it.sessionId.isNotEmpty() }
            if (activeBots.isEmpty()) return@setOnClickListener
            
            updateStatusUI(BotState.RESTARTING)
            val intent = Intent(requireContext(), BotService::class.java).apply { action = "RESTART" }
            ContextCompat.startForegroundService(requireContext(), intent)
        }

        binding.switchDashboardHome.setOnCheckedChangeListener { _, isChecked ->
            if (configManager.enableDashboard == isChecked) return@setOnCheckedChangeListener
            configManager.enableDashboard = isChecked
            
            if (BotService.isRunning) {
                updateStatusUI(BotState.RESTARTING)
                Toast.makeText(context, "Restarting bot to apply Dashboard toggle...", Toast.LENGTH_SHORT).show()
                val intent = Intent(requireContext(), BotService::class.java).apply { action = "RESTART" }
                ContextCompat.startForegroundService(requireContext(), intent)
            }
        }

        binding.btnOpenDashboard.setOnClickListener {
            val port = configManager.serverPort.ifEmpty { "3003" }
            val url = "http://127.0.0.1:$port"
            (activity as MainActivity).navigateTo(WebViewFragment.newInstance(url, "Local Dashboard"))
        }

        binding.btnNavDocs.setOnClickListener {
            (activity as MainActivity).navigateTo(WebViewFragment.newInstance("https://t-ct.org/docs/", "Website"))
        }

        binding.btnNavTutorials.setOnClickListener {
            (activity as MainActivity).navigateTo(TutorialsFragment())
        }

        binding.btnInfo.setOnClickListener { fetchReleaseNotes() }
        binding.btnNavConfig.setOnClickListener { (activity as MainActivity).navigateTo(ConfigFragment()) }
        binding.btnNavLogs.setOnClickListener { (activity as MainActivity).navigateTo(LogsFragment()) }
        binding.btnThemeToggle.setOnClickListener { (activity as MainActivity).toggleTheme() }
    }
    
    private fun fetchSocialLinks() {
        thread {
            try {
                val url = URL("https://gist.githubusercontent.com/i-tct/00aa27fed3bf846fe2d01eefa817eaa9/raw/links.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    activity?.runOnUiThread {
                        setupSocialButton(binding.btnWhatsapp, json.optString("Whatsapp"))
                        setupSocialButton(binding.btnTelegram, json.optString("Telegram"))
                        setupSocialButton(binding.btnYoutube, json.optString("Youtube"))
                        setupSocialButton(binding.btnGithub, json.optString("Github"))
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun setupSocialButton(view: View, link: String) {
        if (link.isNotEmpty()) {
            view.visibility = View.VISIBLE
            view.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            }
        }
    }

    private fun startUptimeTracker() {
        uptimeRunnable = object : Runnable {
            override fun run() {
                if (BotService.isRunning && BotService.startTime > 0) {
                    val diff = (System.currentTimeMillis() - BotService.startTime) / 1000
                    val h = diff / 3600
                    val m = (diff % 3600) / 60
                    val s = diff % 60
                    binding.tvUptime.text = String.format("Uptime: %02d:%02d:%02d", h, m, s)
                    binding.tvUptime.visibility = View.VISIBLE
                } else {
                    binding.tvUptime.visibility = View.GONE
                }
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(uptimeRunnable!!)
    }

    private fun checkAndRunAnimation() {
        val prefs = requireContext().getSharedPreferences("TCTBotPrefs", Context.MODE_PRIVATE)
        val lastOpened = prefs.getLong("LAST_OPENED_TIME", 0L)
        val currentTime = System.currentTimeMillis()
        
        if (lastOpened == 0L || (currentTime - lastOpened) > 14400000L) {
            runEntranceAnimation()
        }
        prefs.edit().putLong("LAST_OPENED_TIME", currentTime).apply()
    }

    private fun runEntranceAnimation() {
        binding.appLogo.alpha = 0f
        binding.appLogo.translationY = -50f
        binding.statusCard.alpha = 0f
        binding.statusCard.translationY = 50f
        binding.actionButtons.alpha = 0f

        binding.appLogo.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(800)
            .setInterpolator(DecelerateInterpolator())
            .start()

        binding.statusCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(800)
            .setStartDelay(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
            
        binding.actionButtons.animate()
            .alpha(1f)
            .setDuration(600)
            .setStartDelay(500)
            .start()
    }

    private fun markdownToHtml(markdown: String): String {
        return markdown
            .replace(Regex("(?m)^#{1,6}\\s+(.*?)$"), "<b>$1</b><br/>")
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
            .replace(Regex("__(.*?)__"), "<b>$1</b>")
            .replace(Regex("\\*(.*?)\\*"), "<i>$1</i>")
            .replace(Regex("_(.*?)_"), "<i>$1</i>")
            .replace(Regex("(?m)^>\\s+(.*?)$"), "<i><font color=\"#888888\">\"$1\"</font></i>")
            .replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "<a href=\"$2\">$1</a>")
            .replace(Regex("`(.*?)`"), "<tt>$1</tt>")
            .replace(Regex("(?m)^[-*+]\\s+"), "• ")
            .replace("\n", "<br/>")
            .trim()
    }

    private fun fetchReleaseNotes() {
        val currentVersion = getVersionSafe()
        if (currentVersion == "Unknown" || currentVersion.isEmpty()) {
            showAboutDialog("Beta Build", "You are using an unofficial, beta, or locally compiled release. No official changelog is available.")
            return
        }

        val prefs = requireContext().getSharedPreferences("TCTBotPrefs", Context.MODE_PRIVATE)
        val cachedVersion = prefs.getString("CACHED_RELEASE_VERSION", "")
        val cachedTitle = prefs.getString("CACHED_RELEASE_TITLE", "")
        val cachedHtml = prefs.getString("CACHED_RELEASE_HTML", "")

        if (currentVersion == cachedVersion && !cachedHtml.isNullOrEmpty() && !cachedTitle.isNullOrEmpty()) {
            showAboutDialog("Release Notes ($cachedTitle) - Cached", cachedHtml)
            return
        }

        Toast.makeText(context, "Fetching Release Notes...", Toast.LENGTH_SHORT).show()
        
        thread {
            try {
                var connection = URL("https://api.github.com/repos/i-tct/tct/releases/tags/$currentVersion").openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                
                if (connection.responseCode == 404) {
                    val altVersion = if (currentVersion.startsWith("v")) currentVersion.substring(1) else "v$currentVersion"
                    connection = URL("https://api.github.com/repos/i-tct/tct/releases/tags/$altVersion").openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                }

                when (connection.responseCode) {
                    200 -> {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val jsonObject = JSONObject(response)
                        val rawBody = jsonObject.getString("body")
                        val versionName = jsonObject.getString("tag_name")
                        
                        val htmlNotes = markdownToHtml(rawBody)
                        
                        prefs.edit()
                            .putString("CACHED_RELEASE_VERSION", currentVersion)
                            .putString("CACHED_RELEASE_TITLE", versionName)
                            .putString("CACHED_RELEASE_HTML", htmlNotes)
                            .apply()
                        
                        activity?.runOnUiThread {
                            showAboutDialog("Release Notes ($versionName)", htmlNotes)
                        }
                    }
                    404 -> {
                        activity?.runOnUiThread {
                            showAboutDialog("Beta Release ($currentVersion)", "You are currently using version $currentVersion. This appears to be a Beta or unreleased version. No official changelog found on GitHub yet.")
                        }
                    }
                    else -> {
                        activity?.runOnUiThread {
                            Toast.makeText(context, "Failed to fetch release notes. (${connection.responseCode})", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAboutDialog(title: String, htmlMessage: String) {
        if (context == null) return
        val spannedMessage = HtmlCompat.fromHtml(htmlMessage, HtmlCompat.FROM_HTML_MODE_LEGACY)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(spannedMessage)
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
            
        val messageView = dialog.findViewById<TextView>(android.R.id.message)
        messageView?.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            requireActivity(),
            statusReceiver,
            IntentFilter("com.tct.bot.STATUS_UPDATE"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        if (BotService.isRunning) updateStatusUI(BotState.RUNNING) else updateStatusUI(BotState.STOPPED)
    }

    override fun onPause() {
        super.onPause()
        requireActivity().unregisterReceiver(statusReceiver)
    }

    private fun updateStatusUI(state: BotState) {
        when (state) {
            BotState.RUNNING -> {
                binding.tvStatus.text = "Status: RUNNING"
                binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_running))
                
                if (configManager.enableDashboard) {
                    binding.btnOpenDashboard.visibility = View.VISIBLE
                } else {
                    binding.btnOpenDashboard.visibility = View.GONE
                }
            }
            BotState.RESTARTING -> {
                binding.tvStatus.text = "Status: RESTARTING..."
                binding.tvStatus.setTextColor(Color.parseColor("#FF9800"))
                binding.btnOpenDashboard.visibility = View.GONE
            }
            BotState.STOPPED -> {
                binding.tvStatus.text = "Status: STOPPED"
                binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_stopped))
                binding.btnOpenDashboard.visibility = View.GONE
            }
        }
    }

    private fun getVersionSafe(): String {
        return try { Mobile.getBotVersion() } catch (e: Exception) { "Unknown" }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        uptimeRunnable?.let { handler.removeCallbacks(it) }
        _binding = null
    }
}
