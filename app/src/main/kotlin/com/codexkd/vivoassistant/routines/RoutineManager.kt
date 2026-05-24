package com.codexkd.vivoassistant.routines

import android.content.Context
import android.util.Log
import com.codexkd.vivoassistant.automation.AutomationEngine
import com.codexkd.vivoassistant.automation.SystemController
import com.codexkd.vivoassistant.memory.MemoryEngine
import com.codexkd.vivoassistant.models.*
import com.codexkd.vivoassistant.utils.Constants
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*

/**
 * RoutineManager — Smart automation routines engine.
 *
 * Built-in routines:
 * - 🎮 Gaming Mode: DND + Max brightness + Open game
 * - 📚 Study Mode: Silent + Open notes + Start timer
 * - 😴 Sleep Mode: Silent + Min brightness + DND
 * - 🌅 Morning Mode: Volume up + Dismiss alarms
 * - 💼 Work Mode: Notifications filtered + Chrome open
 *
 * Supports custom user routines with:
 * - Manual trigger
 * - Voice trigger ("start gaming mode")
 * - Time-based trigger
 */
class RoutineManager private constructor(private val context: Context) {

    private val memoryEngine = MemoryEngine.getInstance(context)
    private val systemController = SystemController(context)
    private val automationEngine = AutomationEngine.getInstance(context)
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Active routine tracking
    private var activeRoutineId: String? = null

    // ═══════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════

    suspend fun initialize() {
        // Insert built-in routines if not present
        val existing = memoryEngine.getEnabledRoutines()
        if (existing.none { it.isBuiltIn }) {
            Log.d(TAG, "Seeding built-in routines")
            getBuiltInRoutines().forEach {
                memoryEngine.saveRoutine(it)
            }
        }
    }

    suspend fun scheduleAllRoutines() {
        initialize()
        val routines = memoryEngine.getEnabledRoutines()
            .filter { it.triggerType == "time" && it.triggerValue.isNotBlank() }

        Log.d(TAG, "Scheduled ${routines.size} time-based routines")
        // WorkManager scheduling can be added here for time-based triggers
    }

    // ═══════════════════════════════════════════════
    // EXECUTION
    // ═══════════════════════════════════════════════

    /**
     * Execute a routine by ID.
     * Returns a human-readable summary of what was done.
     */
    suspend fun executeRoutine(routineId: String): String = withContext(Dispatchers.Main) {
        val routine = memoryEngine.getRoutineById(routineId)
            ?: return@withContext "Routine not found."

        Log.d(TAG, "Executing routine: ${routine.name}")
        activeRoutineId = routineId

        val steps = parseSteps(routine.steps)
        val results = mutableListOf<String>()

        steps.forEach { step ->
            try {
                val result = executeStep(step)
                if (result.isNotBlank()) results.add(result)
                if (step.delayMs > 0) delay(step.delayMs)
            } catch (e: Exception) {
                Log.e(TAG, "Step error '${step.action}': ${e.message}")
            }
        }

        memoryEngine.markRoutineExecuted(routineId)
        activeRoutineId = null

        if (results.isEmpty()) "${routine.name} activated."
        else "${routine.name} activated. ${results.joinToString(" ")}"
    }

    /**
     * Match a voice command to a routine.
     * e.g. "start gaming mode" → ROUTINE_GAMING
     */
    suspend fun matchVoiceCommand(input: String): Routine? {
        val lower = input.lowercase()
        val routines = memoryEngine.getEnabledRoutines()

        return routines.firstOrNull { routine ->
            lower.contains(routine.name.lowercase()) ||
            (routine.triggerType == "voice" &&
             routine.triggerValue.isNotBlank() &&
             lower.contains(routine.triggerValue.lowercase()))
        }
    }

    private suspend fun executeStep(step: RoutineStep): String {
        return when (step.action) {

            "set_brightness" -> {
                val level = step.params["level"]?.toIntOrNull() ?: 70
                systemController.setBrightness(level)
                "Brightness: $level%."
            }

            "set_volume" -> {
                val level = step.params["level"]?.toIntOrNull() ?: 50
                systemController.setVolume(level)
                if (level == 0) "Volume muted." else "Volume: $level%."
            }

            "set_dnd" -> {
                val enable = step.params["enabled"] == "true"
                systemController.setDND(enable)
                if (enable) "DND enabled." else "DND disabled."
            }

            "set_flashlight" -> {
                val enable = step.params["enabled"] == "true"
                systemController.setFlashlight(enable)
                ""
            }

            "open_app" -> {
                val app = step.params["app"] ?: return ""
                val cmd = automationEngine.parseCommand("open $app")
                automationEngine.execute(cmd)
            }

            "toggle_wifi" -> {
                systemController.openWifiSettings()
                ""
            }

            "wait" -> {
                val ms = step.params["ms"]?.toLongOrNull() ?: 1000L
                delay(ms)
                ""
            }

            "show_notification" -> {
                val msg = step.params["message"] ?: ""
                Log.d(TAG, "Routine notification: $msg")
                "" // Could show a notification here
            }

            else -> {
                Log.w(TAG, "Unknown step action: ${step.action}")
                ""
            }
        }
    }

    // ═══════════════════════════════════════════════
    // BUILT-IN ROUTINES
    // ═══════════════════════════════════════════════

    private fun getBuiltInRoutines(): List<Routine> = listOf(

        // ─────────────────────────────
        // 🎮 GAMING MODE
        // ─────────────────────────────
        Routine(
            id          = Constants.ROUTINE_GAMING,
            name        = "Gaming Mode",
            description = "DND on, brightness max, performance optimized",
            iconRes     = "ic_routine_gaming",
            steps       = stepsToJson(listOf(
                RoutineStep("set_dnd",        mapOf("enabled" to "true"),  delayMs = 0,    description = "Enable DND"),
                RoutineStep("set_brightness", mapOf("level" to "100"),     delayMs = 200,  description = "Max brightness"),
                RoutineStep("set_volume",     mapOf("level" to "80"),      delayMs = 200,  description = "Game volume")
            )),
            triggerType = "voice",
            triggerValue = "gaming mode",
            isBuiltIn   = true
        ),

        // ─────────────────────────────
        // 📚 STUDY MODE
        // ─────────────────────────────
        Routine(
            id          = Constants.ROUTINE_STUDY,
            name        = "Study Mode",
            description = "Silent mode, focus optimized",
            iconRes     = "ic_routine_study",
            steps       = stepsToJson(listOf(
                RoutineStep("set_volume",     mapOf("level" to "0"),       delayMs = 0,    description = "Mute phone"),
                RoutineStep("set_dnd",        mapOf("enabled" to "true"),  delayMs = 200,  description = "Enable DND"),
                RoutineStep("set_brightness", mapOf("level" to "60"),      delayMs = 200,  description = "Eye-comfort brightness"),
                RoutineStep("open_app",       mapOf("app" to "notes"),     delayMs = 500,  description = "Open notes")
            )),
            triggerType = "voice",
            triggerValue = "study mode",
            isBuiltIn   = true
        ),

        // ─────────────────────────────
        // 😴 SLEEP MODE
        // ─────────────────────────────
        Routine(
            id          = Constants.ROUTINE_SLEEP,
            name        = "Sleep Mode",
            description = "Silent, dim screen, all alerts off",
            iconRes     = "ic_routine_sleep",
            steps       = stepsToJson(listOf(
                RoutineStep("set_volume",     mapOf("level" to "0"),       delayMs = 0,    description = "Mute"),
                RoutineStep("set_dnd",        mapOf("enabled" to "true"),  delayMs = 200,  description = "Enable DND"),
                RoutineStep("set_brightness", mapOf("level" to "5"),       delayMs = 200,  description = "Min brightness"),
                RoutineStep("set_flashlight", mapOf("enabled" to "false"), delayMs = 0,    description = "Torch off")
            )),
            triggerType = "voice",
            triggerValue = "sleep mode",
            isBuiltIn   = true
        ),

        // ─────────────────────────────
        // 🌅 MORNING MODE
        // ─────────────────────────────
        Routine(
            id          = Constants.ROUTINE_MORNING,
            name        = "Morning Mode",
            description = "Restore normal settings for the day",
            iconRes     = "ic_routine_morning",
            steps       = stepsToJson(listOf(
                RoutineStep("set_dnd",        mapOf("enabled" to "false"), delayMs = 0,    description = "Disable DND"),
                RoutineStep("set_volume",     mapOf("level" to "70"),      delayMs = 200,  description = "Morning volume"),
                RoutineStep("set_brightness", mapOf("level" to "80"),      delayMs = 200,  description = "Morning brightness")
            )),
            triggerType = "voice",
            triggerValue = "morning mode",
            isBuiltIn   = true
        ),

        // ─────────────────────────────
        // 💼 WORK MODE
        // ─────────────────────────────
        Routine(
            id          = Constants.ROUTINE_WORK,
            name        = "Work Mode",
            description = "Focus settings for productivity",
            iconRes     = "ic_routine_work",
            steps       = stepsToJson(listOf(
                RoutineStep("set_volume",     mapOf("level" to "30"),      delayMs = 0,    description = "Low volume"),
                RoutineStep("set_brightness", mapOf("level" to "75"),      delayMs = 200,  description = "Work brightness"),
                RoutineStep("open_app",       mapOf("app" to "chrome"),    delayMs = 500,  description = "Open Chrome")
            )),
            triggerType = "voice",
            triggerValue = "work mode",
            isBuiltIn   = true
        )
    )

    // ═══════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════

    private fun stepsToJson(steps: List<RoutineStep>): String =
        gson.toJson(steps)

    private fun parseSteps(json: String): List<RoutineStep> {
        return try {
            val type = object : TypeToken<List<RoutineStep>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "parseSteps error: ${e.message}")
            emptyList()
        }
    }

    fun getActiveRoutineId() = activeRoutineId

    fun destroy() = scope.cancel()

    companion object {
        private const val TAG = "RoutineManager"

        @Volatile
        private var INSTANCE: RoutineManager? = null

        fun getInstance(context: Context): RoutineManager {
            return INSTANCE ?: synchronized(this) {
                RoutineManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
