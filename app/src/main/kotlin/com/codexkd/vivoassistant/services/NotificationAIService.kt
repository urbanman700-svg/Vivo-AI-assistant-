package com.codexkd.vivoassistant.services

import android.app.Notification
import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.codexkd.vivoassistant.ai.AIResult
import com.codexkd.vivoassistant.ai.CloudAIManager
import com.codexkd.vivoassistant.models.NotificationItem
import com.codexkd.vivoassistant.utils.Constants
import kotlinx.coroutines.*

/**
 * NotificationAIService — AI-powered notification intelligence.
 *
 * Features:
 * - Captures all incoming notifications
 * - AI-powered summary generation
 * - Priority ranking
 * - Smart reply suggestions
 * - Read notifications aloud on request
 * - Notification grouping by app
 *
 * Privacy: Only reads visible notification text.
 * No private data access beyond what appears in the notification shade.
 */
class NotificationAIService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val notificationBuffer = mutableListOf<NotificationItem>()
    private var cloudAI: CloudAIManager? = null

    // Listener for MainActivity/OverlayService to receive updates
    companion object {
        private const val TAG = "NotificationAIService"
        private const val BUFFER_LIMIT = 30

        @Volatile
        private var instance: NotificationAIService? = null

        fun getInstance(): NotificationAIService? = instance

        // Apps to ignore (system noise)
        private val IGNORED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.google.android.gms",
            "com.android.packageinstaller",
            Constants.APP_PACKAGE
        )
    }

    // ═══════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        instance = this
        cloudAI = CloudAIManager.getInstance(this)
        Log.d(TAG, "NotificationAIService connected ✓")
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        Log.d(TAG, "NotificationAIService disconnected")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    // ═══════════════════════════════════════════════
    // NOTIFICATION EVENTS
    // ═══════════════════════════════════════════════

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val pkg = sbn.packageName ?: return
        if (pkg in IGNORED_PACKAGES) return
        if (sbn.isOngoing) return  // Skip persistent notifications

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val body  = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() && body.isBlank()) return

        val appName = getAppLabel(pkg)

        val item = NotificationItem(
            id          = sbn.id,
            packageName = pkg,
            appName     = appName,
            title       = title,
            body        = body,
            priority    = calculatePriority(sbn, title, body)
        )

        // Add to buffer (bounded)
        synchronized(notificationBuffer) {
            notificationBuffer.removeAll { it.id == item.id && it.packageName == pkg }
            notificationBuffer.add(0, item)
            if (notificationBuffer.size > BUFFER_LIMIT) {
                notificationBuffer.removeAt(notificationBuffer.lastIndex)
            }
        }

        Log.d(TAG, "Notification from $appName: $title")

        // Broadcast to app for live update
        sendBroadcast(Intent(Constants.ACTION_NOTIF_UPDATE).apply {
            putExtra("pkg", pkg)
            putExtra("title", title)
        })
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        synchronized(notificationBuffer) {
            notificationBuffer.removeAll { it.id == sbn.id && it.packageName == sbn.packageName }
        }
    }

    // ═══════════════════════════════════════════════
    // AI FUNCTIONS
    // ═══════════════════════════════════════════════

    /**
     * Get all buffered notifications.
     */
    fun getBufferedNotifications(): List<NotificationItem> {
        return synchronized(notificationBuffer) {
            notificationBuffer.toList()
        }
    }

    /**
     * Get notifications from a specific app.
     */
    fun getNotificationsFromApp(packageName: String): List<NotificationItem> {
        return synchronized(notificationBuffer) {
            notificationBuffer.filter { it.packageName == packageName }
        }
    }

    /**
     * AI-powered summary of all pending notifications.
     * Returns a concise natural language summary.
     */
    suspend fun summarizeAll(): String {
        val notifications = getBufferedNotifications()
        if (notifications.isEmpty()) return "No new notifications."

        // Group by app
        val grouped = notifications.groupBy { it.appName }
        val summary = buildString {
            grouped.forEach { (app, items) ->
                appendLine("$app (${items.size}):")
                items.take(2).forEach { item ->
                    if (item.title.isNotBlank()) append("  - ${item.title}")
                    if (item.body.isNotBlank()) appendLine(": ${item.body.take(80)}")
                    else appendLine()
                }
            }
        }

        if (cloudAI?.isConfigured() != true) {
            return buildFallbackSummary(grouped)
        }

        val prompt = """
            Summarize these Android notifications in 2-3 sentences.
            Be concise. Mention important ones first.
            
            Notifications:
            $summary
        """.trimIndent()

        return when (val result = cloudAI!!.quickAnalyze(prompt, maxTokens = 150)) {
            is AIResult.Success -> result.text
            is AIResult.Error   -> buildFallbackSummary(grouped)
        }
    }

    /**
     * Get AI-suggested reply for a notification.
     */
    suspend fun suggestReply(item: NotificationItem): String {
        if (cloudAI?.isConfigured() != true) return ""

        val prompt = """
            Suggest a short, natural reply for this message.
            App: ${item.appName}
            From: ${item.title}
            Message: ${item.body}
            
            Give only the reply text, nothing else. Keep it under 20 words.
        """.trimIndent()

        return when (val result = cloudAI!!.quickAnalyze(prompt, maxTokens = 60)) {
            is AIResult.Success -> result.text
            is AIResult.Error   -> ""
        }
    }

    /**
     * Read out top notifications via TTS.
     */
    fun readAloud(count: Int = 3): String {
        val items = getBufferedNotifications().take(count)
        if (items.isEmpty()) return "No notifications to read."

        return buildString {
            appendLine("You have ${notificationBuffer.size} notifications.")
            items.forEach { item ->
                appendLine("From ${item.appName}: ${item.title}. ${item.body.take(60)}")
            }
        }
    }

    // ═══════════════════════════════════════════════
    // DISMISS ACTIONS
    // ═══════════════════════════════════════════════

    fun dismissAll() {
        try {
            cancelAllNotifications()
            synchronized(notificationBuffer) {
                notificationBuffer.clear()
            }
            Log.d(TAG, "All notifications dismissed")
        } catch (e: Exception) {
            Log.e(TAG, "dismissAll error: ${e.message}")
        }
    }

    fun dismissFromApp(packageName: String) {
        try {
            val toCancel = synchronized(notificationBuffer) {
                notificationBuffer.filter { it.packageName == packageName }
            }
            toCancel.forEach { cancelNotification(it.packageName, null, it.id) }
            synchronized(notificationBuffer) {
                notificationBuffer.removeAll { it.packageName == packageName }
            }
        } catch (e: Exception) {
            Log.e(TAG, "dismissFromApp error: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.split(".").lastOrNull() ?: packageName
        }
    }

    private fun calculatePriority(sbn: StatusBarNotification, title: String, body: String): Int {
        var priority = 0

        // High priority apps
        val highPriorityApps = setOf("com.whatsapp", "org.telegram.messenger", "com.google.android.gm")
        if (sbn.packageName in highPriorityApps) priority += 3

        // Keywords that signal importance
        val urgentKeywords = listOf("urgent", "important", "payment", "otp", "verify", "call", "missed")
        val combined = "$title $body".lowercase()
        urgentKeywords.forEach { if (combined.contains(it)) priority += 2 }

        return priority
    }

    private fun buildFallbackSummary(grouped: Map<String, List<NotificationItem>>): String {
        val total = notificationBuffer.size
        val apps = grouped.keys.take(3).joinToString(", ")
        return "You have $total notification${if (total != 1) "s" else ""} from: $apps${if (grouped.size > 3) " and more" else ""}."
    }
}
