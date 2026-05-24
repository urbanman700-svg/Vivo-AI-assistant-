package com.codexkd.vivoassistant.memory

import android.content.Context
import android.util.Log
import com.codexkd.vivoassistant.models.ContextSnapshot
import com.codexkd.vivoassistant.models.Message
import com.codexkd.vivoassistant.models.Routine
import com.codexkd.vivoassistant.utils.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * MemoryEngine — The brain of Vivo Assistant's context system.
 *
 * Responsibilities:
 * - Store and retrieve conversation history
 * - Learn user habits and preferences
 * - Build context for AI prompts
 * - Manage predictive suggestions
 * - Track frequently used apps/commands
 */
class MemoryEngine private constructor(context: Context) {

    private val db = MemoryDatabase.getInstance(context)
    private val msgDao = db.messageDao()
    private val routineDao = db.routineDao()
    private val ctxDao = db.contextDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // In-memory cache for fast access
    private val recentCommands = ArrayDeque<String>(20)
    private val appUsageCount = mutableMapOf<String, Int>()
    private var currentSnapshot: ContextSnapshot? = null

    // ═══════════════════════════════════════════════
    // MESSAGE STORAGE
    // ═══════════════════════════════════════════════

    suspend fun saveMessage(message: Message): Long {
        // Enforce history limit
        val count = msgDao.getCount()
        if (count >= Constants.MAX_CHAT_HISTORY) {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            msgDao.deleteOlderThan(cutoff)
        }
        return msgDao.insert(message)
    }

    fun getMessagesFlow(): Flow<List<Message>> =
        msgDao.getRecentMessages(50)

    suspend fun getSessionHistory(sessionId: String): List<Message> =
        msgDao.getSessionMessages(sessionId)

    suspend fun getAIHistory(limit: Int = Constants.MAX_HISTORY_MESSAGES): List<Message> =
        msgDao.getHistoryForAI(limit)

    suspend fun clearHistory() {
        msgDao.deleteAll()
    }

    suspend fun searchHistory(query: String): List<Message> =
        msgDao.search(query)

    // ═══════════════════════════════════════════════
    // CONTEXT BUILDING — For AI system prompts
    // ═══════════════════════════════════════════════

    /**
     * Builds a rich context string to inject into AI system prompt.
     * This gives the AI awareness of user's habits and current state.
     */
    suspend fun buildContextForAI(): String {
        val sb = StringBuilder()
        val snapshot = currentSnapshot

        // Device state
        if (snapshot != null) {
            sb.appendLine("=== Current Device State ===")
            if (snapshot.currentApp.isNotEmpty())
                sb.appendLine("User is currently in: ${snapshot.currentApp}")
            sb.appendLine("Battery: ${snapshot.batteryLevel}%${if (snapshot.isCharging) " (charging)" else ""}")
            sb.appendLine("Network: ${if (snapshot.isOnline) "Online (${snapshot.networkType})" else "Offline"}")
            sb.appendLine("Time: ${snapshot.timeOfDay}")
            if (snapshot.isDNDEnabled) sb.appendLine("DND mode is active")
            if (snapshot.isHeadphonesConnected) sb.appendLine("Headphones connected")
        }

        // User habits
        val habits = getTopHabits(5)
        if (habits.isNotEmpty()) {
            sb.appendLine("\n=== User Habits ===")
            habits.forEach { sb.appendLine("- $it") }
        }

        // Frequent apps
        val topApps = getTopApps(3)
        if (topApps.isNotEmpty()) {
            sb.appendLine("\n=== Frequently Used Apps ===")
            sb.appendLine(topApps.joinToString(", "))
        }

        return sb.toString().trim()
    }

    /**
     * Updates the current context snapshot (called from ContextAwareness)
     */
    fun updateSnapshot(snapshot: ContextSnapshot) {
        currentSnapshot = snapshot
    }

    // ═══════════════════════════════════════════════
    // HABIT LEARNING
    // ═══════════════════════════════════════════════

    fun trackCommand(command: String) {
        recentCommands.addFirst(command)
        if (recentCommands.size > 20) recentCommands.removeLast()

        scope.launch {
            // Persist command frequency
            val key = "cmd_freq_${command.lowercase().take(30)}"
            val existing = ctxDao.get(key)
            val count = existing?.value?.toIntOrNull() ?: 0
            ctxDao.set(ContextEntity(key, (count + 1).toString()))
        }
    }

    fun trackAppOpen(appName: String) {
        appUsageCount[appName] = (appUsageCount[appName] ?: 0) + 1

        scope.launch {
            val key = "app_open_$appName"
            val existing = ctxDao.get(key)
            val count = existing?.value?.toIntOrNull() ?: 0
            ctxDao.set(ContextEntity(key, (count + 1).toString()))
        }
    }

    private suspend fun getTopApps(limit: Int): List<String> {
        return ctxDao.getRecent(50)
            .filter { it.key.startsWith("app_open_") }
            .sortedByDescending { it.value.toIntOrNull() ?: 0 }
            .take(limit)
            .map { it.key.removePrefix("app_open_") }
    }

    private suspend fun getTopHabits(limit: Int): List<String> {
        return ctxDao.getRecent(50)
            .filter { it.key.startsWith("cmd_freq_") }
            .sortedByDescending { it.value.toIntOrNull() ?: 0 }
            .take(limit)
            .map { "Uses '${it.key.removePrefix("cmd_freq_")}' frequently (${it.value}x)" }
    }

    // ═══════════════════════════════════════════════
    // USER PREFERENCES
    // ═══════════════════════════════════════════════

    suspend fun savePreference(key: String, value: String) {
        ctxDao.set(ContextEntity("pref_$key", value))
    }

    suspend fun getPreference(key: String): String? {
        return ctxDao.get("pref_$key")?.value
    }

    // ═══════════════════════════════════════════════
    // PREDICTIVE SUGGESTIONS
    // ═══════════════════════════════════════════════

    /**
     * Based on time of day + habits, suggest relevant actions.
     * Called when overlay is opened.
     */
    suspend fun getPredictiveSuggestions(): List<String> {
        val suggestions = mutableListOf<String>()
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

        // Time-based suggestions
        when {
            hour in 6..9   -> suggestions.add("Good morning! Want to check today's schedule?")
            hour in 9..12  -> suggestions.add("Work mode — enable focus?")
            hour in 12..14 -> suggestions.add("Lunch time. Play some music?")
            hour in 18..20 -> suggestions.add("Evening wind-down. Enable sleep mode?")
            hour in 22..23 -> suggestions.add("Night mode? Reduce brightness and enable DND?")
        }

        // Habit-based suggestions
        val topApps = getTopApps(3)
        topApps.take(2).forEach { app ->
            suggestions.add("Open $app")
        }

        return suggestions.take(4)
    }

    // ═══════════════════════════════════════════════
    // ROUTINES
    // ═══════════════════════════════════════════════

    fun getRoutinesFlow(): Flow<List<Routine>> = routineDao.getAllRoutines()

    suspend fun saveRoutine(routine: Routine) = routineDao.insert(routine)

    suspend fun updateRoutine(routine: Routine) = routineDao.update(routine)

    suspend fun deleteRoutine(routine: Routine) = routineDao.delete(routine)

    suspend fun getRoutineById(id: String): Routine? = routineDao.getById(id)

    suspend fun getEnabledRoutines(): List<Routine> = routineDao.getEnabledRoutines()

    suspend fun markRoutineExecuted(id: String) =
        routineDao.incrementExecuteCount(id)

    // ═══════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════

    fun destroy() {
        scope.cancel()
    }

    companion object {
        private const val TAG = "MemoryEngine"

        @Volatile
        private var INSTANCE: MemoryEngine? = null

        fun getInstance(context: Context): MemoryEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = MemoryEngine(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
