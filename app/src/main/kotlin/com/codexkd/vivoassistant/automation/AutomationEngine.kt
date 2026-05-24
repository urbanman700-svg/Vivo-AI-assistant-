package com.codexkd.vivoassistant.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.codexkd.vivoassistant.accessibility.AssistantAccessibilityService
import com.codexkd.vivoassistant.memory.MemoryEngine
import com.codexkd.vivoassistant.models.*
import com.codexkd.vivoassistant.utils.Constants
import kotlinx.coroutines.*

/**
 * AutomationEngine — Parses commands and routes them to the correct executor.
 *
 * This is the dispatcher layer between the AI output and actual Android actions.
 * It:
 * - Parses raw text commands into AutomationCommand objects
 * - Routes to SystemController (settings) or AccessibilityService (UI interaction)
 * - Returns human-readable confirmation text
 * - Tracks command execution in MemoryEngine
 */
class AutomationEngine(private val context: Context) {

    private val systemController = SystemController(context)
    private val memoryEngine = MemoryEngine.getInstance(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Callback for speaking confirmations
    var onCommandConfirmation: ((String) -> Unit)? = null

    // ═══════════════════════════════════════════════
    // COMMAND PARSING
    // ═══════════════════════════════════════════════

    /**
     * Parse raw text into structured AutomationCommand.
     * Called after AI classifies input as an automation command.
     */
    fun parseCommand(rawInput: String): AutomationCommand {
        val lower = rawInput.lowercase().trim()

        return when {
            // ─── APP OPENING ───────────────────────────
            Constants.CMD_OPEN_APP.any { lower.contains(it) } -> {
                val appName = extractAppName(lower)
                AutomationCommand(rawInput, CommandType.OPEN_APP, target = appName)
            }

            // ─── BRIGHTNESS ────────────────────────────
            Constants.CMD_BRIGHTNESS.any { lower.contains(it) } -> {
                val value = extractNumber(lower)?.toString() ?: extractBrightnessLevel(lower)
                AutomationCommand(rawInput, CommandType.SET_BRIGHTNESS, value = value)
            }

            // ─── VOLUME ────────────────────────────────
            Constants.CMD_VOLUME.any { lower.contains(it) } -> {
                val value = extractNumber(lower)?.toString()
                    ?: if (lower.contains("mute") || lower.contains("silent")) "0"
                    else if (lower.contains("max") || lower.contains("full")) "100"
                    else "50"
                AutomationCommand(rawInput, CommandType.SET_VOLUME, value = value)
            }

            // ─── FLASHLIGHT ────────────────────────────
            Constants.CMD_FLASHLIGHT.any { lower.contains(it) } -> {
                val on = !lower.contains("off") && !lower.contains("band")
                AutomationCommand(rawInput, CommandType.TOGGLE_FLASHLIGHT, value = if (on) "on" else "off")
            }

            // ─── WIFI ──────────────────────────────────
            Constants.CMD_WIFI.any { lower.contains(it) } -> {
                val on = !lower.contains("off") && !lower.contains("band")
                AutomationCommand(rawInput, CommandType.TOGGLE_WIFI, value = if (on) "on" else "off")
            }

            // ─── BLUETOOTH ─────────────────────────────
            Constants.CMD_BLUETOOTH.any { lower.contains(it) } -> {
                val on = !lower.contains("off") && !lower.contains("band")
                AutomationCommand(rawInput, CommandType.TOGGLE_BLUETOOTH, value = if (on) "on" else "off")
            }

            // ─── DND ───────────────────────────────────
            Constants.CMD_DND.any { lower.contains(it) } -> {
                val on = !lower.contains("off") && !lower.contains("hatao")
                AutomationCommand(rawInput, CommandType.TOGGLE_DND, value = if (on) "on" else "off")
            }

            // ─── SCREENSHOT ────────────────────────────
            Constants.CMD_SCREENSHOT.any { lower.contains(it) } -> {
                AutomationCommand(rawInput, CommandType.TAKE_SCREENSHOT)
            }

            // ─── ALARM ─────────────────────────────────
            Constants.CMD_ALARM.any { lower.contains(it) } -> {
                val time = extractTimeFromText(lower)
                AutomationCommand(rawInput, CommandType.SET_ALARM, value = time)
            }

            // ─── SEND MESSAGE ──────────────────────────
            Constants.CMD_REPLY.any { lower.startsWith(it) } -> {
                val messageText = extractMessageText(lower)
                AutomationCommand(rawInput, CommandType.SEND_MESSAGE, value = messageText)
            }

            // ─── NAVIGATION ────────────────────────────
            Constants.CMD_BACK.any { lower.contains(it) } ->
                AutomationCommand(rawInput, CommandType.NAVIGATE_BACK)

            Constants.CMD_HOME.any { lower.contains(it) } ->
                AutomationCommand(rawInput, CommandType.NAVIGATE_HOME)

            // ─── SCROLL ────────────────────────────────
            Constants.CMD_SCROLL.any { lower.contains(it) } -> {
                val dir = if (lower.contains("up") || lower.contains("upar")) "up" else "down"
                AutomationCommand(rawInput, CommandType.SCROLL, value = dir)
            }

            // ─── READ SCREEN ───────────────────────────
            Constants.CMD_NOTIFICATIONS.any { lower.contains(it) } ->
                AutomationCommand(rawInput, CommandType.SUMMARIZE_NOTIFICATIONS)

            // ─── DEFAULT ───────────────────────────────
            else -> AutomationCommand(rawInput, CommandType.UNKNOWN)
        }
    }

    // ═══════════════════════════════════════════════
    // COMMAND EXECUTION
    // ═══════════════════════════════════════════════

    /**
     * Execute a parsed command and return confirmation message.
     */
    suspend fun execute(command: AutomationCommand): String = withContext(Dispatchers.Main) {
        Log.d(TAG, "Executing: ${command.type} target=${command.target} value=${command.value}")

        // Track for habit learning
        CoroutineScope(Dispatchers.IO).launch {
            memoryEngine.trackCommand(command.rawInput)
        }

        return@withContext when (command.type) {

            CommandType.OPEN_APP -> {
                val result = openApp(command.target)
                if (result) {
                    memoryEngine.trackAppOpen(command.target)
                    "Opening ${command.target}."
                } else {
                    "${command.target} not found. Try installing it from Play Store."
                }
            }

            CommandType.SET_BRIGHTNESS -> {
                val level = command.value.toIntOrNull() ?: 70
                systemController.setBrightness(level)
                "Brightness set to $level%."
            }

            CommandType.SET_VOLUME -> {
                val level = command.value.toIntOrNull() ?: 50
                systemController.setVolume(level)
                if (level == 0) "Phone muted." else "Volume set to $level%."
            }

            CommandType.TOGGLE_FLASHLIGHT -> {
                val on = command.value == "on"
                systemController.setFlashlight(on)
                if (on) "Torch on." else "Torch off."
            }

            CommandType.TOGGLE_WIFI -> {
                val on = command.value == "on"
                systemController.openWifiSettings()
                "Opening WiFi settings." // Android 10+ requires user to toggle
            }

            CommandType.TOGGLE_BLUETOOTH -> {
                val on = command.value == "on"
                systemController.toggleBluetooth(on)
                if (on) "Turning on Bluetooth." else "Turning off Bluetooth."
            }

            CommandType.TOGGLE_DND -> {
                val on = command.value == "on"
                systemController.setDND(on)
                if (on) "Do Not Disturb enabled." else "DND disabled. Notifications restored."
            }

            CommandType.TAKE_SCREENSHOT -> {
                AssistantAccessibilityService.getInstance()?.takeScreenshot()
                "Taking screenshot."
            }

            CommandType.SET_ALARM -> {
                openAlarmApp(command.value)
                "Opening Clock app${if (command.value.isNotEmpty()) " for ${command.value}" else ""}."
            }

            CommandType.SEND_MESSAGE -> {
                AssistantAccessibilityService.getInstance()?.typeAndSend(command.value)
                "Typed your message. Check before sending."
            }

            CommandType.NAVIGATE_BACK -> {
                AssistantAccessibilityService.getInstance()?.pressBack()
                "Going back."
            }

            CommandType.NAVIGATE_HOME -> {
                AssistantAccessibilityService.getInstance()?.pressHome()
                "Going to home screen."
            }

            CommandType.NAVIGATE_RECENT -> {
                AssistantAccessibilityService.getInstance()?.pressRecents()
                "Showing recent apps."
            }

            CommandType.SCROLL -> {
                val up = command.value == "up"
                AssistantAccessibilityService.getInstance()?.scroll(up)
                if (up) "Scrolling up." else "Scrolling down."
            }

            CommandType.READ_SCREEN -> {
                "Let me read the screen." // Handled by ScreenIntelligence
            }

            CommandType.SUMMARIZE_NOTIFICATIONS -> {
                "Let me check your notifications." // Handled by NotificationAIService
            }

            CommandType.START_ROUTINE -> {
                "Starting ${command.target} routine."
            }

            CommandType.UNKNOWN, CommandType.AI_CHAT -> {
                "" // Let AI handle it
            }

            else -> "Command executed."
        }
    }

    // ═══════════════════════════════════════════════
    // APP LAUNCHING
    // ═══════════════════════════════════════════════

    private fun openApp(appName: String): Boolean {
        val lower = appName.lowercase().trim()

        // Check known apps map
        val packageName = Constants.APP_MAP.entries
            .firstOrNull { lower.contains(it.key) }?.value

        if (packageName != null) {
            return launchByPackage(packageName)
        }

        // Try searching installed apps
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(0)

        val match = installedApps.firstOrNull { app ->
            val label = pm.getApplicationLabel(app).toString().lowercase()
            label.contains(lower) || lower.contains(label)
        }

        return if (match != null) {
            launchByPackage(match.packageName)
        } else {
            false
        }
    }

    private fun launchByPackage(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "launchByPackage error: ${e.message}")
            false
        }
    }

    private fun openAlarmApp(time: String) {
        try {
            val intent = Intent(Intent.ACTION_SET_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (time.isNotEmpty()) {
                    // Attempt to parse time and pre-fill
                    val parts = time.split(":")
                    if (parts.size == 2) {
                        putExtra("android.intent.extra.alarm.HOUR", parts[0].toIntOrNull() ?: 7)
                        putExtra("android.intent.extra.alarm.MINUTES", parts[1].toIntOrNull() ?: 0)
                    }
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: open clock app
            launchByPackage("com.android.deskclock")
        }
    }

    // ═══════════════════════════════════════════════
    // TEXT PARSERS
    // ═══════════════════════════════════════════════

    private fun extractAppName(input: String): String {
        val stopWords = Constants.CMD_OPEN_APP +
                listOf("please", "karo", "kardo", "zara", "mujhe")

        var result = input
        stopWords.forEach { result = result.replace(it, " ") }
        return result.trim().ifBlank { "app" }
    }

    private fun extractNumber(input: String): Int? {
        return Regex("\\d+").find(input)?.value?.toIntOrNull()
    }

    private fun extractBrightnessLevel(input: String): String {
        return when {
            input.contains("full") || input.contains("max") || input.contains("zyada") -> "100"
            input.contains("half") || input.contains("medium")                         -> "50"
            input.contains("low") || input.contains("dim") || input.contains("kam")    -> "20"
            else                                                                        -> "70"
        }
    }

    private fun extractMessageText(input: String): String {
        val stopWords = Constants.CMD_REPLY
        var result = input
        stopWords.forEach { result = result.removePrefix(it).trim() }
        return result.removePrefix(":").trim()
    }

    private fun extractTimeFromText(input: String): String {
        // Match patterns like "7 am", "7:30", "7 baje"
        val timeRegex = Regex("(\\d{1,2})(:(\\d{2}))? ?(am|pm|baje|bajke)?", RegexOption.IGNORE_CASE)
        val match = timeRegex.find(input) ?: return ""

        val hour = match.groupValues[1].toIntOrNull() ?: return ""
        val minute = match.groupValues[3].toIntOrNull() ?: 0
        val ampm = match.groupValues[4].lowercase()

        val finalHour = when {
            ampm == "pm" && hour < 12 -> hour + 12
            ampm == "am" && hour == 12 -> 0
            else -> hour
        }

        return String.format("%02d:%02d", finalHour, minute)
    }

    companion object {
        private const val TAG = "AutomationEngine"

        @Volatile
        private var INSTANCE: AutomationEngine? = null

        fun getInstance(context: Context): AutomationEngine {
            return INSTANCE ?: synchronized(this) {
                AutomationEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
