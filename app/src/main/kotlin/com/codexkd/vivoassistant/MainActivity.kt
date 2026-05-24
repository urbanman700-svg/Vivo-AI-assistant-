package com.codexkd.vivoassistant

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.codexkd.vivoassistant.ai.AIResult
import com.codexkd.vivoassistant.ai.CloudAIManager
import com.codexkd.vivoassistant.automation.AutomationEngine
import com.codexkd.vivoassistant.databinding.ActivityMainBinding
import com.codexkd.vivoassistant.models.CommandType
import com.codexkd.vivoassistant.overlay.OverlayService
import com.codexkd.vivoassistant.routines.RoutineManager
import com.codexkd.vivoassistant.services.AssistantForegroundService
import com.codexkd.vivoassistant.ui.PermissionSetupDialog
import com.codexkd.vivoassistant.utils.Constants
import com.codexkd.vivoassistant.utils.NetworkManager
import com.codexkd.vivoassistant.utils.PermissionManager
import com.codexkd.vivoassistant.voice.TTSManager
import com.codexkd.vivoassistant.voice.VoiceEngine
import kotlinx.coroutines.*

/**
 * MainActivity — The heart of Vivo Assistant's UI.
 *
 * Hosts:
 * - Bottom Navigation (Dashboard, Chat, Routines, Settings)
 * - Voice session management
 * - Service binding
 * - Permission flow orchestration
 * - Network state monitoring
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    // Core components
    private lateinit var cloudAI: CloudAIManager
    private lateinit var voiceEngine: VoiceEngine
    private lateinit var ttsManager: TTSManager
    private lateinit var automationEngine: AutomationEngine
    private lateinit var networkManager: NetworkManager

    // Service binding
    private var assistantService: AssistantForegroundService? = null
    private var isServiceBound = false

    // Session state
    private var isVoiceSessionActive = false

    // ═══════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initComponents()
        setupNavigation()
        setupFab()
        bindForegroundService()
        observeNetworkState()
        registerNotificationReceiver()

        // Check if we need to show permissions
        if (intent.getBooleanExtra("show_permissions", false)) {
            showPermissionDialog()
        }

        Log.d(TAG, "MainActivity created ✓")
    }

    override fun onStart() {
        super.onStart()
        bindForegroundService()
    }

    override fun onStop() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        voiceEngine.destroy()
        ttsManager.destroy()
        try { unregisterReceiver(notificationReceiver) } catch (e: Exception) {}
        super.onDestroy()
    }

    // ═══════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════

    private fun initComponents() {
        cloudAI          = CloudAIManager.getInstance(this)
        automationEngine = AutomationEngine.getInstance(this)
        networkManager   = NetworkManager.getInstance(this)

        ttsManager = TTSManager(this).also { it.initialize() }
        voiceEngine = VoiceEngine(this, voiceListener).also { it.initialize() }
    }

    // ═══════════════════════════════════════════════
    // NAVIGATION
    // ═══════════════════════════════════════════════

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        // Hide bottom nav on certain destinations if needed
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.settingsActivity -> binding.bottomNav.visibility = View.GONE
                else                  -> binding.bottomNav.visibility = View.VISIBLE
            }
        }
    }

    // ═══════════════════════════════════════════════
    // FAB — Voice / Overlay toggle
    // ═══════════════════════════════════════════════

    private fun setupFab() {
        binding.fabVoice.setOnClickListener {
            if (isVoiceSessionActive) {
                stopVoiceSession()
            } else {
                startVoiceSession()
            }
        }

        binding.fabVoice.setOnLongClickListener {
            toggleOverlay()
            true
        }
    }

    // ═══════════════════════════════════════════════
    // VOICE SESSION
    // ═══════════════════════════════════════════════

    private fun startVoiceSession() {
        if (!PermissionManager.hasMicrophonePermission(this)) {
            PermissionManager.requestMicrophone(this)
            return
        }

        if (!networkManager.isNetworkAvailable() && !cloudAI.isConfigured()) {
            showToast("AI offline. Automation commands still available.")
        }

        isVoiceSessionActive = true
        assistantService?.startSession()

        binding.fabVoice.setImageResource(R.drawable.ic_mic_active)
        binding.fabVoice.backgroundTintList = ContextCompat.getColorStateList(
            this, R.color.color_voice_active
        )

        voiceEngine.startListening()
        ttsManager.speak("Listening")

        Log.d(TAG, "Voice session started")
    }

    private fun stopVoiceSession() {
        isVoiceSessionActive = false
        assistantService?.endSession()

        voiceEngine.stopListening()
        ttsManager.stop()

        binding.fabVoice.setImageResource(R.drawable.ic_mic)
        binding.fabVoice.backgroundTintList = ContextCompat.getColorStateList(
            this, R.color.color_primary
        )

        Log.d(TAG, "Voice session stopped")
    }

    // ═══════════════════════════════════════════════
    // COMMAND PROCESSING
    // ═══════════════════════════════════════════════

    private fun processCommand(text: String) {
        Log.d(TAG, "Processing: $text")
        showToast("\"$text\"")

        lifecycleScope.launch {
            try {
                val commandType = cloudAI.classifyCommand(text)

                if (commandType != CommandType.AI_CHAT) {
                    // Execute automation
                    val command = automationEngine.parseCommand(text)
                    val confirmation = automationEngine.execute(command)
                    if (confirmation.isNotBlank()) {
                        ttsManager.speak(confirmation)
                        showToast(confirmation)
                    }
                } else {
                    // AI Chat — navigate to chat fragment with pre-filled text
                    val bundle = Bundle().apply { putString("prefill_text", text) }
                    navController.navigate(R.id.chatFragment, bundle)

                    // Also process through AI
                    when (val result = cloudAI.chat(text)) {
                        is AIResult.Success -> ttsManager.speak(result.text)
                        is AIResult.Error   -> ttsManager.speak(result.message)
                    }
                }

                // Check for routine match
                val routineManager = RoutineManager.getInstance(this@MainActivity)
                val matchedRoutine = routineManager.matchVoiceCommand(text)
                if (matchedRoutine != null) {
                    val result = routineManager.executeRoutine(matchedRoutine.id)
                    ttsManager.speak(result)
                }

            } catch (e: Exception) {
                Log.e(TAG, "processCommand error: ${e.message}")
                ttsManager.speak("Something went wrong. Try again.")
            }
        }
    }

    // ═══════════════════════════════════════════════
    // VOICE LISTENER
    // ═══════════════════════════════════════════════

    private val voiceListener = object : VoiceEngine.VoiceListener {
        override fun onListeningStarted() {
            binding.tvVoiceStatus.visibility = View.VISIBLE
            binding.tvVoiceStatus.text = "Listening..."
        }

        override fun onPartialResult(text: String) {
            binding.tvVoiceStatus.text = text
        }

        override fun onResult(text: String) {
            binding.tvVoiceStatus.visibility = View.GONE
            processCommand(text)

            // Restart listening if session is still active (continuous mode)
            if (isVoiceSessionActive) {
                voiceEngine.startListening()
            }
        }

        override fun onError(error: String) {
            binding.tvVoiceStatus.visibility = View.GONE
            if (isVoiceSessionActive) {
                // Auto-restart on recoverable errors
                voiceEngine.startListening()
            } else {
                stopVoiceSession()
            }
        }

        override fun onListeningStopped() {
            binding.tvVoiceStatus.visibility = View.GONE
        }

        override fun onVolumeChanged(rmsdB: Float) {
            // Animate FAB scale based on voice volume
            val scale = 1f + (rmsdB / 20f).coerceIn(0f, 0.3f)
            binding.fabVoice.scaleX = scale
            binding.fabVoice.scaleY = scale
        }
    }

    // ═══════════════════════════════════════════════
    // OVERLAY
    // ═══════════════════════════════════════════════

    private fun toggleOverlay() {
        if (!PermissionManager.hasOverlayPermission(this)) {
            showToast("Overlay permission needed")
            PermissionManager.openOverlaySettings(this)
            return
        }

        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_TOGGLE
        }
        startService(intent)
    }

    // ═══════════════════════════════════════════════
    // SERVICE BINDING
    // ═══════════════════════════════════════════════

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? AssistantForegroundService.AssistantBinder
            assistantService = localBinder?.getService()
            isServiceBound = true
            Log.d(TAG, "Service bound ✓")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            assistantService = null
            isServiceBound = false
        }
    }

    private fun bindForegroundService() {
        // Start service first
        AssistantForegroundService.start(this)

        // Then bind
        val intent = Intent(this, AssistantForegroundService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    // ═══════════════════════════════════════════════
    // NETWORK STATE
    // ═══════════════════════════════════════════════

    private fun observeNetworkState() {
        lifecycleScope.launch {
            networkManager.isOnline.collect { online ->
                binding.tvNetworkStatus.visibility = if (!online) View.VISIBLE else View.GONE
                binding.tvNetworkStatus.text = "Offline — AI chat unavailable"
            }
        }
    }

    // ═══════════════════════════════════════════════
    // NOTIFICATION RECEIVER
    // ═══════════════════════════════════════════════

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // New notification arrived — update badge or indicator
            val pkg = intent?.getStringExtra("pkg") ?: return
            Log.d(TAG, "New notification from: $pkg")
        }
    }

    private fun registerNotificationReceiver() {
        val filter = IntentFilter(Constants.ACTION_NOTIF_UPDATE)
        ContextCompat.registerReceiver(
            this, notificationReceiver, filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    // ═══════════════════════════════════════════════
    // PERMISSIONS
    // ═══════════════════════════════════════════════

    private fun showPermissionDialog() {
        PermissionSetupDialog(this) { allGranted ->
            if (allGranted) {
                ttsManager.speak("Vivo Assistant is ready. Say Vivo to start.")
            }
        }.show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PermissionManager.RC_AUDIO -> {
                if (grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    voiceEngine.initialize()
                } else {
                    showToast("Microphone permission needed for voice commands")
                }
            }
        }
    }

    // ═══════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
