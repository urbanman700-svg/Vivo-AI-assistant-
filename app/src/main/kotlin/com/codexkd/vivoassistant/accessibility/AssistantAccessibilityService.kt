package com.codexkd.vivoassistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.codexkd.vivoassistant.utils.Constants
import kotlinx.coroutines.*

/**
 * AssistantAccessibilityService — The Android Automation Core.
 *
 * This service uses Android Accessibility APIs to:
 * - Navigate apps (back, home, recents)
 * - Tap UI elements by text/content description
 * - Type text into input fields
 * - Scroll lists and content
 * - Take screenshots
 * - Read visible UI content
 * - Perform smart gestures
 *
 * IMPORTANT:
 * - All automation is through VISIBLE UI interaction only
 * - No private data access or hidden monitoring
 * - User must manually enable this service in Settings > Accessibility
 */
class AssistantAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Track current app foreground
    var currentPackage: String = ""
        private set

    // Callback for screen content changes
    var onScreenChanged: ((packageName: String) -> Unit)? = null
    var onNodeFound: ((text: String) -> Unit)? = null

    // ═══════════════════════════════════════════════
    // SERVICE LIFECYCLE
    // ═══════════════════════════════════════════════

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service connected ✓")

        // Broadcast that service is ready
        sendBroadcast(Intent("${Constants.APP_PACKAGE}.ACCESSIBILITY_READY"))
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        scope.cancel()
        Log.d(TAG, "Accessibility Service disconnected")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    // ═══════════════════════════════════════════════
    // EVENT HANDLING
    // ═══════════════════════════════════════════════

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                if (pkg != currentPackage) {
                    currentPackage = pkg
                    onScreenChanged?.invoke(pkg)
                }
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                // User is typing — track for context
            }
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                // Notification appeared — track
            }
        }
    }

    // ═══════════════════════════════════════════════
    // GLOBAL NAVIGATION ACTIONS
    // ═══════════════════════════════════════════════

    fun pressBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        Log.d(TAG, "Back pressed")
    }

    fun pressHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        Log.d(TAG, "Home pressed")
    }

    fun pressRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        Log.d(TAG, "Recents pressed")
    }

    fun expandNotifications() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        Log.d(TAG, "Notifications expanded")
    }

    fun expandQuickSettings() {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        Log.d(TAG, "Quick settings expanded")
    }

    fun lockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    fun takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            Log.d(TAG, "Screenshot taken")
        }
    }

    // ═══════════════════════════════════════════════
    // ELEMENT INTERACTION
    // ═══════════════════════════════════════════════

    /**
     * Find and click a UI element by its visible text.
     */
    fun clickByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false

        val nodes = root.findAccessibilityNodeInfosByText(text)
        val target = nodes?.firstOrNull { it.isClickable } ?: nodes?.firstOrNull()

        return if (target != null) {
            val clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "Click '$text': $clicked")
            target.recycle()
            root.recycle()
            clicked
        } else {
            Log.w(TAG, "Node '$text' not found")
            root.recycle()
            false
        }
    }

    /**
     * Find and click by content description (for icons, buttons without text).
     */
    fun clickByContentDesc(desc: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val result = findAndClickByDesc(root, desc)
        root.recycle()
        return result
    }

    private fun findAndClickByDesc(node: AccessibilityNodeInfo, desc: String): Boolean {
        val nodeDesc = node.contentDescription?.toString() ?: ""
        if (nodeDesc.contains(desc, ignoreCase = true) && node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickByDesc(child, desc)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    /**
     * Type text into the focused input field.
     */
    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false

        // Find focused editable node
        val focused = findFocusedEditableNode(root)
        root.recycle()

        return if (focused != null) {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            val result = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Log.d(TAG, "Type text: $result")
            focused.recycle()
            result
        } else {
            Log.w(TAG, "No editable field focused")
            false
        }
    }

    /**
     * Type text and then click the send/submit button.
     */
    fun typeAndSend(text: String): Boolean {
        if (!typeText(text)) return false

        scope.launch {
            delay(300)
            // Try common send button labels
            val sendLabels = listOf("Send", "भेजें", "Submit", "Post", "Reply")
            val sent = sendLabels.any { clickByText(it) }

            if (!sent) {
                // Try pressing Enter key action
                val root = rootInActiveWindow
                val focused = root?.let { findFocusedEditableNode(it) }
                focused?.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
                focused?.recycle()
                root?.recycle()
            }
            Log.d(TAG, "Message sent: $sent")
        }
        return true
    }

    private fun findFocusedEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isFocused && root.isEditable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findFocusedEditableNode(child)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    // ═══════════════════════════════════════════════
    // SCROLLING
    // ═══════════════════════════════════════════════

    fun scroll(up: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findScrollableNode(root)
        root.recycle()

        return if (scrollable != null) {
            val action = if (up)
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD

            val result = scrollable.performAction(action)
            scrollable.recycle()
            Log.d(TAG, "Scroll ${if (up) "up" else "down"}: $result")
            result
        } else {
            // Fallback: gesture-based scroll
            gestureScroll(up)
        }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun gestureScroll(up: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels

        val startY = if (up) screenHeight * 0.3f else screenHeight * 0.7f
        val endY = if (up) screenHeight * 0.7f else screenHeight * 0.3f
        val centerX = screenWidth * 0.5f

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 400))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    // ═══════════════════════════════════════════════
    // SCREEN CONTENT READING
    // ═══════════════════════════════════════════════

    /**
     * Extract all visible text from the current screen.
     * Used for screen intelligence and OCR.
     */
    fun extractScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        extractTextFromNode(root, sb)
        root.recycle()
        return sb.toString().trim()
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo, sb: StringBuilder) {
        node.text?.let { if (it.isNotBlank()) sb.appendLine(it) }
        node.contentDescription?.let { if (it.isNotBlank()) sb.appendLine(it) }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            extractTextFromNode(child, sb)
            child.recycle()
        }
    }

    /**
     * Find a UI element by text and return its bounds.
     */
    fun findElementBounds(text: String): Rect? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByText(text)
        val node = nodes?.firstOrNull() ?: return null

        val rect = Rect()
        node.getBoundsInScreen(rect)
        node.recycle()
        root.recycle()
        return rect
    }

    // ═══════════════════════════════════════════════
    // COMPANION (Singleton access)
    // ═══════════════════════════════════════════════

    companion object {
        private const val TAG = "AssistantAccessibility"

        @Volatile
        private var instance: AssistantAccessibilityService? = null

        fun getInstance(): AssistantAccessibilityService? = instance

        fun isServiceEnabled(): Boolean = instance != null
    }
}
