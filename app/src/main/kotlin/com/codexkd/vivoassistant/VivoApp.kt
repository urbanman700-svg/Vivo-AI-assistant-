package com.codexkd.vivoassistant

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.codexkd.vivoassistant.ai.CloudAIManager
import com.codexkd.vivoassistant.memory.MemoryDatabase
import com.codexkd.vivoassistant.memory.MemoryEngine
import com.codexkd.vivoassistant.utils.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// DataStore extension property (singleton)
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vivo_prefs")

/**
 * VivoApp — Application class.
 *
 * Global initialization:
 * - DataStore preferences
 * - Room database
 * - AI Manager configuration
 * - Crash handler
 * - Lightweight global state
 */
class VivoApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d(TAG, "VivoApp starting...")

        // Install global crash handler (log + recover)
        setupCrashHandler()

        // Warm up database (background, non-blocking)
        appScope.launch {
            try {
                MemoryDatabase.getInstance(this@VivoApp)
                Log.d(TAG, "Database ready ✓")
            } catch (e: Exception) {
                Log.e(TAG, "Database init error: ${e.message}")
            }
        }

        // Configure AI manager from saved preferences
        appScope.launch {
            try {
                configureAIFromPrefs()
                Log.d(TAG, "AI configured ✓")
            } catch (e: Exception) {
                Log.e(TAG, "AI config error: ${e.message}")
            }
        }

        Log.d(TAG, "VivoApp ready ✓")
    }

    // ═══════════════════════════════════════════════
    // AI CONFIGURATION FROM DATASTORE
    // ═══════════════════════════════════════════════

    private suspend fun configureAIFromPrefs() {
        val prefs = dataStore.data.first()

        val apiKey     = prefs[stringPreferencesKey(Constants.PREF_API_KEY)]     ?: ""
        val model      = prefs[stringPreferencesKey(Constants.PREF_AI_MODEL)]    ?: Constants.DEFAULT_AI_MODEL
        val personality = prefs[stringPreferencesKey(Constants.PREF_AI_PERSONALITY)] ?: Constants.PERSONALITY_JARVIS

        if (apiKey.isNotBlank()) {
            CloudAIManager.getInstance(this).configure(apiKey, model, personality)
            Log.d(TAG, "AI configured: model=$model")
        } else {
            Log.w(TAG, "No API key set — AI chat disabled until configured")
        }
    }

    fun refreshAIConfig() {
        appScope.launch { configureAIFromPrefs() }
    }

    // ═══════════════════════════════════════════════
    // DATASTORE HELPERS (for use across the app)
    // ═══════════════════════════════════════════════

    suspend fun savePreference(key: String, value: String) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey(key)] = value
        }
    }

    suspend fun savePreference(key: String, value: Boolean) {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(key)] = value
        }
    }

    suspend fun getStringPref(key: String, default: String = ""): String {
        return dataStore.data.map { prefs ->
            prefs[stringPreferencesKey(key)] ?: default
        }.first()
    }

    suspend fun getBoolPref(key: String, default: Boolean = false): Boolean {
        return dataStore.data.map { prefs ->
            prefs[booleanPreferencesKey(key)] ?: default
        }.first()
    }

    // ═══════════════════════════════════════════════
    // CRASH HANDLER
    // ═══════════════════════════════════════════════

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "UNCAUGHT CRASH on ${thread.name}: ${throwable.message}", throwable)

            // Attempt graceful cleanup
            try {
                MemoryDatabase.destroy()
            } catch (e: Exception) {
                // Ignore cleanup errors during crash
            }

            // Pass to default handler (shows crash dialog / restarts)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Respond to memory pressure
        when (level) {
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "CRITICAL memory pressure — trimming caches")
                // CloudAI http client will evict connections automatically
                System.gc()
            }
        }
    }

    companion object {
        private const val TAG = "VivoApp"

        @Volatile
        private var instance: VivoApp? = null

        fun get(): VivoApp = instance ?: throw IllegalStateException("VivoApp not initialized")
    }
}
