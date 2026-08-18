package com.tct.bot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tct.bot.managers.ConfigManager
import com.tct.bot.managers.LogManager
import mobile.Mobile
import kotlin.concurrent.thread

class BotService : Service() {

    companion object {
        private const val CHANNEL_ID = "TCT_BOT_CHANNEL"
        private const val NOTIF_ID = 1
        var isRunning = false
        var startTime: Long = 0L
    }

    private var isBotIntendedToRun = false
    private var isTemporarilyPaused = false
    private var isRestarting = false
    private var botThread: Thread? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private lateinit var notifManager: NotificationManager
    private lateinit var notifBuilder: NotificationCompat.Builder
    private lateinit var configManager: ConfigManager
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var networkLostHandler = Handler(Looper.getMainLooper())
    private var networkLostRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        configManager = ConfigManager(this)
        notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        setupNotificationBuilder()
        setupNetworkListener()

        Mobile.registerFFmpeg(AndroidFFmpegBridge())
        Mobile.registerLogListener(LogManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> {
                isBotIntendedToRun = false
                stopBotEngine(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            "RESTART" -> {
                isBotIntendedToRun = true
                restartBotEngine()
                return START_STICKY
            }
        }

        isBotIntendedToRun = true

        val notification = buildStickyNotification("Initializing...")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (cm.activeNetwork != null) {
            startBotEngine()
        } else {
            updateNotification("Waiting for Internet Connection...", true)
            isTemporarilyPaused = true
        }

        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TCTBot::EngineWakeLock")
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire()
            }
        } catch (e: Exception) {
            Log.e("BotService", "WakeLock acquire blocked by OS: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e("BotService", "WakeLock release error: ${e.message}")
        }
    }

    private fun broadcastToUI(status: String, message: String) {
        sendBroadcast(Intent("com.tct.bot.STATUS_UPDATE").apply {
            setPackage(packageName)
            putExtra("status", status)
            putExtra("message", message)
        })
    }

    private fun restartBotEngine() {
        if (!isRunning) {
            startBotEngine()
            return
        }
        
        isRestarting = true
        broadcastToUI("RESTARTING", "Restarting bot services...")
        updateNotification("Restarting Engine...", true)
        
        thread {
            try {
                Mobile.stopBot()
                botThread?.join(5000) 
            } catch (e: Exception) {}
            
            isRunning = false
            startTime = 0L
            isRestarting = false
            
            Handler(Looper.getMainLooper()).post {
                startBotEngine()
            }
        }
    }

    private fun startBotEngine() {
        if (isRunning) return
        
        val activeBots = configManager.getBots().filter { it.sessionId.isNotEmpty() }
        if (activeBots.isEmpty()) {
            stopSelf()
            return
        }

        isRunning = true
        isTemporarilyPaused = false
        startTime = System.currentTimeMillis()
        
        acquireWakeLock()
        
        updateNotification("Bot is Running (Online)", false)
        broadcastToUI("RUNNING", "Bot started securely")

        configManager.generateTctFile()
        Mobile.setEnv("DASHBOARD_ENABLED", if (configManager.enableDashboard) "true" else "false")

        botThread = thread(start = true) {
            try {
                // Execute core
                Mobile.startBot(filesDir.absolutePath)

                if (isBotIntendedToRun && !isTemporarilyPaused && !isRestarting) {
                    isRunning = false
                    startTime = 0L
                    updateNotification("Bot stopped unexpectedly.", false)
                    broadcastToUI("ERROR", "Session closed or API unreachable.")
                    stopSelf()
                }
            } catch (e: Exception) {
                isRunning = false
                startTime = 0L
                val errorMsg = e.message ?: "Unknown Error"

                if (errorMsg.contains("server returned status", ignoreCase = true) || errorMsg.contains("session id", ignoreCase = true)) {
                    updateNotification("Invalid Session ID!", true)
                    broadcastToUI("ERROR", "❌ INVALID SESSION ID DETECTED!\n\nThe server rejected the session download request. Please check your Session ID and ensure it is valid.\n\nBackend Info: $errorMsg")
                } else {
                    updateNotification("Bot Crashed!", true)
                    broadcastToUI("ERROR", "CRITICAL ERROR:\n$errorMsg")
                }
                stopSelf()
            }
        }
    }

    private fun stopBotEngine(temporarily: Boolean) {
        if (!isRunning) return
        try {
            Mobile.stopBot()
            botThread?.interrupt()
            botThread = null
        } catch (e: Exception) {
            Log.e("BotService", "Error stopping: ${e.message}")
        }
        isRunning = false
        startTime = 0L
        isTemporarilyPaused = temporarily
        
        releaseWakeLock()
        
        if (!isRestarting) {
            val msg = if (temporarily) "Bot paused (Offline/No Network)." else "Bot stopped normally."
            updateNotification(msg, temporarily)
            broadcastToUI("STOPPED", msg)
        }
    }

    private fun setupNetworkListener() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkLostRunnable?.let { networkLostHandler.removeCallbacks(it) }
                if (isBotIntendedToRun && !isRunning && !isRestarting) {
                    startBotEngine() 
                }
            }
            override fun onLost(network: Network) {
                if (isRunning) {
                    networkLostRunnable = Runnable {
                        val currentNetwork = cm.activeNetwork
                        if (currentNetwork == null && isRunning) {
                            stopBotEngine(temporarily = true) 
                        }
                    }
                    networkLostHandler.postDelayed(networkLostRunnable!!, 5000)
                }
            }
        }
        cm.registerDefaultNetworkCallback(networkCallback!!)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "TCT Bot Operations", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps the TCT Bot alive in the background" }
            notifManager.createNotificationChannel(channel)
        }
    }

    private fun setupNotificationBuilder() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        notifBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TCT Engine")
            .setSmallIcon(R.drawable.ic_stat_bot) 
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setAutoCancel(false)
    }

    private fun buildStickyNotification(text: String): Notification {
        notifBuilder.setContentText(text)
        val notification = notifBuilder.build()
        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
        return notification
    }

    private fun updateNotification(text: String, showWarning: Boolean) {
        if (showWarning) {
            notifBuilder.setColor(android.graphics.Color.RED)
        } else {
            notifBuilder.setColor(android.graphics.Color.parseColor("#4CAF50"))
        }
        val notification = buildStickyNotification(text)
        notifManager.notify(NOTIF_ID, notification) 
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBotEngine(false)
        releaseWakeLock()
        networkLostRunnable?.let { networkLostHandler.removeCallbacks(it) }
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        networkCallback?.let { cm?.unregisterNetworkCallback(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
