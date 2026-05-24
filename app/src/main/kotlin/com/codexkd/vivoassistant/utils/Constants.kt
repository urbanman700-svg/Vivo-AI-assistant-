package com.codexkd.vivoassistant.utils

/**
 * Vivo Assistant — Global Constants
 * Codex KD Official
 *
 * All app-wide constants, intent actions, preference keys,
 * notification channels, and configuration values.
 */
object Constants {

    // ═══════════════════════════════════════════════
    // APP INFO
    // ═══════════════════════════════════════════════
    const val APP_NAME              = "Vivo Assistant"
    const val APP_PACKAGE           = "com.codexkd.vivoassistant"
    const val CODEX_KD_TAG          = "VivoAssistant"

    // ═══════════════════════════════════════════════
    // AI API CONFIGURATION
    // ═══════════════════════════════════════════════
    // OpenRouter supports Claude, GPT-4, Gemini, etc.
    // Get your free key at: https://openrouter.ai
    const val AI_BASE_URL           = "https://openrouter.ai/api/v1/chat/completions"

    // Fallback to direct OpenAI if needed
    const val OPENAI_BASE_URL       = "https://api.openai.com/v1/chat/completions"

    // Default model — fast, cheap, multilingual
    const val DEFAULT_AI_MODEL      = "anthropic/claude-haiku-4-5"

    // Alternative models
    const val MODEL_GPT_MINI        = "openai/gpt-4o-mini"
    const val MODEL_GEMINI_FLASH    = "google/gemini-flash-1.5"
    const val MODEL_CLAUDE_SONNET   = "anthropic/claude-sonnet-4-5"

    // API timeouts (seconds)
    const val API_CONNECT_TIMEOUT   = 10L
    const val API_READ_TIMEOUT      = 30L
    const val API_WRITE_TIMEOUT     = 15L

    // Max tokens per response
    const val MAX_TOKENS            = 800

    // Max conversation history to send (prevents huge payloads)
    const val MAX_HISTORY_MESSAGES  = 12

    // ═══════════════════════════════════════════════
    // DATASTORE PREFERENCE KEYS
    // ═══════════════════════════════════════════════
    const val PREF_API_KEY          = "ai_api_key"
    const val PREF_AI_MODEL         = "ai_model"
    const val PREF_AI_PERSONALITY   = "ai_personality"
    const val PREF_VOICE_SPEED      = "voice_speed"
    const val PREF_VOICE_PITCH      = "voice_pitch"
    const val PREF_VOICE_LANGUAGE   = "voice_language"
    const val PREF_OVERLAY_ENABLED  = "overlay_enabled"
    const val PREF_OVERLAY_SIDE     = "overlay_side"   // left / right
    const val PREF_THEME_MODE       = "theme_mode"
    const val PREF_HAPTIC_ENABLED   = "haptic_enabled"
    const val PREF_SOUND_ENABLED    = "sound_enabled"
    const val PREF_NOTIF_AI_ENABLED = "notif_ai_enabled"
    const val PREF_BOOT_START       = "boot_autostart"
    const val PREF_FIRST_LAUNCH     = "first_launch"
    const val PREF_SETUP_DONE       = "setup_done"
    const val PREF_UI_MODE          = "ui_mode"

    // ═══════════════════════════════════════════════
    // AI PERSONALITY PROFILES
    // ═══════════════════════════════════════════════
    const val PERSONALITY_JARVIS    = "jarvis"
    const val PERSONALITY_FRIENDLY  = "friendly"
    const val PERSONALITY_CALM      = "calm"
    const val PERSONALITY_MINIMAL   = "minimal"
    const val PERSONALITY_PRO       = "professional"

    // ═══════════════════════════════════════════════
    // UI MODES
    // ═══════════════════════════════════════════════
    const val MODE_NORMAL           = "normal"
    const val MODE_GAMING           = "gaming"
    const val MODE_STUDY            = "study"
    const val MODE_SLEEP            = "sleep"
    const val MODE_CINEMATIC        = "cinematic"

    // ═══════════════════════════════════════════════
    // NOTIFICATION CHANNELS
    // ═══════════════════════════════════════════════
    const val CHANNEL_ASSISTANT     = "vivo_assistant_channel"
    const val CHANNEL_ALERTS        = "vivo_alerts_channel"
    const val CHANNEL_ROUTINES      = "vivo_routines_channel"

    // Notification IDs
    const val NOTIF_ID_FOREGROUND   = 1001
    const val NOTIF_ID_ALERT        = 1002
    const val NOTIF_ID_ROUTINE      = 1003

    // ═══════════════════════════════════════════════
    // INTENT ACTIONS (Service <-> Activity comms)
    // ═══════════════════════════════════════════════
    const val ACTION_START_VOICE    = "$APP_PACKAGE.START_VOICE"
    const val ACTION_STOP_VOICE     = "$APP_PACKAGE.STOP_VOICE"
    const val ACTION_SHOW_OVERLAY   = "$APP_PACKAGE.SHOW_OVERLAY"
    const val ACTION_HIDE_OVERLAY   = "$APP_PACKAGE.HIDE_OVERLAY"
    const val ACTION_AI_COMMAND     = "$APP_PACKAGE.AI_COMMAND"
    const val ACTION_EXECUTE_CMD    = "$APP_PACKAGE.EXECUTE_CMD"
    const val ACTION_ROUTINE_EXEC   = "$APP_PACKAGE.ROUTINE_EXEC"
    const val ACTION_NOTIF_UPDATE   = "$APP_PACKAGE.NOTIF_UPDATE"
    const val ACTION_DISMISS_NOTIF  = "$APP_PACKAGE.DISMISS_NOTIF"

    // Broadcast extras
    const val EXTRA_COMMAND         = "command_text"
    const val EXTRA_RESPONSE        = "ai_response"
    const val EXTRA_ROUTINE_ID      = "routine_id"
    const val EXTRA_APP_PACKAGE     = "app_package"

    // ═══════════════════════════════════════════════
    // AUTOMATION COMMAND KEYWORDS
    // ═══════════════════════════════════════════════
    // Voice trigger keyword
    const val WAKE_WORD             = "vivo"

    // Command categories
    val CMD_OPEN_APP       = listOf("open", "launch", "start", "kholo", "chalao")
    val CMD_BRIGHTNESS     = listOf("brightness", "roshan", "ujala", "dim", "bright")
    val CMD_VOLUME         = listOf("volume", "awaz", "loud", "mute", "silent")
    val CMD_FLASHLIGHT     = listOf("flashlight", "torch", "light", "torch on", "torch off")
    val CMD_WIFI           = listOf("wifi", "wi-fi", "internet")
    val CMD_BLUETOOTH      = listOf("bluetooth", "bt")
    val CMD_DND            = listOf("dnd", "do not disturb", "silent mode", "disturb mat karo")
    val CMD_SCREENSHOT     = listOf("screenshot", "capture", "snap", "screen shot")
    val CMD_ALARM          = listOf("alarm", "reminder", "wake me", "set alarm")
    val CMD_REPLY          = listOf("reply", "send", "type", "message", "jawab do")
    val CMD_SCROLL         = listOf("scroll down", "scroll up", "neeche", "upar")
    val CMD_BACK           = listOf("go back", "back", "wapas", "peeche")
    val CMD_HOME           = listOf("go home", "home", "ghar")
    val CMD_NOTIFICATIONS  = listOf("notifications", "alerts", "notification check")

    // ═══════════════════════════════════════════════
    // SMART ROUTINE IDs
    // ═══════════════════════════════════════════════
    const val ROUTINE_GAMING        = "routine_gaming"
    const val ROUTINE_STUDY         = "routine_study"
    const val ROUTINE_SLEEP         = "routine_sleep"
    const val ROUTINE_MORNING       = "routine_morning"
    const val ROUTINE_WORK          = "routine_work"

    // ═══════════════════════════════════════════════
    // OVERLAY CONFIGURATION
    // ═══════════════════════════════════════════════
    const val ORB_SIZE_COLLAPSED    = 56    // dp — collapsed orb size
    const val ORB_SIZE_EXPANDED     = 300   // dp — expanded panel width
    const val ORB_ALPHA_IDLE        = 0.85f
    const val ORB_ALPHA_ACTIVE      = 1.0f
    const val ORB_ANIMATION_MS      = 300L

    // ═══════════════════════════════════════════════
    // VOICE ENGINE CONFIGURATION
    // ═══════════════════════════════════════════════
    const val VAD_SILENCE_TIMEOUT   = 2000L  // ms — stop after 2s silence
    const val MAX_LISTEN_DURATION   = 10000L // ms — max listen time
    const val TTS_DEFAULT_SPEED     = 1.0f
    const val TTS_DEFAULT_PITCH     = 1.0f

    // Supported voice languages
    const val LANG_ENGLISH          = "en-IN"  // Indian English
    const val LANG_HINDI            = "hi-IN"

    // ═══════════════════════════════════════════════
    // MEMORY ENGINE
    // ═══════════════════════════════════════════════
    const val MEMORY_DB_NAME        = "vivo_memory.db"
    const val MEMORY_DB_VERSION     = 1
    const val MAX_CHAT_HISTORY      = 500    // messages to keep in DB
    const val CONTEXT_INJECT_LIMIT  = 5      // recent memories to inject

    // ═══════════════════════════════════════════════
    // NETWORK
    // ═══════════════════════════════════════════════
    const val NETWORK_CHECK_URL     = "https://www.google.com"
    const val MIN_LATENCY_GOOD      = 200L   // ms
    const val MIN_LATENCY_POOR      = 1000L  // ms

    // ═══════════════════════════════════════════════
    // COMMON APPS (package names)
    // ═══════════════════════════════════════════════
    val APP_MAP = mapOf(
        "whatsapp"      to "com.whatsapp",
        "instagram"     to "com.instagram.android",
        "youtube"       to "com.google.android.youtube",
        "telegram"      to "org.telegram.messenger",
        "twitter"       to "com.twitter.android",
        "x"             to "com.twitter.android",
        "snapchat"      to "com.snapchat.android",
        "facebook"      to "com.facebook.katana",
        "spotify"       to "com.spotify.music",
        "chrome"        to "com.android.chrome",
        "gmail"         to "com.google.android.gm",
        "maps"          to "com.google.android.apps.maps",
        "camera"        to "android.media.action.IMAGE_CAPTURE",
        "calculator"    to "com.google.android.calculator",
        "settings"      to "com.android.settings",
        "phone"         to "com.android.dialer",
        "contacts"      to "com.android.contacts",
        "gallery"       to "com.android.gallery3d",
        "clock"         to "com.android.deskclock",
        "calendar"      to "com.google.android.calendar",
        "notes"         to "com.google.android.keep",
        "files"         to "com.google.android.documentsui",
        "play store"    to "com.android.vending",
        "netflix"       to "com.netflix.mediaclient",
        "amazon"        to "in.amazon.mShop.android.shopping",
        "paytm"         to "net.one97.paytm",
        "phonepe"       to "com.phonepe.app",
        "gpay"          to "com.google.android.apps.nbu.paisa.user"
    )
}
