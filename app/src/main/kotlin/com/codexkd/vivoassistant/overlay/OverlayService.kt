package com.codexkd.vivoassistant.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.cardview.widget.CardView
import com.codexkd.vivoassistant.R
import com.codexkd.vivoassistant.ai.AIResult
import com.codexkd.vivoassistant.ai.CloudAIManager
import com.codexkd.vivoassistant.automation.AutomationEngine
import com.codexkd.vivoassistant.models.CommandType
import com.codexkd.vivoassistant.utils.Constants
import com.codexkd.vivoassistant.voice.TTSManager
import com.codexkd.vivoassistant.voice.VoiceEngine
import kotlinx.coroutines.*

/**
 * OverlayService — The floating AI orb that appears over all apps.
 *
 * Features:
 * - Floating AI orb (collapsed) → Expandable assistant panel
 * - Real-time voice input with waveform
 * - Quick AI chat panel
 * - Quick action buttons
 * - Drag to reposition
 * - Smooth animations (GPU-friendly)
 * - Auto-collapse on inactivity
 */
class OverlayService : Service() {

    // WindowManager for overlay rendering
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var isExpanded = false

    // AI + Voice components
    private lateinit var cloudAI: CloudAIManager
    private lateinit var ttsManager: TTSManager
    private lateinit var voiceEngine: VoiceEngine
    private lateinit var automationEngine: AutomationEngine

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Layout params for positioning
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // UI references (set after inflate)
    private var orbContainer: View? = null
    private var expandedPanel: CardView? = null
    private var orbPulse: View? = null
    private var waveformBar: LinearLayout? = null
    private var statusText: TextView? = null
    private var chatInput: EditText? = null
    private var chatOutput: TextView? = null
    private var sendBtn: ImageButton? = null
    private var micBtn: ImageButton? = null
    private var closeBtn: ImageButton? = null

    // Auto-collapse timer
    private var autoCollapseJob: Job? = null

    // ═══════════════════════════════════════════════
    // SERVICE LIFECYCLE
    // ═══════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        cloudAI = CloudAIManager.getInstance(this)
        ttsManager = TTSManager(this).also { it.initialize() }
        automationEngine = AutomationEngine.getInstance(this)
        voiceEngine = VoiceEngine(this, voiceListener).also { it.initialize() }

        Log.d(TAG, "OverlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
            ACTION_TOGGLE -> if (overlayView != null) hideOverlay() else showOverlay()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        hideOverlay()
        voiceEngine.destroy()
        ttsManager.destroy()
        Log.d(TAG, "OverlayService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ═══════════════════════════════════════════════
    // OVERLAY DISPLAY
    // ═══════════════════════════════════════════════

    private fun showOverlay() {
        if (overlayView != null) return

        if (!android.provider.Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission not granted")
            return
        }

        inflateOverlay()
        setupTouchDrag()
        setupClickListeners()

        try {
            windowManager.addView(overlayView, layoutParams)
            animateOrbEntry()
            Log.d(TAG, "Overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "addView error: ${e.message}")
        }
    }

    private fun hideOverlay() {
        autoCollapseJob?.cancel()
        overlayView?.let { view ->
            animateOrbExit {
                try {
                    windowManager.removeView(view)
                } catch (e: Exception) {
                    Log.e(TAG, "removeView error: ${e.message}")
                }
                overlayView = null
                isExpanded = false
            }
        }
    }

    private fun inflateOverlay() {
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_assistant, null)

        // Find views
        orbContainer   = overlayView?.findViewById(R.id.orbContainer)
        expandedPanel  = overlayView?.findViewById(R.id.expandedPanel)
        orbPulse       = overlayView?.findViewById(R.id.orbPulse)
        waveformBar    = overlayView?.findViewById(R.id.waveformBar)
        statusText     = overlayView?.findViewById(R.id.tvStatus)
        chatInput      = overlayView?.findViewById(R.id.etChatInput)
        chatOutput     = overlayView?.findViewById(R.id.tvChatOutput)
        sendBtn        = overlayView?.findViewById(R.id.btnSend)
        micBtn         = overlayView?.findViewById(R.id.btnMic)
        closeBtn       = overlayView?.findViewById(R.id.btnClose)

        // Initial state — collapsed orb
        expandedPanel?.visibility = View.GONE

        // Layout params
        val dp = resources.displayMetrics.density
        val orbSize = (Constants.ORB_SIZE_COLLAPSED * dp).toInt()

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (16 * dp).toInt()
            y = (120 * dp).toInt()
        }
    }

    // ═══════════════════════════════════════════════
    // DRAG BEHAVIOR
    // ═══════════════════════════════════════════════

    private fun setupTouchDrag() {
        var isDragging = false
        var dragStartTime = 0L

        orbContainer?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragStartTime = System.currentTimeMillis()
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        layoutParams.x = initialX - dx
                        layoutParams.y = initialY + dy
                        windowManager.updateViewLayout(overlayView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Short tap — toggle expand
                        toggleExpand()
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ═══════════════════════════════════════════════
    // CLICK LISTENERS
    // ═══════════════════════════════════════════════

    private fun setupClickListeners() {
        closeBtn?.setOnClickListener {
            collapsePanel()
        }

        sendBtn?.setOnClickListener {
            val text = chatInput?.text?.toString()?.trim() ?: return@setOnClickListener
            if (text.isNotEmpty()) {
                chatInput?.setText("")
                processInput(text)
            }
        }

        micBtn?.setOnClickListener {
            if (voiceEngine.isCurrentlyListening()) {
                voiceEngine.stopListening()
            } else {
                startVoiceInput()
            }
        }

        // Make expanded panel focusable when visible
        expandedPanel?.setOnClickListener { /* consume */ }
    }

    // ═══════════════════════════════════════════════
    // EXPAND / COLLAPSE
    // ═══════════════════════════════════════════════

    private fun toggleExpand() {
        if (isExpanded) collapsePanel() else expandPanel()
    }

    private fun expandPanel() {
        isExpanded = true

        // Make overlay focusable so keyboard works
        layoutParams.flags = layoutParams.flags and
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        windowManager.updateViewLayout(overlayView, layoutParams)

        expandedPanel?.visibility = View.VISIBLE
        expandedPanel?.alpha = 0f
        expandedPanel?.scaleX = 0.8f
        expandedPanel?.scaleY = 0.8f

        expandedPanel?.animate()
            ?.alpha(1f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setDuration(Constants.ORB_ANIMATION_MS)
            ?.setInterpolator(OvershootInterpolator(1.2f))
            ?.start()

        // Start auto-collapse timer
        scheduleAutoCollapse()
    }

    private fun collapsePanel() {
        if (!isExpanded) return
        isExpanded = false
        autoCollapseJob?.cancel()

        // Restore non-focusable flag
        layoutParams.flags = layoutParams.flags or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        windowManager.updateViewLayout(overlayView, layoutParams)

        expandedPanel?.animate()
            ?.alpha(0f)
            ?.scaleX(0.8f)
            ?.scaleY(0.8f)
            ?.setDuration(Constants.ORB_ANIMATION_MS)
            ?.withEndAction { expandedPanel?.visibility = View.GONE }
            ?.start()
    }

    private fun scheduleAutoCollapse() {
        autoCollapseJob?.cancel()
        autoCollapseJob = scope.launch {
            delay(15_000L) // Auto-collapse after 15s inactivity
            if (isExpanded) collapsePanel()
        }
    }

    // ═══════════════════════════════════════════════
    // INPUT PROCESSING
    // ═══════════════════════════════════════════════

    private fun processInput(text: String) {
        setStatus("Thinking...")
        showOrbPulse(true)

        // Reset auto-collapse timer
        scheduleAutoCollapse()

        scope.launch {
            try {
                // Classify the command
                val commandType = cloudAI.classifyCommand(text)

                if (commandType != CommandType.AI_CHAT) {
                    // Execute automation command
                    val command = automationEngine.parseCommand(text)
                    val confirmation = automationEngine.execute(command)
                    showResponse(confirmation)
                    ttsManager.speak(confirmation)
                } else {
                    // Cloud AI conversation
                    when (val result = cloudAI.chat(text)) {
                        is AIResult.Success -> {
                            showResponse(result.text)
                            ttsManager.speak(result.text)
                        }
                        is AIResult.Error -> {
                            showResponse("Error: ${result.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                showResponse("Error: ${e.message}")
            } finally {
                showOrbPulse(false)
                setStatus("Ask me anything...")
            }
        }
    }

    // ═══════════════════════════════════════════════
    // VOICE INPUT
    // ═══════════════════════════════════════════════

    private fun startVoiceInput() {
        voiceEngine.startListening()
        setStatus("Listening...")
        animateWaveform(true)
    }

    private val voiceListener = object : VoiceEngine.VoiceListener {
        override fun onListeningStarted() {
            micBtn?.setImageResource(R.drawable.ic_mic_active)
        }

        override fun onPartialResult(text: String) {
            chatInput?.setText(text)
        }

        override fun onResult(text: String) {
            animateWaveform(false)
            micBtn?.setImageResource(R.drawable.ic_mic)
            chatInput?.setText("")
            processInput(text)
        }

        override fun onError(error: String) {
            animateWaveform(false)
            micBtn?.setImageResource(R.drawable.ic_mic)
            setStatus(error)
        }

        override fun onListeningStopped() {
            animateWaveform(false)
            micBtn?.setImageResource(R.drawable.ic_mic)
        }

        override fun onVolumeChanged(rmsdB: Float) {
            animateWaveformVolume(rmsdB)
        }
    }

    // ═══════════════════════════════════════════════
    // UI HELPERS
    // ═══════════════════════════════════════════════

    private fun setStatus(text: String) {
        statusText?.text = text
    }

    private fun showResponse(text: String) {
        chatOutput?.text = text
        chatOutput?.visibility = View.VISIBLE
    }

    private fun showOrbPulse(active: Boolean) {
        if (active) {
            startOrbPulseAnimation()
        } else {
            orbPulse?.animate()?.alpha(0f)?.setDuration(300)?.start()
        }
    }

    private fun startOrbPulseAnimation() {
        orbPulse?.animate()?.cancel()
        orbPulse?.alpha = 0.6f
        orbPulse?.animate()
            ?.alpha(0f)
            ?.setDuration(800)
            ?.setStartDelay(0)
            ?.withEndAction { if (isExpanded) startOrbPulseAnimation() }
            ?.start()
    }

    private fun animateWaveform(active: Boolean) {
        waveformBar?.visibility = if (active) View.VISIBLE else View.GONE
    }

    private fun animateWaveformVolume(rmsdB: Float) {
        if (waveformBar?.visibility != View.VISIBLE) return
        val scale = (rmsdB / 10f).coerceIn(0.2f, 1.5f)
        waveformBar?.scaleY = scale
    }

    private fun animateOrbEntry() {
        orbContainer?.scaleX = 0f
        orbContainer?.scaleY = 0f
        orbContainer?.alpha = 0f

        orbContainer?.animate()
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.alpha(1f)
            ?.setDuration(400)
            ?.setInterpolator(OvershootInterpolator(2f))
            ?.start()
    }

    private fun animateOrbExit(onEnd: () -> Unit) {
        orbContainer?.animate()
            ?.scaleX(0f)
            ?.scaleY(0f)
            ?.alpha(0f)
            ?.setDuration(250)
            ?.withEndAction(onEnd)
            ?.start() ?: onEnd()
    }

    companion object {
        private const val TAG = "OverlayService"

        const val ACTION_SHOW   = "OVERLAY_SHOW"
        const val ACTION_HIDE   = "OVERLAY_HIDE"
        const val ACTION_TOGGLE = "OVERLAY_TOGGLE"
    }
}
