package com.codexkd.vivoassistant.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import com.codexkd.vivoassistant.R
import com.codexkd.vivoassistant.utils.PermissionManager

class PermissionSetupDialog(
    private val activity: Activity,
    private val onComplete: (Boolean) -> Unit
) : Dialog(activity) {

    data class PermRow(
        val title: String,
        val description: String,
        val isRequired: Boolean,
        val checkGranted: () -> Boolean,
        val requestGrant: () -> Unit
    )

    private val permRows = mutableListOf<PermRow>()
    private val btnMap   = mutableMapOf<String, Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            requestFeature(Window.FEATURE_NO_TITLE)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.CENTER)
        }

        val view = LayoutInflater.from(activity)
            .inflate(R.layout.dialog_permission_setup, null)
        setContentView(view)
        setCancelable(false)

        buildRows()
        renderRows(view)
        setupButtons(view)
    }

    private fun buildRows() {
        permRows.addAll(listOf(
            PermRow("Microphone",            "For voice commands",             true,
                { PermissionManager.hasMicrophonePermission(activity) },
                { PermissionManager.requestMicrophone(activity) }),

            PermRow("Display Over Apps",     "For floating AI orb",            true,
                { PermissionManager.hasOverlayPermission(activity) },
                { PermissionManager.openOverlaySettings(activity) }),

            PermRow("Accessibility Service", "For app automation",             false,
                { PermissionManager.hasAccessibilityPermission(activity) },
                { PermissionManager.openAccessibilitySettings(activity) }),

            PermRow("Notification Access",   "For AI notification summaries",  false,
                { PermissionManager.hasNotificationListenerPermission(activity) },
                { PermissionManager.openNotificationListenerSettings(activity) }),

            PermRow("Modify System Settings","To change brightness",            false,
                { PermissionManager.hasWriteSettingsPermission(activity) },
                { PermissionManager.openWriteSettingsPage(activity) }),

            PermRow("Do Not Disturb",        "For sleep and gaming modes",      false,
                { PermissionManager.hasDNDPermission(activity) },
                { PermissionManager.openDNDSettingsPage(activity) })
        ))
    }

    private fun renderRows(root: View) {
        val container = root.findViewById<LinearLayout>(R.id.permContainer)
        val inflater  = LayoutInflater.from(activity)

        permRows.forEach { perm ->
            val row = inflater.inflate(R.layout.item_permission_row, container, false)

            row.findViewById<TextView>(R.id.tvPermTitle).text = perm.title
            row.findViewById<TextView>(R.id.tvPermDesc).text  = perm.description

            val badge = row.findViewById<TextView>(R.id.tvPermBadge)
            badge.text = if (perm.isRequired) "Required" else "Optional"
            badge.setBackgroundColor(
                if (perm.isRequired) activity.getColor(R.color.color_error)
                else                 activity.getColor(R.color.bg_elevated)
            )

            val btn = row.findViewById<Button>(R.id.btnGrantPerm)
            refreshBtn(btn, perm.checkGranted())
            btn.setOnClickListener { perm.requestGrant() }

            btnMap[perm.title] = btn
            container.addView(row)
        }
    }

    private fun setupButtons(root: View) {
        root.findViewById<Button>(R.id.btnPermContinue).setOnClickListener {
            val allRequired = permRows.filter { it.isRequired }.all { it.checkGranted() }
            dismiss()
            onComplete(allRequired)
        }
        root.findViewById<TextView>(R.id.tvPermSkip).setOnClickListener {
            dismiss()
            onComplete(false)
        }
    }

    private fun refreshBtn(btn: Button, granted: Boolean) {
        if (granted) {
            btn.text = "✓ Granted"
            btn.isEnabled = false
            btn.setTextColor(activity.getColor(R.color.color_success))
        } else {
            btn.text = "Grant"
            btn.isEnabled = true
            btn.setTextColor(activity.getColor(R.color.text_primary))
        }
    }

    /** Call this when user returns from a system settings page */
    fun refreshStatuses() {
        permRows.forEach { perm ->
            btnMap[perm.title]?.let { btn -> refreshBtn(btn, perm.checkGranted()) }
        }
    }
}
