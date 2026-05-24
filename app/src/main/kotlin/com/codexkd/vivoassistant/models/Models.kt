package com.codexkd.vivoassistant.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

// ═══════════════════════════════════════════════════════
// MESSAGE MODEL — Chat messages (stored in Room DB)
// ═══════════════════════════════════════════════════════
@Parcelize
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String,           // "user" | "assistant" | "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = "",
    val isVoice: Boolean = false,
    val model: String = "",
    val tokensUsed: Int = 0,
    val isError: Boolean = false
) : Parcelable {
    companion object {
        fun user(text: String, session: String = "", voice: Boolean = false) =
            Message(role = "user", content = text, sessionId = session, isVoice = voice)

        fun assistant(text: String, session: String = "", model: String = "") =
            Message(role = "assistant", content = text, sessionId = session, model = model)

        fun system(text: String) =
            Message(role = "system", content = text)

        fun error(text: String) =
            Message(role = "assistant", content = text, isError = true)
    }
}

// ═══════════════════════════════════════════════════════
// ROUTINE MODEL — Smart automation routines
// ═══════════════════════════════════════════════════════
@Parcelize
@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val iconRes: String = "",
    val steps: String,              // JSON array of RoutineStep
    val triggerType: String,        // "manual" | "voice" | "time" | "context"
    val triggerValue: String = "",  // time string or context keyword
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val lastExecuted: Long = 0L,
    val executeCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable

// ═══════════════════════════════════════════════════════
// ROUTINE STEP — Individual action within a routine
// ═══════════════════════════════════════════════════════
@Parcelize
data class RoutineStep(
    val action: String,     // "open_app" | "set_volume" | "set_brightness" | etc.
    val params: Map<String, String> = emptyMap(),
    val delayMs: Long = 0L,
    val description: String = ""
) : Parcelable

// ═══════════════════════════════════════════════════════
// AI SESSION — Tracks a live AI conversation session
// ═══════════════════════════════════════════════════════
data class AISession(
    val id: String = generateSessionId(),
    val startTime: Long = System.currentTimeMillis(),
    var lastActivity: Long = System.currentTimeMillis(),
    var messageCount: Int = 0,
    var totalTokens: Int = 0,
    val personality: String = "jarvis",
    val model: String = "",
    var isActive: Boolean = true,
    var currentApp: String = "",    // App user is using during session
    var context: String = ""        // Injected context
) {
    fun isExpired(): Boolean {
        val idleMs = System.currentTimeMillis() - lastActivity
        return idleMs > SESSION_TIMEOUT_MS
    }

    companion object {
        const val SESSION_TIMEOUT_MS = 5 * 60 * 1000L  // 5 minutes idle = expired

        fun generateSessionId(): String {
            return "session_${System.currentTimeMillis()}"
        }
    }
}

// ═══════════════════════════════════════════════════════
// CONTEXT SNAPSHOT — What assistant knows right now
// ═══════════════════════════════════════════════════════
data class ContextSnapshot(
    val currentApp: String = "",
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val isOnline: Boolean = true,
    val networkType: String = "",
    val timeOfDay: String = "",    // morning | afternoon | evening | night
    val isHeadphonesConnected: Boolean = false,
    val isDNDEnabled: Boolean = false,
    val currentVolume: Int = 0,
    val brightness: Int = 0,
    val recentNotifications: List<String> = emptyList()
)

// ═══════════════════════════════════════════════════════
// NOTIFICATION ITEM — Captured notification data
// ═══════════════════════════════════════════════════════
data class NotificationItem(
    val id: Int,
    val packageName: String,
    val appName: String,
    val title: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    var aiSummary: String = "",
    var isRead: Boolean = false,
    val priority: Int = 0
)

// ═══════════════════════════════════════════════════════
// AI REQUEST/RESPONSE — OpenRouter API models
// ═══════════════════════════════════════════════════════
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int = 800,
    val temperature: Float = 0.7f,
    val stream: Boolean = false
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val id: String = "",
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
    val error: ErrorBody? = null
)

data class Choice(
    val message: ChatMessage,
    val finish_reason: String = ""
)

data class Usage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0
)

data class ErrorBody(
    val message: String = "",
    val code: String = ""
)

// ═══════════════════════════════════════════════════════
// AUTOMATION COMMAND — Parsed voice/text command
// ═══════════════════════════════════════════════════════
data class AutomationCommand(
    val rawInput: String,
    val type: CommandType,
    val target: String = "",        // App name, setting name, etc.
    val value: String = "",         // Value to set
    val extra: String = "",         // Additional context
    val confidence: Float = 1.0f
)

enum class CommandType {
    OPEN_APP,
    CLOSE_APP,
    SET_BRIGHTNESS,
    SET_VOLUME,
    TOGGLE_WIFI,
    TOGGLE_BLUETOOTH,
    TOGGLE_FLASHLIGHT,
    TOGGLE_DND,
    TAKE_SCREENSHOT,
    SET_ALARM,
    SEND_MESSAGE,
    SCROLL,
    NAVIGATE_BACK,
    NAVIGATE_HOME,
    NAVIGATE_RECENT,
    CLICK_ELEMENT,
    TYPE_TEXT,
    READ_SCREEN,
    SUMMARIZE_NOTIFICATIONS,
    START_ROUTINE,
    AI_CHAT,            // Pass to cloud AI
    UNKNOWN
}
