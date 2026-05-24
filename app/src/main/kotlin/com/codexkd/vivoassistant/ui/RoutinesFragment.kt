package com.codexkd.vivoassistant.ui

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codexkd.vivoassistant.R
import com.codexkd.vivoassistant.databinding.FragmentRoutinesBinding
import com.codexkd.vivoassistant.memory.MemoryEngine
import com.codexkd.vivoassistant.models.Routine
import com.codexkd.vivoassistant.routines.RoutineManager
import com.codexkd.vivoassistant.voice.TTSManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RoutinesFragment : Fragment() {

    private var _binding: FragmentRoutinesBinding? = null
    private val binding get() = _binding!!

    private lateinit var routineManager: RoutineManager
    private lateinit var memoryEngine: MemoryEngine
    private lateinit var ttsManager: TTSManager
    private lateinit var adapter: RoutineAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoutinesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        routineManager = RoutineManager.getInstance(requireContext())
        memoryEngine   = MemoryEngine.getInstance(requireContext())
        ttsManager     = TTSManager(requireContext()).also { it.initialize() }
        setupRecyclerView()
        observeRoutines()
    }

    override fun onDestroyView() {
        ttsManager.destroy()
        _binding = null
        super.onDestroyView()
    }

    private fun setupRecyclerView() {
        adapter = RoutineAdapter(
            onActivate = { routine -> activateRoutine(routine) },
            onToggle   = { routine, enabled -> toggleRoutine(routine, enabled) }
        )
        binding.rvRoutines.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRoutines.adapter = adapter
    }

    private fun observeRoutines() {
        viewLifecycleOwner.lifecycleScope.launch {
            memoryEngine.getRoutinesFlow().collectLatest { routines ->
                adapter.submitList(routines)
                binding.tvEmptyState.visibility =
                    if (routines.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun activateRoutine(routine: Routine) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
                val result = routineManager.executeRoutine(routine.id)
                ttsManager.speak(result)
                Toast.makeText(requireContext(), result, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun toggleRoutine(routine: Routine, enabled: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            memoryEngine.updateRoutine(routine.copy(isEnabled = enabled))
        }
    }

    // ── ADAPTER ─────────────────────────────────────
    inner class RoutineAdapter(
        private val onActivate: (Routine) -> Unit,
        private val onToggle: (Routine, Boolean) -> Unit
    ) : RecyclerView.Adapter<RoutineAdapter.VH>() {

        private val items = mutableListOf<Routine>()

        fun submitList(list: List<Routine>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_routine_full, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            private val tvName    : TextView = view.findViewById(R.id.tvRoutineName)
            private val tvDesc    : TextView = view.findViewById(R.id.tvRoutineDesc)
            private val tvLast    : TextView = view.findViewById(R.id.tvLastUsed)
            private val btnAct    : Button   = view.findViewById(R.id.btnActivate)
            private val swEnabled : Switch   = view.findViewById(R.id.switchEnabled)

            fun bind(routine: Routine) {
                tvName.text = routine.name
                tvDesc.text = routine.description
                tvLast.text = if (routine.lastExecuted > 0)
                    "Last used: ${relativeTime(routine.lastExecuted)}"
                else "Never used"

                swEnabled.isChecked = routine.isEnabled
                swEnabled.setOnCheckedChangeListener { _, checked -> onToggle(routine, checked) }
                btnAct.setOnClickListener { onActivate(routine) }
                btnAct.isEnabled = routine.isEnabled
            }

            private fun relativeTime(ts: Long): String {
                val diff = System.currentTimeMillis() - ts
                return when {
                    diff < 60_000L      -> "just now"
                    diff < 3_600_000L   -> "${diff / 60_000}m ago"
                    diff < 86_400_000L  -> "${diff / 3_600_000}h ago"
                    else                -> "${diff / 86_400_000}d ago"
                }
            }
        }
    }
}
