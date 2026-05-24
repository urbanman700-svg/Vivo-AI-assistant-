package com.codexkd.vivoassistant.automation

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * SystemController — Direct Android system hardware controls.
 *
 * Controls:
 * - Screen brightness
 * - Volume levels
 * - Bluetooth toggle
 * - Flashlight (torch)
 * - DND (Do Not Disturb)
 * - Quick Settings panel
 * - WiFi Settings
 * - Battery saver mode
 */
class SystemController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var torchCameraId: String? = null
    private var isTorchOn = false

    init {
        // Find the main back camera for flashlight
        torchCameraId = try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            null
        }
    }

    // ═══════════════════════════════════════════════
    // BRIGHTNESS
    // ═══════════════════════════════════════════════

    fun setBrightness(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        val androidValue = (clamped / 100f * 255).toInt()

        try {
            // Disable auto-brightness first
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            // Set brightness
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                androidValue
            )
            Log.d(TAG, "Brightness set to $percent% ($androidValue)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Brightness permission denied. Need WRITE_SETTINGS.")
            // Open Settings page to grant permission
            openWriteSettingsPermission()
        }
    }

    fun getBrightness(): Int {
        return try {
            val value = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            (value / 255f * 100).toInt()
        } catch (e: Exception) { 50 }
    }

    // ═══════════════════════════════════════════════
    // VOLUME
    // ═══════════════════════════════════════════════

    fun setVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)

        // Set media volume
        val maxMedia = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val mediaLevel = (clamped / 100f * maxMedia).toInt()
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            mediaLevel,
            0
        )

        // Set ringer volume proportionally
        val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        val ringLevel = (clamped / 100f * maxRing).toInt()
        if (ringLevel > 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_RING, ringLevel, 0)
        }

        Log.d(TAG, "Volume set to $percent%")
    }

    fun setMute(mute: Boolean) {
        if (mute) {
            audioManager.adjustVolume(AudioManager.ADJUST_MUTE, 0)
        } else {
            audioManager.adjustVolume(AudioManager.ADJUST_UNMUTE, 0)
        }
    }

    fun getMediaVolume(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) (current.toFloat() / max * 100).toInt() else 0
    }

    // ═══════════════════════════════════════════════
    // FLASHLIGHT (TORCH)
    // ═══════════════════════════════════════════════

    fun setFlashlight(on: Boolean) {
        val cameraId = torchCameraId ?: run {
            Log.e(TAG, "No camera with flash found")
            return
        }

        try {
            cameraManager.setTorchMode(cameraId, on)
            isTorchOn = on
            Log.d(TAG, "Flashlight: ${if (on) "ON" else "OFF"}")
        } catch (e: Exception) {
            Log.e(TAG, "Flashlight error: ${e.message}")
        }
    }

    fun toggleFlashlight() = setFlashlight(!isTorchOn)

    fun isFlashlightOn() = isTorchOn

    // ═══════════════════════════════════════════════
    // DO NOT DISTURB (DND)
    // ═══════════════════════════════════════════════

    fun setDND(enable: Boolean) {
        if (!notifManager.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "DND access not granted")
            openDNDPermission()
            return
        }

        notifManager.setInterruptionFilter(
            if (enable) {
                NotificationManager.INTERRUPTION_FILTER_NONE       // Total silence
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL        // All notifications
            }
        )
        Log.d(TAG, "DND: ${if (enable) "enabled" else "disabled"}")
    }

    fun isDNDEnabled(): Boolean {
        return notifManager.currentInterruptionFilter ==
                NotificationManager.INTERRUPTION_FILTER_NONE ||
                notifManager.currentInterruptionFilter ==
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
    }

    // ═══════════════════════════════════════════════
    // BLUETOOTH
    // ═══════════════════════════════════════════════

    fun toggleBluetooth(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ — can't toggle programmatically, open settings
            openBluetoothSettings()
            return
        }

        @Suppress("DEPRECATION")
        val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        try {
            if (enable) {
                @Suppress("DEPRECATION")
                bluetoothAdapter?.enable()
            } else {
                @Suppress("DEPRECATION")
                bluetoothAdapter?.disable()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission error: ${e.message}")
            openBluetoothSettings()
        }
    }

    fun isBluetoothEnabled(): Boolean {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        return adapter?.isEnabled ?: false
    }

    // ═══════════════════════════════════════════════
    // NAVIGATION INTENTS
    // ═══════════════════════════════════════════════

    fun openWifiSettings() {
        openSettings(Settings.Panel.ACTION_WIFI)
    }

    fun openBluetoothSettings() {
        openSettings(Settings.Panel.ACTION_BLUETOOTH)
    }

    fun openVolumePanel() {
        openSettings(Settings.Panel.ACTION_VOLUME)
    }

    fun openInternetSettings() {
        openSettings(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    }

    private fun openSettings(action: String) {
        try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openSettings error for $action: ${e.message}")
        }
    }

    private fun openWriteSettingsPermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Can't open write settings: ${e.message}")
        }
    }

    private fun openDNDPermission() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Can't open DND settings: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SystemController"
    }
}
