package com.codexkd.vivoassistant.ui

import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codexkd.vivoassistant.R
import com.codexkd.vivoassistant.ai.AIPersonality
import com.codexkd.vivoassistant.ai.AIResult
import com.codexkd.vivoassistant.ai.CloudAIManager
import com.codexkd.vivoassistant.automation.AutomationEngine
import com.codexkd.vivoassistant.databinding.FragmentChatBinding
import com.codexkd.vivoassistant.memory.MemoryEngine
import com.codexkd.vivoassistant.models.CommandType
import com.codexkd.vivoassistant.models.Message
import com.codexkd.vivoassistant.utils.Constants
import com.codexkd.vivoassistant.utils.NetworkManager
import com.codexkd.vivoassistant.voice.TTSManager
import com.codexkd.vivoassistant.voice.VoiceEngine
import kotlinx.coroutines.*

/**
 * ChatFragment — The core AI conversation interface.
 *
 * Features:
 * - Full AI chat with streaming-style typing indicator
 * - Voice input with waveform
 * - Personality switcher
 * - Markdown rendered responses
 * - Session continuity
 * - Speak-message button
 * - Clear chat with confirmation
 * - Offline graceful handling
 * - Automation command detection
 */
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var cloudAI: CloudAIManager
    private lateinit var memoryEngine: MemoryEngine
    private lateinit var automationEngine: AutomationEngine
    private lateinit var networkManager: NetworkManager
    private lateinit var ttsManager: TTSManager
    private lateinit var voiceEngine: VoiceEngine
    private lateinit var chatAdapter: ChatAdapter

    private var currentPersonality = Constants.PERSONALITY_JARVIS
    private var isProcessing = false

    // ═══════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cloudAI          = CloudAIManager.getInstance(requireContext())
        memoryEngine     = MemoryEngine.getInstance(requireContext())
        automationEngine = AutomationEngine.getInstance(requireContext())
        networkManager   = NetworkManager.getInstance(requireContext())
        ttsManager       = TTSManager(requireContext()).also { it.initialize() }
        voiceEngine      = VoiceEngine(requireContext(), voiceListener).also { it.initialize() }

        setupRecyclerView()
        setupInputBar()
        setupPersonalitySpinner()
        setupTopBar()
        loadChatHistory()

        // Handle pre-filled text from voice command / navigation
        arguments?.getString("prefill_text")?.let { prefill ->
            if (prefill.isNotBlank()) {
                binding.etMessage.setText(prefill)
                binding.etMessage.setSelection(prefill.length)
            }
        }
    }

    override fun onDestroyView() {
        voiceEngine.destroy()
        ttsManager.destroy()
        _binding = null
        super.onDestroyView()
    }

    // ═══════════════════════════════════════════════
    // RECYCLER VIEW
    // ═══════════════════════════════════════════════

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(requireContext()) { message ->
            // Speak button clicked
            ttsManager.speak(message.content)
        }

        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
            itemAnimator = null  // Disable default animation (custom ones in adapter)
        }
    }

    private fun loadChatHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            val history = memoryEngine.getAIHistory(Constants.MAX_HISTORY_MESSAGES)
            val filtered = history.filter { it.role != "system" }
            withContext(Dispatchers.Main) {
                chatAdapter.submitList(filtered)
                scrollToBottom()
            }
        }
    }

    // ═══════════════════════════════════════════════
    // INPUT BAR
    // ═══════════════════════════════════════════════

    private fun setupInputBar() {
        // Send on keyboard "Done" / "Send" action
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                actionId == EditorInfo.IME_ACTION_DONE) {
                sendMessage()
                true
            } else false
        }

        binding.btnSend.setOnClickListener { sendMessage() }

        binding.btnVoiceInput.setOnClickListener {
            if (voiceEngine.isCurrentlyListening()) {
                voiceEngine.stopListening()
                setVoiceBtnState(false)
            } else {
                startVoiceInput()
            }
        }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text?.toString()?.trim() ?: return
        if (text.isBlank() || isProcessing) return

        binding.etMessage.setText("")
        processInput(text, isVoice = false)
    }

    // ═══════════════════════════════════════════════
    // MESSAGE PROCESSING
    // ═══════════════════════════════════════════════

    private fun processInput(text: String, isVoice: Boolean = false) {
        if (isProcessing) return
        isProcessing = true

        // Show user message immediately
        val userMsg = Message.user(text, isVoice = isVoice)
        appendMessage(userMsg)

        // Show typing indicator
        chatAdapter.showTyping()
        scrollToBottom()
        setInputEnabled(false)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val commandType = cloudAI.classifyCommand(text)

                val responseText = if (commandType != CommandType.AI_CHAT) {
                    // Automation command
                    val command = automationEngine.parseCommand(text)
                    automationEngine.execute(command)
                } else {
                    // Check network first
                    if (!networkManager.isNetworkAvailable()) {
                        getString(R.string.chat_error_offline)
                    } else if (!cloudAI.isConfigured()) {
                        getString(R.string.chat_error_no_api_key)
                    } else {
                        when (val result = cloudAI.chat(text)) {
                            is AIResult.Success -> {
                                updateTokenUsage(result.tokensUsed)
                                result.text
                            }
                            is AIResult.Error -> result.message
                        }
                    }
                }

                // Show assistant response
                chatAdapter.hideTyping()
                val assistantMsg = Message.assistant(responseText)
                appendMessage(assistantMsg)

                // Auto-speak response
                ttsManager.speak(responseText)

            } catch (e: Exception) {
                chatAdapter.hideTyping()
                appendMessage(Message.error("Error: ${e.message}"))
            } finally {
                isProcessing = false
                setInputEnabled(true)
                scrollToBottom()
            }
        }
    }

    private fun appendMessage(message: Message) {
        val current = chatAdapter.currentList.toMutableList()
        current.add(message)
        chatAdapter.submitList(current)
        scrollToBottom()

        // Persist to DB
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            memoryEngine.saveMessage(message)
        }
    }

    private fun scrollToBottom() {
        val count = chatAdapter.itemCount
        if (count > 0) {
            binding.rvChat.post {
                binding.rvChat.smoothScrollToPosition(count - 1)
            }
        }
    }

    // ═══════════════════════════════════════════════
    // TOP BAR
    // ═══════════════════════════════════════════════

    private fun setupTopBar() {
        binding.tvModelName.text = "Vivo AI"
        binding.btnClearChat.setOnClickListener { confirmClearChat() }
    }

    private fun confirmClearChat() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.clear_chat))
            .setMessage(getString(R.string.clear_chat_confirm))
            .setPositiveButton("Clear") { _, _ -> clearChat() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearChat() {
        chatAdapter.submitList(emptyList())
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            memoryEngine.clearHistory()
        }
        cloudAI.endSession()
        cloudAI.startSession(currentPersonality)
    }

    // ═══════════════════════════════════════════════
    // PERSONALITY SPINNER
    // ═══════════════════════════════════════════════

    private fun setupPersonalitySpinner() {
        val personalities = listOf(
            Constants.PERSONALITY_JARVIS,
            Constants.PERSONALITY_FRIENDLY,
            Constants.PERSONALITY_CALM,
            Constants.PERSONALITY_MINIMAL,
            Constants.PERSONALITY_PRO
        )
        val displayNames = personalities.map { AIPersonality.getDisplayName(it) }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            displayNames
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.spinnerPersonality.adapter = adapter
        binding.spinnerPersonality.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                currentPersonality = personalities[pos]
                cloudAI.endSession()
                cloudAI.startSession(currentPersonality)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    // ═══════════════════════════════════════════════
    // VOICE INPUT
    // ═══════════════════════════════════════════════

    private fun startVoiceInput() {
        voiceEngine.startListening()
        setVoiceBtnState(true)
    }

    private val voiceListener = object : VoiceEngine.VoiceListener {
        override fun onListeningStarted() {
            binding.etMessage.hint = getString(R.string.voice_listening)
        }

        override fun onPartialResult(text: String) {
            binding.etMessage.setText(text)
            binding.etMessage.setSelection(text.length)
        }

        override fun onResult(text: String) {
            setVoiceBtnState(false)
            binding.etMessage.setText("")
            binding.etMessage.hint = getString(R.string.chat_hint)
            processInput(text, isVoice = true)
        }

        override fun onError(error: String) {
            setVoiceBtnState(false)
            binding.etMessage.hint = getString(R.string.chat_hint)
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }

        override fun onListeningStopped() {
            setVoiceBtnState(false)
            binding.etMessage.hint = getString(R.string.chat_hint)
        }

        override fun onVolumeChanged(rmsdB: Float) {
            val scale = 1f + (rmsdB / 25f).coerceIn(0f, 0.25f)
            binding.btnVoiceInput.scaleX = scale
            binding.btnVoiceInput.scaleY = scale
        }
    }

    private fun setVoiceBtnState(listening: Boolean) {
        binding.btnVoiceInput.setImageResource(
            if (listening) R.drawable.ic_mic_active else R.drawable.ic_mic
        )
        binding.btnVoiceInput.scaleX = 1f
        binding.btnVoiceInput.scaleY = 1f
    }

    // ═══════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════

    private fun setInputEnabled(enabled: Boolean) {
        binding.etMessage.isEnabled = enabled
        binding.btnSend.isEnabled = enabled
    }

    private fun updateTokenUsage(tokens: Int) {
        if (tokens > 0) {
            binding.tvTokenUsage.visibility = View.VISIBLE
            binding.tvTokenUsage.text = getString(R.string.token_usage, tokens)
        }
    }
}
