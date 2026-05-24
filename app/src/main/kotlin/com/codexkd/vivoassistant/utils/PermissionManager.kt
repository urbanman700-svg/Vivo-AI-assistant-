package com.codexkd.vivoassistant.utils

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.codexkd.vivoassistant.accessibility.AssistantAccessibilityService

/**
 * PermissionManager — Centralized permission handling.
 *
 * Manages all runtime permissions needed by Vivo Assistant.
 * Provides clear status reporting and guided permission grant flows.
 */
object PermissionManager {

    // Permission request codes
    const val RC_AUDIO          = 101
    const val RC_NOTIFICATIONS  = 102
    const val RC_STORAGE        = 103
    const val RC_CAMERA         = 104
    const val RC_BLUETOOTH      = 105
    const val RC_ALL_REQUIRED   = 200

    // ═══════════════════════════════════════════════
    // STATUS CHECKS
    // ═══════════════════════════════════════════════

    fun hasMicrophonePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    fun hasOverlayPermission(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun hasAccessibilityPermission(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val componentName = ComponentName(
            context, AssistantAccessibilityService::class.java
        ).flattenToString()

        return enabledServices.split(":").any {
            it.equals(componentName, ignoreCase = true)
        }
    }

    fun hasNotificationListenerPermission(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val componentName = ComponentName(
            context,
            com.codexkd.vivoassistant.services.NotificationAIService::class.java
        ).flattenToString()
        return flat.split(":").any { it.equals(componentName, ignoreCase = true) }
    }

    fun hasWriteSettingsPermission(context: Context): Boolean =
        Settings.System.canWrite(context)

    fun hasDNDPermission(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    // ═══════════════════════════════════════════════
    // BULK STATUS
    // ═══════════════════════════════════════════════

    data class PermissionStatus(
        val microphone: Boolean,
        val overlay: Boolean,
        val accessibility: Boolean,
        val notifications: Boolean,
        val notificationListener: Boolean,
        val writeSettings: Boolean,
        val dnd: Boolean,
        val camera: Boolean
    ) {
        val allCriticalGranted: Boolean
            get() = microphone && overlay && notifications

        val allGranted: Boolean
            get() = microphone && overlay && accessibility &&
                    notifications && notificationListener &&
                    writeSettings && dnd
    }

    fun getFullStatus(context: Context) = PermissionStatus(
        microphone           = hasMicrophonePermission(context),
        overlay              = hasOverlayPermission(context),
        accessibility        = hasAccessibilityPermission(context),
        notifications        = hasNotificationPermission(context),
        notificationListener = hasNotificationListenerPermission(context),
        writeSettings        = hasWriteSettingsPermission(context),
        dnd                  = hasDNDPermission(context),
        camera               = hasCameraPermission(context)
    )

    // ═══════════════════════════════════════════════
    // GRANT FLOWS
    // ═══════════════════════════════════════════════

    fun requestMicrophone(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            RC_AUDIO
        )
    }

    fun requestNotifications(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                RC_NOTIFICATIONS
            )
        }
    }

    fun requestCamera(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.CAMERA),
            RC_CAMERA
        )
    }

    fun openOverlaySettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    fun openNotificationListenerSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    fun openWriteSettingsPage(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    fun openDNDSettingsPage(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    fun openAppSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            // Last resort
        }
    }

    // ═══════════════════════════════════════════════
    // HUMAN-READABLE DESCRIPTIONS
    // ═══════════════════════════════════════════════

    fun getPermissionTitle(key: String): String = when (key) {
        "microphone"           -> "Microphone"
        "overlay"              -> "Display Over Other Apps"
        "accessibility"        -> "Accessibility Service"
        "notifications"        -> "Post Notifications"
        "notificationListener" -> "Notification Access"
        "writeSettings"        -> "Modify System Settings"
        "dnd"                  -> "Do Not Disturb Access"
        "camera"               -> "Camera (for OCR)"
        else -> key
    }

    fun getPermissionDescription(key: String): String = when (key) {
        "microphone"           -> "Required for voice commands"
        "overlay"              -> "Required for floating AI orb"
        "accessibility"        -> "Required for app automation and control"
        "notifications"        -> "Required to show assistant alerts"
        "notificationListener" -> "Required for AI notification summaries"
        "writeSettings"        -> "Required to change brightness"
        "dnd"                  -> "Required for DND and sleep mode"
        "camera"               -> "Required for screen OCR analysis"
        else -> ""
    }
}
