package com.codexkd.vivoassistant.ui

import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.codexkd.vivoassistant.R
import com.codexkd.vivoassistant.VivoApp
import com.codexkd.vivoassistant.ai.AIPersonality
import com.codexkd.vivoassistant.databinding.ActivitySettingsBinding
import com.codexkd.vivoassistant.utils.Constants
import com.codexkd.vivoassistant.utils.PermissionManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val app get() = application as VivoApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Settings"
        }
        loadCurrentSettings()
        setupSaveButton()
        setupPermissionsSection()
        setupAPIKeyToggle()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    // ── LOAD ────────────────────────────────────────
    private fun loadCurrentSettings() {
        lifecycleScope.launch {
            binding.etApiKey.setText(app.getStringPref(Constants.PREF_API_KEY, ""))
            setupModelSpinner(app.getStringPref(Constants.PREF_AI_MODEL, Constants.DEFAULT_AI_MODEL))
            setupPersonalitySpinner(app.getStringPref(Constants.PREF_AI_PERSONALITY, Constants.PERSONALITY_JARVIS))
            setupLanguageSpinner(app.getStringPref(Constants.PREF_VOICE_LANGUAGE, Constants.LANG_ENGLISH))

            val speed = app.getStringPref(Constants.PREF_VOICE_SPEED, "1.0").toFloatOrNull() ?: 1.0f
            val pitch = app.getStringPref(Constants.PREF_VOICE_PITCH, "1.0").toFloatOrNull() ?: 1.0f
            binding.seekVoiceSpeed.progress = (speed * 10).toInt()
            binding.seekVoicePitch.progress = (pitch * 10).toInt()
            binding.tvVoiceSpeedValue.text = String.format("%.1f", speed)
            binding.tvVoicePitchValue.text = String.format("%.1f", pitch)

            binding.switchOverlay.isChecked    = app.getBoolPref(Constants.PREF_OVERLAY_ENABLED, true)
            binding.switchBootStart.isChecked  = app.getBoolPref(Constants.PREF_BOOT_START, false)
            binding.switchNotifAI.isChecked    = app.getBoolPref(Constants.PREF_NOTIF_AI_ENABLED, true)
            binding.switchHaptic.isChecked     = app.getBoolPref(Constants.PREF_HAPTIC_ENABLED, true)
            binding.switchSound.isChecked      = app.getBoolPref(Constants.PREF_SOUND_ENABLED, true)
            binding.tvVersionValue.text        = "v1.0.0"
        }

        binding.seekVoiceSpeed.setOnSeekBarChangeListener(seekListener(binding.tvVoiceSpeedValue))
        binding.seekVoicePitch.setOnSeekBarChangeListener(seekListener(binding.tvVoicePitchValue))
    }

    private fun seekListener(tv: TextView) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
            tv.text = String.format("%.1f", progress / 10f)
        }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }

    // ── SPINNERS ────────────────────────────────────
    private fun setupModelSpinner(selected: String) {
        val models = listOf(Constants.DEFAULT_AI_MODEL, Constants.MODEL_GPT_MINI,
            Constants.MODEL_GEMINI_FLASH, Constants.MODEL_CLAUDE_SONNET)
        val names  = listOf("Claude Haiku (Fast)", "GPT-4o Mini", "Gemini Flash", "Claude Sonnet (Best)")
        binding.spinnerModel.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerModel.setSelection(models.indexOf(selected).coerceAtLeast(0))
    }

    private fun setupPersonalitySpinner(selected: String) {
        val personalities = listOf(Constants.PERSONALITY_JARVIS, Constants.PERSONALITY_FRIENDLY,
            Constants.PERSONALITY_CALM, Constants.PERSONALITY_MINIMAL, Constants.PERSONALITY_PRO)
        val names = personalities.map { AIPersonality.getDisplayName(it) }
        binding.spinnerPersonality.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerPersonality.setSelection(personalities.indexOf(selected).coerceAtLeast(0))
    }

    private fun setupLanguageSpinner(selected: String) {
        val langs = listOf(Constants.LANG_ENGLISH, Constants.LANG_HINDI)
        val names = listOf("English (India)", "Hindi (हिंदी)")
        binding.spinnerLanguage.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerLanguage.setSelection(langs.indexOf(selected).coerceAtLeast(0))
    }

    // ── SAVE ────────────────────────────────────────
    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener { saveSettings() }
    }

    private fun saveSettings() {
        val apiKey = binding.etApiKey.text?.toString()?.trim() ?: ""
        if (apiKey.isBlank()) {
            Toast.makeText(this, "API key cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        val models = listOf(Constants.DEFAULT_AI_MODEL, Constants.MODEL_GPT_MINI,
            Constants.MODEL_GEMINI_FLASH, Constants.MODEL_CLAUDE_SONNET)
        val personalities = listOf(Constants.PERSONALITY_JARVIS, Constants.PERSONALITY_FRIENDLY,
            Constants.PERSONALITY_CALM, Constants.PERSONALITY_MINIMAL, Constants.PERSONALITY_PRO)
        val langs = listOf(Constants.LANG_ENGLISH, Constants.LANG_HINDI)

        lifecycleScope.launch {
            app.savePreference(Constants.PREF_API_KEY,           apiKey)
            app.savePreference(Constants.PREF_AI_MODEL,          models[binding.spinnerModel.selectedItemPosition])
            app.savePreference(Constants.PREF_AI_PERSONALITY,    personalities[binding.spinnerPersonality.selectedItemPosition])
            app.savePreference(Constants.PREF_VOICE_LANGUAGE,    langs[binding.spinnerLanguage.selectedItemPosition])
            app.savePreference(Constants.PREF_VOICE_SPEED,       (binding.seekVoiceSpeed.progress / 10f).toString())
            app.savePreference(Constants.PREF_VOICE_PITCH,       (binding.seekVoicePitch.progress / 10f).toString())
            app.savePreference(Constants.PREF_OVERLAY_ENABLED,   binding.switchOverlay.isChecked)
            app.savePreference(Constants.PREF_BOOT_START,        binding.switchBootStart.isChecked)
            app.savePreference(Constants.PREF_NOTIF_AI_ENABLED,  binding.switchNotifAI.isChecked)
            app.savePreference(Constants.PREF_HAPTIC_ENABLED,    binding.switchHaptic.isChecked)
            app.savePreference(Constants.PREF_SOUND_ENABLED,     binding.switchSound.isChecked)
            app.refreshAIConfig()
            Toast.makeText(this@SettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()
        }
    }

    // ── API KEY VISIBILITY ──────────────────────────
    private fun setupAPIKeyToggle() {
        var visible = false
        binding.btnToggleApiKey.setOnClickListener {
            visible = !visible
            binding.etApiKey.inputType = if (visible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            binding.etApiKey.setSelection(binding.etApiKey.text?.length ?: 0)
            binding.btnToggleApiKey.setImageResource(
                if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
            )
        }
    }

    // ── PERMISSIONS ─────────────────────────────────
    private fun setupPermissionsSection() {
        val s = PermissionManager.getFullStatus(this)
        setPermRow(binding.btnGrantMic,              s.microphone,           { PermissionManager.requestMicrophone(this) })
        setPermRow(binding.btnGrantOverlay,          s.overlay,              { PermissionManager.openOverlaySettings(this) })
        setPermRow(binding.btnGrantAccessibility,    s.accessibility,        { PermissionManager.openAccessibilitySettings(this) })
        setPermRow(binding.btnGrantNotifications,    s.notifications,        { PermissionManager.requestNotifications(this) })
        setPermRow(binding.btnGrantNotifListener,    s.notificationListener, { PermissionManager.openNotificationListenerSettings(this) })
        setPermRow(binding.btnGrantWriteSettings,    s.writeSettings,        { PermissionManager.openWriteSettingsPage(this) })
        setPermRow(binding.btnGrantDND,              s.dnd,                  { PermissionManager.openDNDSettingsPage(this) })
    }

    private fun setPermRow(btn: Button, granted: Boolean, onGrant: () -> Unit) {
        if (granted) {
            btn.text = "✓ Granted"
            btn.isEnabled = false
            btn.setTextColor(getColor(R.color.color_success))
        } else {
            btn.text = "Grant"
            btn.isEnabled = true
            btn.setOnClickListener { onGrant() }
        }
    }

    override fun onResume() {
        super.onResume()
        setupPermissionsSection()
    }
}
