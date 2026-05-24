package com.codexkd.vivoassistant.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.codexkd.vivoassistant.MainActivity
import com.codexkd.vivoassistant.R
import com.codexkd.vivoassistant.ai.CloudAIManager
import com.codexkd.vivoassistant.memory.MemoryEngine
import com.codexkd.vivoassistant.overlay.OverlayService
import com.codexkd.vivoassistant.routines.RoutineManager
import com.codexkd.vivoassistant.utils.Constants
import com.codexkd.vivoassistant.utils.NetworkManager
import kotlinx.coroutines.*

/**
 * AssistantForegroundService — The always-on lightweight backbone.
 *
 * Responsibilities:
 * - Shows persistent foreground notification (required by Android)
 * - Manages overlay service lifecycle
 * - Handles routine scheduling
 * - Monitors network state
 * - Coordinates session state between components
 * - Auto-starts on boot
 *
 * CRITICAL: This service is LIGHTWEIGHT.
 * - No continuous AI computation
 * - No persistent microphone listening
 * - No background heavy polling
 * - Only starts voice listening during active sessions
 */
class AssistantForegroundService : LifecycleService() {

    // Service binder for activity binding
    inner class AssistantBinder : Binder() {
        fun getService() = this@AssistantForegroundService
    }
    private val binder = AssistantBinder()

    // Core components
    private lateinit var networkManager: NetworkManager
    private lateinit var memoryEngine: MemoryEngine
    private lateinit var routineManager: RoutineManager

    // Wake lock — only hold briefly during AI response
    private var wakeLock: PowerManager.WakeLock? = null

    // Session state
    var isSessionActive = false
        private set

    // ═══════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        createNotificationChannels()

        networkManager = NetworkManager.getInstance(this)
        memoryEngine = MemoryEngine.getInstance(this)
        routineManager = RoutineManager.getInstance(this)

        networkManager.startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                startForegroundWithNotification()
                initComponents()
                Log.d(TAG, "Assistant service STARTED")
            }
            ACTION_STOP -> {
                stopSelf()
            }
            ACTION_SHOW_OVERLAY -> {
                showOverlay()
            }
            ACTION_HIDE_OVERLAY -> {
                hideOverlay()
            }
            ACTION_START_SESSION -> {
                startSession()
            }
            ACTION_END_SESSION -> {
                endSession()
            }
        }

        // Restart if killed by system
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        networkManager.stopMonitoring()
        releaseWakeLock()
        hideOverlay()
        super.onDestroy()
    }

    // ═══════════════════════════════════════════════
    // FOREGROUND NOTIFICATION
    // ═══════════════════════════════════════════════

    private fun startForegroundWithNotification() {
        val notification = buildNotification("Vivo Assistant is ready")
        startForeground(Constants.NOTIF_ID_FOREGROUND, notification)
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AssistantForegroundService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.CHANNEL_ASSISTANT)
            .setContentTitle(Constants.APP_NAME)
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_vivo_orb)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun updateNotificationStatus(status: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(Constants.NOTIF_ID_FOREGROUND, buildNotification(status))
    }

    // ═══════════════════════════════════════════════
    // NOTIFICATION CHANNELS
    // ═══════════════════════════════════════════════

    private fun createNotificationChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Main assistant channel (persistent, silent)
        nm.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_ASSISTANT,
                "Vivo Assistant",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Shows when Vivo Assistant is active"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )

        // Alerts channel (for important AI notifications)
        nm.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_ALERTS,
                "Vivo Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Important alerts from Vivo Assistant"
            }
        )

        // Routines channel
        nm.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_ROUTINES,
                "Smart Routines",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications from scheduled routines"
                setSound(null, null)
            }
        )
    }

    // ═══════════════════════════════════════════════
    // OVERLAY MANAGEMENT
    // ═══════════════════════════════════════════════

    fun showOverlay() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW
        }
        startService(intent)
    }

    fun hideOverlay() {
        try {
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_HIDE
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "hideOverlay error: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════
    // SESSION MANAGEMENT
    // ═══════════════════════════════════════════════

    fun startSession() {
        isSessionActive = true
        acquireWakeLock()
        updateNotificationStatus("Session active — listening")
        Log.d(TAG, "Session started")
    }

    fun endSession() {
        isSessionActive = false
        releaseWakeLock()
        updateNotificationStatus("Vivo Assistant is ready")
        Log.d(TAG, "Session ended")
    }

    // ═══════════════════════════════════════════════
    // WAKE LOCK (brief, during AI response only)
    // ═══════════════════════════════════════════════

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$APP_PACKAGE::VoiceSession"
        ).apply {
            acquire(Constants.MAX_LISTEN_DURATION + 5000) // Auto-release safety
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock release error: ${e.message}")
        }
        wakeLock = null
    }

    // ═══════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════

    private fun initComponents() {
        lifecycleScope.launch {
            try {
                // Schedule enabled routines
                routineManager.scheduleAllRoutines()
                Log.d(TAG, "Components initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Init error: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "AssistantFgService"
        private const val APP_PACKAGE = Constants.APP_PACKAGE

        const val ACTION_START         = "${APP_PACKAGE}.SERVICE_START"
        const val ACTION_STOP          = "${APP_PACKAGE}.SERVICE_STOP"
        const val ACTION_SHOW_OVERLAY  = "${APP_PACKAGE}.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY  = "${APP_PACKAGE}.HIDE_OVERLAY"
        const val ACTION_START_SESSION = "${APP_PACKAGE}.START_SESSION"
        const val ACTION_END_SESSION   = "${APP_PACKAGE}.END_SESSION"

        fun start(context: Context) {
            val intent = Intent(context, AssistantForegroundService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AssistantForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
