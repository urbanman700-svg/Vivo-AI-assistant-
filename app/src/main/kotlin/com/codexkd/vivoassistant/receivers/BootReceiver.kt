package com.codexkd.vivoassistant.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.codexkd.vivoassistant.services.AssistantForegroundService
import com.codexkd.vivoassistant.utils.Constants
import kotlinx.coroutines.*

/**
 * BootReceiver — Automatically restarts Vivo Assistant after device reboot.
 *
 * Only starts if user has enabled auto-start in Settings.
 * Delayed start (3s) to let system settle after boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action !in listOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_QUICKBOOT_POWERON,
                "com.htc.intent.action.QUICKBOOT_POWERON"
            )
        ) return

        Log.d(TAG, "Boot completed — checking auto-start setting")

        // Check auto-start preference in background
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? com.codexkd.vivoassistant.VivoApp
                val autoStart = app?.getBoolPref(Constants.PREF_BOOT_START, false) ?: false

                if (autoStart) {
                    Log.d(TAG, "Auto-start enabled — starting AssistantForegroundService in 3s")

                    // Delay to let system fully settle
                    delay(3000L)

                    withContext(Dispatchers.Main) {
                        AssistantForegroundService.start(context)
                        Log.d(TAG, "AssistantForegroundService started from boot ✓")
                    }
                } else {
                    Log.d(TAG, "Auto-start disabled — skipping")
                }
            } catch (e: Exception) {
                Log.e(TAG, "BootReceiver error: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
