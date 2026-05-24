package com.codexkd.vivoassistant.ai

import android.content.Context
import android.util.Log
import com.codexkd.vivoassistant.memory.MemoryEngine
import com.codexkd.vivoassistant.models.*
import com.codexkd.vivoassistant.utils.Constants
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * CloudAIManager — Core AI brain of Vivo Assistant.
 *
 * Handles all communication with the cloud AI (OpenRouter API).
 * Features:
 * - Multi-model support (Claude, GPT, Gemini)
 * - Personality injection via system prompts
 * - Context-aware responses
 * - Conversation history management
 * - Hindi + English multilingual support
 * - Graceful offline fallback
 * - Token usage tracking
 */
class CloudAIManager(private val context: Context) {

    private val gson: Gson = GsonBuilder().create()
    private val memoryEngine = MemoryEngine.getInstance(context)

    // OkHttp client with optimized connection pooling
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Constants.API_CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(Constants.API_READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(Constants.API_WRITE_TIMEOUT, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(3, 5, TimeUnit.MINUTES))
        .build()

    // Active session tracking
    private var currentSession: AISession? = null
    private var apiKey: String = ""
    private var currentModel: String = Constants.DEFAULT_AI_MODEL
    private var currentPersonality: String = Constants.PERSONALITY_JARVIS

    // ═══════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════

    fun configure(apiKey: String, model: String, personality: String) {
        this.apiKey = apiKey
        this.currentModel = model
        this.currentPersonality = personality
        Log.d(TAG, "Configured: model=$model, personality=$personality")
    }

    fun isConfigured(): Boolean = apiKey.isNotBlank()

    fun startSession(personality: String = currentPersonality): AISession {
        val session = AISession(
            personality = personality,
            model = currentModel
        )
        currentSession = session
        Log.d(TAG, "Session started: ${session.id}")
        return session
    }

    fun endSession() {
        currentSession?.isActive = false
        Log.d(TAG, "Session ended: ${currentSession?.id}")
        currentSession = null
    }

    // ═══════════════════════════════════════════════
    // MAIN AI CHAT — Non-streaming
    // ═══════════════════════════════════════════════

    /**
     * Send a message and get an AI response.
     * Automatically builds system prompt + injects context + history.
     *
     * @param userMessage The user's input text
     * @param extraContext Additional context to inject (e.g., screen content)
     * @return AIResult with response text or error
     */
    suspend fun chat(
        userMessage: String,
        extraContext: String = ""
    ): AIResult = withContext(Dispatchers.IO) {

        if (apiKey.isBlank()) {
            return@withContext AIResult.Error("API key not configured. Please add your key in Settings.")
        }

        try {
            // Build message list: system prompt + history + new message
            val messages = buildMessageList(userMessage, extraContext)

            val request = ChatRequest(
                model = currentModel,
                messages = messages,
                max_tokens = Constants.MAX_TOKENS,
                temperature = getTemperatureForPersonality(currentPersonality),
                stream = false
            )

            val json = gson.toJson(request)
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

            val httpRequest = Request.Builder()
                .url(Constants.AI_BASE_URL)
                .post(body)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "https://codexkd.vivoassistant")
                .header("X-Title", "Vivo Assistant")
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "API error ${response.code}: $responseBody")
                return@withContext AIResult.Error(
                    "AI service returned error ${response.code}. Check your API key."
                )
            }

            val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)

            // Handle API-level errors
            if (chatResponse.error != null) {
                return@withContext AIResult.Error(chatResponse.error.message)
            }

            val reply = chatResponse.choices.firstOrNull()?.message?.content?.trim()
                ?: return@withContext AIResult.Error("No response from AI. Try again.")

            // Update session stats
            currentSession?.let {
                it.messageCount += 2
                it.totalTokens += chatResponse.usage?.total_tokens ?: 0
                it.lastActivity = System.currentTimeMillis()
            }

            // Persist both messages
            val sessionId = currentSession?.id ?: AISession.generateSessionId()
            memoryEngine.saveMessage(Message.user(userMessage, sessionId))
            memoryEngine.saveMessage(Message.assistant(reply, sessionId, currentModel))

            // Track user command for habit learning
            memoryEngine.trackCommand(userMessage)

            AIResult.Success(
                text = reply,
                tokensUsed = chatResponse.usage?.total_tokens ?: 0,
                model = currentModel
            )

        } catch (e: IOException) {
            Log.e(TAG, "Network error: ${e.message}")
            AIResult.Error("Network error. Check your internet connection.")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            AIResult.Error("Something went wrong. Please try again.")
        }
    }

    // ═══════════════════════════════════════════════
    // QUICK AI ANALYSIS (no history, fast response)
    // ═══════════════════════════════════════════════

    /**
     * Quick one-shot AI call, no conversation history.
     * Used for: notification summaries, screen analysis, quick questions.
     */
    suspend fun quickAnalyze(
        prompt: String,
        maxTokens: Int = 300
    ): AIResult = withContext(Dispatchers.IO) {

        if (apiKey.isBlank()) return@withContext AIResult.Error("API key not set")

        try {
            val request = ChatRequest(
                model = currentModel,
                messages = listOf(ChatMessage("user", prompt)),
                max_tokens = maxTokens,
                temperature = 0.3f  // More precise for analysis
            )

            val json = gson.toJson(request)
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

            val httpRequest = Request.Builder()
                .url(Constants.AI_BASE_URL)
                .post(body)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                return@withContext AIResult.Error("Analysis failed: ${response.code}")
            }

            val chatResponse = gson.fromJson(
                response.body?.string() ?: "",
                ChatResponse::class.java
            )

            val reply = chatResponse.choices.firstOrNull()?.message?.content?.trim()
                ?: return@withContext AIResult.Error("No analysis result")

            AIResult.Success(reply, chatResponse.usage?.total_tokens ?: 0, currentModel)

        } catch (e: Exception) {
            Log.e(TAG, "Quick analyze error: ${e.message}")
            AIResult.Error("Analysis failed: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════
    // SMART COMMAND CLASSIFICATION
    // ═══════════════════════════════════════════════

    /**
     * Classify whether input is an automation command or AI chat.
     * Returns CommandType for automation or AI_CHAT for conversation.
     */
    suspend fun classifyCommand(input: String): CommandType {
        val lower = input.lowercase().trim()

        // Check local keyword matches first (fast, no API call)
        Constants.CMD_OPEN_APP.forEach { kw ->
            if (lower.contains(kw)) return CommandType.OPEN_APP
        }
        Constants.CMD_SCREENSHOT.forEach { kw ->
            if (lower.contains(kw)) return CommandType.TAKE_SCREENSHOT
        }
        Constants.CMD_BRIGHTNESS.forEach { kw ->
            if (lower.contains(kw)) return CommandType.SET_BRIGHTNESS
        }
        Constants.CMD_VOLUME.forEach { kw ->
            if (lower.contains(kw)) return CommandType.SET_VOLUME
        }
        Constants.CMD_WIFI.forEach { kw ->
            if (lower.contains(kw)) return CommandType.TOGGLE_WIFI
        }
        Constants.CMD_BLUETOOTH.forEach { kw ->
            if (lower.contains(kw)) return CommandType.TOGGLE_BLUETOOTH
        }
        Constants.CMD_FLASHLIGHT.forEach { kw ->
            if (lower.contains(kw)) return CommandType.TOGGLE_FLASHLIGHT
        }
        Constants.CMD_DND.forEach { kw ->
            if (lower.contains(kw)) return CommandType.TOGGLE_DND
        }
        Constants.CMD_ALARM.forEach { kw ->
            if (lower.contains(kw)) return CommandType.SET_ALARM
        }
        Constants.CMD_REPLY.forEach { kw ->
            if (lower.startsWith(kw)) return CommandType.SEND_MESSAGE
        }
        Constants.CMD_BACK.forEach { kw ->
            if (lower.contains(kw)) return CommandType.NAVIGATE_BACK
        }
        Constants.CMD_HOME.forEach { kw ->
            if (lower.contains(kw)) return CommandType.NAVIGATE_HOME
        }
        Constants.CMD_SCROLL.forEach { kw ->
            if (lower.contains(kw)) return CommandType.SCROLL
        }
        Constants.CMD_NOTIFICATIONS.forEach { kw ->
            if (lower.contains(kw)) return CommandType.SUMMARIZE_NOTIFICATIONS
        }
        if (lower.contains("screen") || lower.contains("kya dikh")) return CommandType.READ_SCREEN

        // Default: treat as AI conversation
        return CommandType.AI_CHAT
    }

    // ═══════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════

    private suspend fun buildMessageList(
        userMessage: String,
        extraContext: String
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        // System prompt (personality + context)
        val systemPrompt = buildSystemPrompt(extraContext)
        messages.add(ChatMessage("system", systemPrompt))

        // Recent conversation history
        val history = memoryEngine.getAIHistory(Constants.MAX_HISTORY_MESSAGES)
        history.reversed().forEach { msg ->
            if (msg.role in listOf("user", "assistant")) {
                messages.add(ChatMessage(msg.role, msg.content))
            }
        }

        // Current user message
        messages.add(ChatMessage("user", userMessage))

        return messages
    }

    private suspend fun buildSystemPrompt(extraContext: String): String {
        val personality = AIPersonality.getPrompt(currentPersonality)
        val deviceContext = memoryEngine.buildContextForAI()

        return buildString {
            appendLine(personality)
            appendLine()
            appendLine("=== Assistant Rules ===")
            appendLine("- You are Vivo, an AI assistant embedded in Android.")
            appendLine("- Respond concisely — this is a mobile assistant.")
            appendLine("- Support both Hindi and English. Reply in the same language the user uses.")
            appendLine("- For automation tasks, confirm the action briefly.")
            appendLine("- For questions, give direct helpful answers.")
            appendLine("- Keep responses under 150 words unless asked for detail.")
            appendLine()

            if (deviceContext.isNotBlank()) {
                appendLine(deviceContext)
                appendLine()
            }

            if (extraContext.isNotBlank()) {
                appendLine("=== Additional Context ===")
                appendLine(extraContext)
            }
        }.trim()
    }

    private fun getTemperatureForPersonality(personality: String): Float = when (personality) {
        Constants.PERSONALITY_JARVIS   -> 0.7f
        Constants.PERSONALITY_FRIENDLY -> 0.85f
        Constants.PERSONALITY_CALM     -> 0.6f
        Constants.PERSONALITY_MINIMAL  -> 0.4f
        Constants.PERSONALITY_PRO      -> 0.5f
        else                           -> 0.7f
    }

    fun destroy() {
        httpClient.dispatcher.cancelAll()
        httpClient.connectionPool.evictAll()
    }

    companion object {
        private const val TAG = "CloudAIManager"

        @Volatile
        private var INSTANCE: CloudAIManager? = null

        fun getInstance(context: Context): CloudAIManager {
            return INSTANCE ?: synchronized(this) {
                CloudAIManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// AI RESULT SEALED CLASS
// ═══════════════════════════════════════════════════════
sealed class AIResult {
    data class Success(
        val text: String,
        val tokensUsed: Int = 0,
        val model: String = ""
    ) : AIResult()

    data class Error(val message: String) : AIResult()
}
