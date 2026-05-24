package com.codexkd.vivoassistant

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.codexkd.vivoassistant.utils.PermissionManager

/**
 * SplashActivity — Cinematic launch screen.
 *
 * Flow:
 * 1. Show animated logo + name
 * 2. Check if first launch → show setup
 * 3. Check critical permissions
 * 4. Navigate to MainActivity
 *
 * Duration: ~2.5 seconds total
 */
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install Android 12+ splash screen API
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Hide system UI for immersive splash
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        startSplashAnimation()
    }

    // ═══════════════════════════════════════════════
    // ANIMATIONS
    // ═══════════════════════════════════════════════

    private fun startSplashAnimation() {
        val orbView   = findViewById<View>(R.id.splashOrb)
        val ringView  = findViewById<View>(R.id.splashRing)
        val tvName    = findViewById<TextView>(R.id.tvAppName)
        val tvTagline = findViewById<TextView>(R.id.tvTagline)
        val tvVersion = findViewById<TextView>(R.id.tvVersion)

        // Initial state — all invisible
        orbView.alpha   = 0f
        orbView.scaleX  = 0f
        orbView.scaleY  = 0f
        ringView.alpha  = 0f
        ringView.scaleX = 0f
        ringView.scaleY = 0f
        tvName.alpha    = 0f
        tvName.translationY = 40f
        tvTagline.alpha = 0f
        tvTagline.translationY = 30f
        tvVersion.alpha = 0f

        // Step 1: Orb appears (0ms)
        val orbAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(orbView, "alpha", 0f, 1f).apply { duration = 600 },
                ObjectAnimator.ofFloat(orbView, "scaleX", 0f, 1f).apply {
                    duration = 700
                    interpolator = OvershootInterpolator(2.5f)
                },
                ObjectAnimator.ofFloat(orbView, "scaleY", 0f, 1f).apply {
                    duration = 700
                    interpolator = OvershootInterpolator(2.5f)
                }
            )
        }

        // Step 2: Ring pulse (400ms)
        val ringAnim = AnimatorSet().apply {
            startDelay = 400
            playTogether(
                ObjectAnimator.ofFloat(ringView, "alpha", 0f, 0.6f).apply { duration = 500 },
                ObjectAnimator.ofFloat(ringView, "scaleX", 0.5f, 1.4f).apply { duration = 700 },
                ObjectAnimator.ofFloat(ringView, "scaleY", 0.5f, 1.4f).apply { duration = 700 },
                ObjectAnimator.ofFloat(ringView, "alpha", 0.6f, 0f).apply {
                    duration = 500; startDelay = 400
                }
            )
        }

        // Step 3: App name (700ms)
        val nameAnim = AnimatorSet().apply {
            startDelay = 700
            playTogether(
                ObjectAnimator.ofFloat(tvName, "alpha", 0f, 1f).apply { duration = 500 },
                ObjectAnimator.ofFloat(tvName, "translationY", 40f, 0f).apply {
                    duration = 500
                    interpolator = AccelerateDecelerateInterpolator()
                }
            )
        }

        // Step 4: Tagline (1000ms)
        val taglineAnim = AnimatorSet().apply {
            startDelay = 1000
            playTogether(
                ObjectAnimator.ofFloat(tvTagline, "alpha", 0f, 1f).apply { duration = 400 },
                ObjectAnimator.ofFloat(tvTagline, "translationY", 30f, 0f).apply { duration = 400 }
            )
        }

        // Step 5: Version (1300ms)
        val versionAnim = ObjectAnimator.ofFloat(tvVersion, "alpha", 0f, 0.5f).apply {
            duration = 300; startDelay = 1300
        }

        // Play all
        AnimatorSet().apply {
            playTogether(orbAnim, ringAnim, nameAnim, taglineAnim, versionAnim)
            start()
        }

        // Navigate after animation completes
        handler.postDelayed({ navigateNext() }, 2500)
    }

    // ═══════════════════════════════════════════════
    // NAVIGATION LOGIC
    // ═══════════════════════════════════════════════

    private fun navigateNext() {
        val intent = Intent(this, MainActivity::class.java)

        // Check if this is the first launch
        // (API key not set → open onboarding/settings first)
        val status = PermissionManager.getFullStatus(this)

        if (!status.allCriticalGranted) {
            // Flag to show permission dialog in MainActivity
            intent.putExtra("show_permissions", true)
        }

        startActivity(intent)
        // No need to finish — noHistory=true in manifest
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
