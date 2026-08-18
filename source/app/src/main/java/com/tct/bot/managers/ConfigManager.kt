package com.tct.bot.managers

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

val defaultAliveString = """https://t-ct.org/media/alive.jpg
https://t-ct.org/media/alive1.jpg
https://t-ct.org/media/alive2.jpg
https://t-ct.org/media/alive3.jpg
https://t-ct.org/media/alive4.jpg
https://t-ct.org/media/alive5.jpg
https://t-ct.org/media/alive6.jpg
https://t-ct.org/media/alive7.jpg
https://t-ct.org/media/alive8.jpg
https://t-ct.org/media/alive9.jpg
https://t-ct.org/media/alive10.jpg
https://t-ct.org/media/alive11.jpg"""

val defaultWelcomeString = """https://t-ct.org/media/welcome1.jpg
https://t-ct.org/media/welcome2.jpg
https://t-ct.org/media/welcome3.jpg
https://t-ct.org/media/welcome4.jpg
https://t-ct.org/media/welcome5.jpg
https://t-ct.org/media/welcome6.jpg
https://t-ct.org/media/welcome7.jpg
https://t-ct.org/media/welcome8.jpg
https://t-ct.org/media/welcome9.jpg"""

data class BotConfig(
    var sessionId: String = "",
    var prefix: String = ".",
    var timezone: String = "Africa/Nairobi",
    var openWeatherApiKey: String = "",
    var lockMaxWarns: Int = 3,
    var lockWarnMessage: String = "*⚠️WARN MESSAGE⚠️*\\n\\n*USER:* {user}\\n*MAX WARNINGS:* {max_warns}\\n*REMAINING WARNINGS:* {remaining_warns}\\n*REASON:* {reason}",
    var lockDefaultReason: String = "Breaking group rules.",
    var lockShowTrigger: Boolean = true,
    var lockDeleteWarnAfter: Int = 3,
    var aliveImages: String = defaultAliveString,
    var welcomeImages: String = defaultWelcomeString
)

class ConfigManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("TCTBotPrefs", Context.MODE_PRIVATE)

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean("AUTO_START_ON_BOOT", true)
        set(value) = prefs.edit().putBoolean("AUTO_START_ON_BOOT", value).apply()

    var enableDashboard: Boolean
        get() = prefs.getBoolean("ENABLE_WEB_DASHBOARD", false)
        set(value) = prefs.edit().putBoolean("ENABLE_WEB_DASHBOARD", value).apply()

    var serverPort: String
        get() {
            // Forces exactly 3003 default even if accidentally saved empty previously
            val p = prefs.getString("SERVER_PORT", "3003") ?: "3003"
            return p.ifEmpty { "3003" }
        }
        set(value) = prefs.edit().putString("SERVER_PORT", value.trim()).apply()

    var dbSyncMode: String
        get() = prefs.getString("DB_SYNC_MODE", "NORMAL") ?: "NORMAL"
        set(value) = prefs.edit().putString("DB_SYNC_MODE", value.trim()).apply()
    var dbBatchSize: Int
        get() = prefs.getInt("DB_BATCH_SIZE", 200)
        set(value) = prefs.edit().putInt("DB_BATCH_SIZE", value).apply()
    var dbFlushInterval: String
        get() = prefs.getString("DB_FLUSH_INTERVAL", "5000") ?: "5000"
        set(value) = prefs.edit().putString("DB_FLUSH_INTERVAL", value.trim()).apply()
    var dbCacheMaxBytes: Long
        get() = prefs.getLong("DB_CACHE_MAX_BYTES", 134217728L)
        set(value) = prefs.edit().putLong("DB_CACHE_MAX_BYTES", value).apply()
    var dbUseWal: Boolean
        get() = prefs.getBoolean("DB_USE_WAL", true)
        set(value) = prefs.edit().putBoolean("DB_USE_WAL", value).apply()
    var dbBusyTimeoutMs: Int
        get() = prefs.getInt("DB_BUSY_TIMEOUT_MS", 15000)
        set(value) = prefs.edit().putInt("DB_BUSY_TIMEOUT_MS", value).apply()

    var postgresUrl: String
        get() = prefs.getString("POSTGRES_URL", "") ?: ""
        set(value) = prefs.edit().putString("POSTGRES_URL", value.trim()).apply()
        
    var postgresSyncInterval: Int
        get() = prefs.getInt("POSTGRES_SYNC_INTERVAL", 60)
        set(value) = prefs.edit().putInt("POSTGRES_SYNC_INTERVAL", value).apply()

    var cloudinaryName: String
        get() = prefs.getString("CLOUDINARY_CLOUD_NAME", "") ?: ""
        set(value) = prefs.edit().putString("CLOUDINARY_CLOUD_NAME", value.trim()).apply()
    var cloudinaryKey: String
        get() = prefs.getString("CLOUDINARY_API_KEY", "") ?: ""
        set(value) = prefs.edit().putString("CLOUDINARY_API_KEY", value.trim()).apply()
    var cloudinarySecret: String
        get() = prefs.getString("CLOUDINARY_API_SECRET", "") ?: ""
        set(value) = prefs.edit().putString("CLOUDINARY_API_SECRET", value.trim()).apply()

    var filterNoiseLogs: Boolean
        get() = prefs.getBoolean("FILTER_NOISE_LOGS", true)
        set(value) = prefs.edit().putBoolean("FILTER_NOISE_LOGS", value).apply()
    var logLevel: String
        get() = prefs.getString("LOG_LEVEL", "info") ?: "info"
        set(value) = prefs.edit().putString("LOG_LEVEL", value.trim()).apply()

    fun getBots(): MutableList<BotConfig> {
        val botsStr = prefs.getString("BOTS_ARRAY", "[]") ?: "[]"
        val list = mutableListOf<BotConfig>()
        try {
            val arr = JSONArray(botsStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(BotConfig(
                    sessionId = obj.optString("sessionId", ""),
                    prefix = obj.optString("prefix", "."),
                    timezone = obj.optString("timezone", "Africa/Nairobi"),
                    openWeatherApiKey = obj.optString("openWeatherApiKey", ""),
                    lockMaxWarns = obj.optInt("lockMaxWarns", 3),
                    lockWarnMessage = obj.optString("lockWarnMessage", "*⚠️WARN MESSAGE⚠️*\\n\\n*USER:* {user}\\n*MAX WARNINGS:* {max_warns}\\n*REMAINING WARNINGS:* {remaining_warns}\\n*REASON:* {reason}"),
                    lockDefaultReason = obj.optString("lockDefaultReason", "Breaking group rules."),
                    lockShowTrigger = obj.optBoolean("lockShowTrigger", true),
                    lockDeleteWarnAfter = obj.optInt("lockDeleteWarnAfter", 3),
                    aliveImages = obj.optString("aliveImages", defaultAliveString),
                    welcomeImages = obj.optString("welcomeImages", defaultWelcomeString)
                ))
            }
        } catch (e: Exception) { e.printStackTrace() }
        
        return list
    }

    fun saveBots(bots: List<BotConfig>) {
        val arr = JSONArray()
        bots.forEach { b ->
            val obj = JSONObject()
            obj.put("sessionId", b.sessionId)
            obj.put("prefix", b.prefix)
            obj.put("timezone", b.timezone)
            obj.put("openWeatherApiKey", b.openWeatherApiKey)
            obj.put("lockMaxWarns", b.lockMaxWarns)
            obj.put("lockWarnMessage", b.lockWarnMessage)
            obj.put("lockDefaultReason", b.lockDefaultReason)
            obj.put("lockShowTrigger", b.lockShowTrigger)
            obj.put("lockDeleteWarnAfter", b.lockDeleteWarnAfter)
            obj.put("aliveImages", b.aliveImages)
            obj.put("welcomeImages", b.welcomeImages)
            arr.put(obj)
        }
        prefs.edit().putString("BOTS_ARRAY", arr.toString()).apply()
    }

    fun resetToDefaults() {
        val backupPostgres = postgresUrl
        val backupBots = getBots()
        prefs.edit().clear().apply()
        postgresUrl = backupPostgres
        saveBots(backupBots)
    }

    private fun formatYamlList(input: String, indent: String): String {
        val lines = input.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return " []\n"
        return "\n" + lines.joinToString("\n") { "$indent- \"$it\"" } + "\n"
    }

    fun generateTctFile() {
        val sb = java.lang.StringBuilder()
        sb.append("SERVER_PORT: ${serverPort.ifEmpty { "3003" }}\n")
        sb.append("FILTER_NOISE_LOGS: $filterNoiseLogs\n")
        sb.append("LOG_TO_FILE: false\n") // Overridden because Android uses JNI streaming
        sb.append("LOG_LEVEL: \"$logLevel\"\n\n")

        sb.append("DATABASE:\n")
        sb.append("  SYNC_MODE: \"$dbSyncMode\"\n")
        sb.append("  BATCH_SIZE: $dbBatchSize\n")
        sb.append("  FLUSH_INTERVAL: \"$dbFlushInterval\"\n")
        sb.append("  CACHE_MAX_BYTES: $dbCacheMaxBytes\n")
        sb.append("  USE_WAL: $dbUseWal\n")
        sb.append("  BUSY_TIMEOUT_MS: $dbBusyTimeoutMs\n\n")

        if (postgresUrl.isNotEmpty()) {
            sb.append("POSTGRES_URL: \"$postgresUrl\"\n")
            sb.append("POSTGRES_SYNC_INTERVAL: $postgresSyncInterval\n\n")
        }

        if (cloudinaryName.isNotEmpty()) {
            sb.append("CLOUDINARY:\n")
            sb.append("  CLOUD_NAME: \"$cloudinaryName\"\n")
            sb.append("  API_KEY: \"$cloudinaryKey\"\n")
            sb.append("  API_SECRET: \"$cloudinarySecret\"\n\n")
        }

        sb.append("BOTS:\n")
        val activeBots = getBots()
        for (bot in activeBots) {
            if (bot.sessionId.isEmpty()) continue
            
            sb.append("  - SESSION_ID: \"${bot.sessionId}\"\n")
            sb.append("    TIMEZONE: \"${bot.timezone}\"\n")
            sb.append("    PREFIX: \"${bot.prefix}\"\n")
            if (bot.openWeatherApiKey.isNotEmpty()) {
                sb.append("    OPENWEATHER_API_KEY: \"${bot.openWeatherApiKey}\"\n")
            }

            sb.append("    LOCK_SYSTEM:\n")
            sb.append("      MAX_WARNS: ${bot.lockMaxWarns}\n")
            sb.append("      WARN_MESSAGE: \"${bot.lockWarnMessage.replace("\"", "\\\"")}\"\n")
            sb.append("      DEFAULT_REASON: \"${bot.lockDefaultReason.replace("\"", "\\\"")}\"\n")
            sb.append("      SHOW_TRIGGER_IN_REASON: ${bot.lockShowTrigger}\n")
            sb.append("      DELETE_WARN_AFTER: ${bot.lockDeleteWarnAfter}\n")

            sb.append("    ALIVE_IMAGES:")
            sb.append(formatYamlList(bot.aliveImages, "      "))
            
            sb.append("    WELCOME_IMAGES:")
            sb.append(formatYamlList(bot.welcomeImages, "      "))
            sb.append("\n")
        }

        File(context.filesDir, "tctfile").writeText(sb.toString())
    }
}
