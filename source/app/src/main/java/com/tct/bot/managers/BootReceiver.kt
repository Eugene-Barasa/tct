package com.tct.bot.managers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.tct.bot.BotService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val configManager = ConfigManager(context)
            
            val activeBots = configManager.getBots().filter { it.sessionId.isNotEmpty() }
            
            if (configManager.autoStartOnBoot && activeBots.isNotEmpty()) {
                val serviceIntent = Intent(context, BotService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
