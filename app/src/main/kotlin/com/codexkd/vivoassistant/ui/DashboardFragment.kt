package com.codexkd.vivoassistant.ui

import android.animation.ValueAnimator
import android.content.Context
import android.os.BatteryManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.codexkd.vivoassistant.R
import com.codexkd.vivoassistant.automation.AutomationEngine
import com.codexkd.vivoassistant.databinding.FragmentDashboardBinding
import com.codexkd.vivoassistant.memory.MemoryEngine
import com.codexkd.vivoassistant.models.Routine
import com.codexkd.vivoassistant.routines.RoutineManager
import com.codexkd.vivoassistant.utils.Constants
import com.codexkd.vivoassistant.utils.NetworkManager
import com.codexkd.vivoassistant.voice.TTSManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var memoryEngine: MemoryEngine
    private lateinit var routineManager: RoutineManager
    private lateinit var automationEngine: AutomationEngine
    private lateinit var networkManager: NetworkManager
    private lateinit var ttsManager: TTSManager
    private var orbPulseAnimator: ValueAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        memoryEngine     = MemoryEngine.getInstance(requireContext())
        routineManager   = RoutineManager.getInstance(requireContext())
        automationEngine = AutomationEngine.getInstance(requireContext())
        networkManager   = NetworkManager.getInstance(requireContext())
        ttsManager       = TTSManager(requireContext()).also { it.initialize() }

        setupGreeting()
        setupQuickActions()
        startOrbAnimation()

        viewLifecycleOwner.lifecycleScope.launch {
            updateStatusBar()
            loadRoutines()
            loadSuggestions()
        }
    }

    override fun onDestroyView() {
        orbPulseAnimator?.cancel()
        ttsManager.destroy()
        _binding = null
        super.onDestroyView()
    }

    // ── GREETING ────────────────────────────────────
    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour in 5..11  -> "Good morning"
            hour in 12..16 -> "Good afternoon"
            hour in 17..20 -> "Good evening"
            else           -> "Good night"
        }
        binding.tvGreeting.text = greeting
    }

    // ── STATUS ──────────────────────────────────────
    private fun updateStatusBar() {
        val bm = requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging
        binding.tvBattery.text = "$battery%${if (isCharging) " ⚡" else ""}"

        val netType = networkManager.getConnectionType()
        binding.tvNetStatus.text = netType

        val isOnline = networkManager.isNetworkAvailable()
        binding.chipAIStatus.text = if (isOnline) "● AI Ready" else "● AI Offline"
        binding.chipAIStatus.setTextColor(
            requireContext().getColor(
                if (isOnline) R.color.color_primary else R.color.color_error
            )
        )
    }

    // ── QUICK ACTIONS ───────────────────────────────
    private fun setupQuickActions() {
        data class QuickAction(val icon: String, val label: String, val action: () -> Unit)

        val actions = listOf(
            QuickAction("🔦", "Torch")      { runCmd("toggle flashlight") },
            QuickAction("🔕", "DND")        { runCmd("enable dnd") },
            QuickAction("📷", "Screenshot") { runCmd("take screenshot") },
            QuickAction("☀", "Bright")     { runCmd("set brightness 70") },
            QuickAction("🔔", "Notifs")     { openChat() },
            QuickAction("📶", "WiFi")       { runCmd("open wifi settings") }
        )

        val container = binding.rvQuickActions   // LinearLayout in HorizontalScrollView
        val inflater = LayoutInflater.from(requireContext())

        actions.forEach { action ->
            val chip = inflater.inflate(R.layout.item_quick_action, container, false) as CardView
            chip.findViewById<TextView>(R.id.tvActionIcon).text = action.icon
            chip.findViewById<TextView>(R.id.tvActionLabel).text = action.label
            chip.setOnClickListener { action.action() }
            container.addView(chip)
        }
    }

    private fun runCmd(cmd: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val command = automationEngine.parseCommand(cmd)
            val result = automationEngine.execute(command)
            if (result.isNotBlank()) ttsManager.speak(result)
        }
    }

    private fun openChat() {
        findNavController().navigate(R.id.action_dashboard_to_chat)
    }

    // ── ROUTINES ────────────────────────────────────
    private suspend fun loadRoutines() {
        routineManager.initialize()
        val routines = memoryEngine.getEnabledRoutines()
        withContext(Dispatchers.Main) {
            val container = binding.rvRoutines   // LinearLayout
            val inflater = LayoutInflater.from(requireContext())
            routines.take(4).forEach { routine ->
                val card = inflater.inflate(
                    R.layout.item_routine_card, container, false
                ) as CardView
                card.findViewById<TextView>(R.id.tvRoutineName).text = routine.name
                card.findViewById<TextView>(R.id.tvRoutineDesc).text = routine.description
                val accentColor = when (routine.id) {
                    Constants.ROUTINE_GAMING  -> requireContext().getColor(R.color.routine_gaming)
                    Constants.ROUTINE_STUDY   -> requireContext().getColor(R.color.routine_study)
                    Constants.ROUTINE_SLEEP   -> requireContext().getColor(R.color.routine_sleep)
                    Constants.ROUTINE_MORNING -> requireContext().getColor(R.color.routine_morning)
                    else                      -> requireContext().getColor(R.color.routine_work)
                }
                card.findViewById<View>(R.id.routineAccentBar).setBackgroundColor(accentColor)
                card.setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val result = routineManager.executeRoutine(routine.id)
                        ttsManager.speak(result)
                    }
                }
                container.addView(card)
            }
        }
    }

    // ── SUGGESTIONS ─────────────────────────────────
    private suspend fun loadSuggestions() {
        val suggestions = memoryEngine.getPredictiveSuggestions()
        withContext(Dispatchers.Main) {
            val container = binding.layoutSuggestions
            val inflater = LayoutInflater.from(requireContext())
            suggestions.forEach { suggestion ->
                val chip = inflater.inflate(
                    R.layout.item_suggestion_chip, container, false
                ) as TextView
                chip.text = suggestion
                chip.setOnClickListener {
                    val bundle = Bundle().apply { putString("prefill_text", suggestion) }
                    findNavController().navigate(R.id.action_dashboard_to_chat, bundle)
                }
                container.addView(chip)
            }
        }
    }

    // ── ORB ANIMATION ───────────────────────────────
    private fun startOrbAnimation() {
        orbPulseAnimator = ValueAnimator.ofFloat(0.95f, 1.05f).apply {
            duration = 2000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val scale = anim.animatedValue as Float
                binding.heroOrb.scaleX = scale
                binding.heroOrb.scaleY = scale
            }
            start()
        }
    }
}
