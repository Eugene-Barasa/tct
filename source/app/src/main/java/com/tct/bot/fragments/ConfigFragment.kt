package com.tct.bot.fragments

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tct.bot.BotService
import com.tct.bot.MainActivity
import com.tct.bot.R
import com.tct.bot.databinding.FragmentConfigBinding
import com.tct.bot.managers.BotConfig
import com.tct.bot.managers.ConfigManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class ConfigFragment : Fragment(R.layout.fragment_config) {

    private var _binding: FragmentConfigBinding? = null
    private val binding get() = _binding!!
    private lateinit var configManager: ConfigManager
    
    private var bots = mutableListOf<BotConfig>()
    private var currentBotIndex = -1
    private var isPopulatingUI = false
    
    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private val autoSaveRunnable = Runnable {
        saveCurrentUIState()
        if (BotService.isRunning) {
            Toast.makeText(context, "Config saved! Restart engine to apply changes.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentConfigBinding.bind(view)
        configManager = ConfigManager(requireContext())
        bots = configManager.getBots()

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
        
        binding.toolbar.inflateMenu(R.menu.menu_config)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete -> { showDeleteSessionsDialog(); true }
                R.id.action_global -> { toggleGlobalSettings(); true }
                else -> false
            }
        }

        binding.btnAddSession.setOnClickListener {
            showAddSessionDialog()
        }

        setupSpinner()
        setupAutoSaveSwitches()
        setupAutoSaveTextWatchers()

        binding.btnWipe.setOnClickListener { showWipeDbConfirmation() }
        binding.btnResetDefaults.setOnClickListener { showResetDefaultsConfirmation() }
    }
    
    private fun toggleGlobalSettings() {
        val isGlobalVisible = binding.layoutGlobalSettings.visibility == View.VISIBLE
        if (isGlobalVisible) {
            binding.layoutGlobalSettings.visibility = View.GONE
            binding.layoutBotSettings.visibility = View.VISIBLE
            binding.toolbar.menu.findItem(R.id.action_global).title = "⚙️ Global Settings"
            updateSpinnerData()
        } else {
            binding.layoutGlobalSettings.visibility = View.VISIBLE
            binding.layoutBotSettings.visibility = View.GONE
            binding.toolbar.menu.findItem(R.id.action_global).title = "🤖 Bot Settings"
            loadGlobalConfigValues()
        }
    }

    private fun setupSpinner() {
        updateSpinnerData()
        binding.spinnerSessions.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == currentBotIndex) return
                saveCurrentUIState()
                currentBotIndex = position
                loadBotConfigValues()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateSpinnerData() {
        if (bots.isEmpty()) {
            binding.spinnerSessions.isEnabled = false
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listOf("No Sessions Added"))
            binding.spinnerSessions.adapter = adapter
            currentBotIndex = -1
            
            val startIndex = binding.layoutBotSettings.indexOfChild(binding.spinnerSessions) + 1
            for (i in startIndex until binding.layoutBotSettings.childCount) {
                binding.layoutBotSettings.getChildAt(i).visibility = View.GONE
            }
        } else {
            val startIndex = binding.layoutBotSettings.indexOfChild(binding.spinnerSessions) + 1
            for (i in startIndex until binding.layoutBotSettings.childCount) {
                binding.layoutBotSettings.getChildAt(i).visibility = View.VISIBLE
            }

            binding.spinnerSessions.isEnabled = true
            val names = bots.map { it.sessionId }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerSessions.adapter = adapter
            
            if (currentBotIndex < 0 || currentBotIndex >= bots.size) {
                currentBotIndex = 0
            }
            binding.spinnerSessions.setSelection(currentBotIndex)
            loadBotConfigValues()
        }
    }

    private fun showAddSessionDialog() {
        if (bots.size >= 5) {
            Toast.makeText(context, "Maximum of 5 sessions allowed.", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(requireContext())
        input.hint = "e.g. tct_123abc"
        input.setPadding(40, 40, 40, 40)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Add New Session")
            .setMessage("Enter the Session ID exactly as generated to validate and add it to the engine.")
            .setView(input)
            .setPositiveButton("Verify & Add", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                var sid = input.text.toString().trim()
                if (sid.isEmpty()) {
                    input.error = "Cannot be empty"
                    return@setOnClickListener
                }
                
                if (!sid.startsWith("tct_")) {
                    sid = "tct_$sid"
                }

                if (bots.any { it.sessionId == sid }) {
                    input.error = "Session already exists"
                    return@setOnClickListener
                }
                
                it.isEnabled = false
                dialog.setMessage("Validating with TCT Servers...\nPlease wait.")
                validateAndAddSession(sid, dialog)
            }
        }
        dialog.show()
    }
    
    private fun validateAndAddSession(sessionId: String, dialog: AlertDialog) {
        thread {
            try {
                val url = URL("https://t-ct.org/api/validate-session?id=$sessionId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.setRequestProperty("Accept", "application/json")
                
                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(resp)
                    if (json.optBoolean("valid", false)) {
                        activity?.runOnUiThread {
                            saveCurrentUIState()
                            bots.add(BotConfig(sessionId = sessionId))
                            configManager.saveBots(bots)
                            currentBotIndex = bots.size - 1
                            updateSpinnerData()
                            dialog.dismiss()
                            Toast.makeText(context, "Session Validated & Added!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        activity?.runOnUiThread {
                            dialog.setMessage("Invalid Session ID. Please generate a new one.")
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        }
                    }
                } else {
                    activity?.runOnUiThread {
                        dialog.setMessage("Server rejected request. (Code: ${conn.responseCode})")
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    dialog.setMessage("Network Error. Could not connect to validation server.")
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                }
            }
        }
    }
    
    private fun showDeleteSessionsDialog() {
        if (bots.isEmpty()) {
            Toast.makeText(context, "No sessions to delete.", Toast.LENGTH_SHORT).show()
            return
        }
        val sessionNames = bots.map { it.sessionId }.toTypedArray()
        val checked = BooleanArray(bots.size)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Sessions")
            .setMultiChoiceItems(sessionNames, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Delete Selected") { _, _ ->
                val remainingBots = bots.filterIndexed { index, _ -> !checked[index] }
                if (remainingBots.size == bots.size) return@setPositiveButton
                
                bots.clear()
                bots.addAll(remainingBots)
                configManager.saveBots(bots)
                
                currentBotIndex = if (bots.isEmpty()) -1 else 0
                updateSpinnerData()
                Toast.makeText(context, "Selected sessions deleted.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadGlobalConfigValues() {
        isPopulatingUI = true
        binding.switchAutoBoot.isChecked = configManager.autoStartOnBoot
        binding.switchEnableDashboard.isChecked = configManager.enableDashboard
        binding.etServerPort.setText(configManager.serverPort)
        binding.etDbSyncMode.setText(configManager.dbSyncMode)
        binding.etDbBatchSize.setText(configManager.dbBatchSize.toString())
        binding.etDbFlushInterval.setText(configManager.dbFlushInterval)
        binding.etDbCacheMax.setText(configManager.dbCacheMaxBytes.toString())
        binding.switchDbUseWal.isChecked = configManager.dbUseWal
        binding.etDbBusyTimeout.setText(configManager.dbBusyTimeoutMs.toString())
        
        binding.etPostgresUrl.setText(configManager.postgresUrl)
        
        binding.etPostgresSync.setText(configManager.postgresSyncInterval.toString())
        binding.etCloudName.setText(configManager.cloudinaryName)
        binding.etCloudKey.setText(configManager.cloudinaryKey)
        binding.etCloudSecret.setText(configManager.cloudinarySecret)
        binding.switchNoiseLogs.isChecked = configManager.filterNoiseLogs
        binding.etLogLevel.setText(configManager.logLevel)
        isPopulatingUI = false
    }

    private fun loadBotConfigValues() {
        if (currentBotIndex < 0 || currentBotIndex >= bots.size) return
        isPopulatingUI = true
        val bot = bots[currentBotIndex]
        binding.etSessionId.setText(bot.sessionId)
        binding.etPrefix.setText(bot.prefix)
        binding.etTimezone.setText(bot.timezone)
        binding.etWeather.setText(bot.openWeatherApiKey)
        binding.etMaxWarns.setText(bot.lockMaxWarns.toString())
        binding.etWarnMessage.setText(bot.lockWarnMessage)
        binding.etDefaultReason.setText(bot.lockDefaultReason)
        binding.switchShowTrigger.isChecked = bot.lockShowTrigger
        binding.etDeleteWarnAfter.setText(bot.lockDeleteWarnAfter.toString())
        binding.etAliveImages.setText(bot.aliveImages)
        binding.etWelcomeImages.setText(bot.welcomeImages)
        isPopulatingUI = false
    }

    private fun setupAutoSaveTextWatchers() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isPopulatingUI) return
                autoSaveHandler.removeCallbacks(autoSaveRunnable)
                autoSaveHandler.postDelayed(autoSaveRunnable, 800)
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        val editTexts = listOf(
            binding.etPrefix, binding.etTimezone, binding.etServerPort,
            binding.etWeather, binding.etDbSyncMode, binding.etDbBatchSize, binding.etDbFlushInterval,
            binding.etDbCacheMax, binding.etDbBusyTimeout, binding.etPostgresUrl, binding.etPostgresSync,
            binding.etCloudName, binding.etCloudKey, binding.etCloudSecret, binding.etMaxWarns,
            binding.etWarnMessage, binding.etDefaultReason, binding.etDeleteWarnAfter,
            binding.etAliveImages, binding.etWelcomeImages, binding.etLogLevel
        )
        editTexts.forEach { it.addTextChangedListener(watcher) }
    }

    private fun saveCurrentUIState() {
        if (isPopulatingUI) return

        if (binding.layoutGlobalSettings.visibility == View.VISIBLE) {
            configManager.serverPort = binding.etServerPort.text.toString()
            configManager.dbSyncMode = binding.etDbSyncMode.text.toString()
            configManager.dbBatchSize = binding.etDbBatchSize.text.toString().toIntOrNull() ?: 200
            configManager.dbFlushInterval = binding.etDbFlushInterval.text.toString()
            configManager.dbCacheMaxBytes = binding.etDbCacheMax.text.toString().toLongOrNull() ?: 134217728L
            configManager.dbBusyTimeoutMs = binding.etDbBusyTimeout.text.toString().toIntOrNull() ?: 15000
            configManager.postgresUrl = binding.etPostgresUrl.text.toString()
            configManager.postgresSyncInterval = binding.etPostgresSync.text.toString().toIntOrNull() ?: 60
            configManager.cloudinaryName = binding.etCloudName.text.toString()
            configManager.cloudinaryKey = binding.etCloudKey.text.toString()
            configManager.cloudinarySecret = binding.etCloudSecret.text.toString()
            configManager.logLevel = binding.etLogLevel.text.toString()
        } else if (currentBotIndex in bots.indices) {
            val bot = bots[currentBotIndex]
            bot.prefix = binding.etPrefix.text.toString()
            bot.timezone = binding.etTimezone.text.toString()
            bot.openWeatherApiKey = binding.etWeather.text.toString()
            bot.lockMaxWarns = binding.etMaxWarns.text.toString().toIntOrNull() ?: 3
            bot.lockWarnMessage = binding.etWarnMessage.text.toString()
            bot.lockDefaultReason = binding.etDefaultReason.text.toString()
            bot.lockDeleteWarnAfter = binding.etDeleteWarnAfter.text.toString().toIntOrNull() ?: 3
            bot.aliveImages = binding.etAliveImages.text.toString()
            bot.welcomeImages = binding.etWelcomeImages.text.toString()
            configManager.saveBots(bots)
        }
    }

    private fun setupAutoSaveSwitches() {
        binding.switchAutoBoot.setOnCheckedChangeListener { _, isChecked -> 
            if (!isPopulatingUI) configManager.autoStartOnBoot = isChecked 
        }
        binding.switchEnableDashboard.setOnCheckedChangeListener { _, isChecked ->
            if (isPopulatingUI) return@setOnCheckedChangeListener
            configManager.enableDashboard = isChecked
            if (BotService.isRunning) {
                Toast.makeText(context, "Restarting bot to apply Dashboard toggle...", Toast.LENGTH_SHORT).show()
                val intent = Intent(requireContext(), BotService::class.java).apply { action = "RESTART" }
                ContextCompat.startForegroundService(requireContext(), intent)
            }
        }
        binding.switchDbUseWal.setOnCheckedChangeListener { _, isChecked -> if (!isPopulatingUI) configManager.dbUseWal = isChecked }
        binding.switchNoiseLogs.setOnCheckedChangeListener { _, isChecked -> if (!isPopulatingUI) configManager.filterNoiseLogs = isChecked }
        binding.switchShowTrigger.setOnCheckedChangeListener { _, isChecked -> 
            if (!isPopulatingUI && currentBotIndex in bots.indices) {
                bots[currentBotIndex].lockShowTrigger = isChecked
                configManager.saveBots(bots)
            }
        }
    }

    private fun showResetDefaultsConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset to Defaults")
            .setMessage("Reset all global configurations to default values? Your bots and Postgres URL will be preserved.")
            .setPositiveButton("Reset") { _, _ ->
                configManager.resetToDefaults()
                bots = configManager.getBots()
                if (binding.layoutGlobalSettings.visibility == View.VISIBLE) loadGlobalConfigValues() else loadBotConfigValues()
                Toast.makeText(context, "Settings reset.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showWipeDbConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Wipe Database?")
            .setMessage("Are you sure you want to completely wipe the engine's database? The engine will automatically STOP.")
            .setPositiveButton("WIPE DB") { _, _ ->
                if (BotService.isRunning) {
                    requireContext().startService(Intent(requireContext(), BotService::class.java).apply { action = "STOP" })
                }
                val filesDir = requireContext().filesDir
                var deletedAny = false
                filesDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".db") || file.name.endsWith(".db-shm") || file.name.endsWith(".db-wal")) {
                        if (file.delete()) deletedAny = true
                    }
                }
                Toast.makeText(context, if (deletedAny) "Database wiped!" else "No DB found.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onPause() {
        super.onPause()
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
        saveCurrentUIState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
        _binding = null
    }
}
