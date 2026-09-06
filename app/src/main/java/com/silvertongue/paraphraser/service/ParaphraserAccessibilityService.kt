package com.silvertongue.paraphraser.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.silvertongue.paraphraser.BuildConfig
import com.silvertongue.paraphraser.overlay.FocusedTextBridge
import com.silvertongue.paraphraser.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class ParaphraserAccessibilityService : AccessibilityService(), FocusedTextBridge {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlayController: OverlayController? = null

    private var lastEvaluationAt = 0L
    private var wasTargetForeground = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayController = OverlayController(this, this, serviceScope)
        isRunning = true
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val accessibilityEvent = event ?: return
        val eventPackage = accessibilityEvent.packageName?.toString()

        if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (eventPackage !in TARGET_PACKAGES) return
            val now = SystemClock.uptimeMillis()
            if (now - lastEvaluationAt < TEXT_CHANGE_THROTTLE_MS) return
            lastEvaluationAt = now
        }

        refreshOverlayState()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isRunning = false
        overlayController?.destroy()
        overlayController = null
        serviceScope.cancel()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    override fun readFocusedText(): String? {
        val node = resolveEditableNode() ?: return null
        if (isShowingPlaceholder(node)) return null

        val text = node.text?.toString()
            ?.replace(ZERO_WIDTH_CHARACTERS, "")
            ?.trim()
            .orEmpty()

        return text.ifEmpty { null }
    }

    override fun writeFocusedText(text: String): Boolean {
        val node = resolveEditableNode() ?: run {
            Log.w(TAG, "No editable node resolved at insertion time")
            return false
        }
        val outcome = TextFieldWriter.insert(this, node, text)
        Log.i(TAG, "Insertion outcome: $outcome")
        return outcome != InsertionOutcome.FAILED
    }

    private fun refreshOverlayState() {
        val controller = overlayController ?: return
        val root = rootInActiveWindow
        val activePackage = root?.packageName?.toString()

        if (activePackage == packageName) return

        val isTargetForeground = activePackage in TARGET_PACKAGES

        if (isTargetForeground && !wasTargetForeground) {
            controller.clearSuppression()
        }
        wasTargetForeground = isTargetForeground

        if (!isTargetForeground) {
            controller.hide()
            return
        }

        val editableNode = resolveEditableNode(root)
        if (editableNode == null) {
            controller.hide()
        } else {
            logFocusedTextForDebug(editableNode)
            controller.showCollapsed()
        }
    }

    private fun resolveEditableNode(
        root: AccessibilityNodeInfo? = rootInActiveWindow
    ): AccessibilityNodeInfo? {
        val windowRoot = root ?: return null
        if (windowRoot.packageName?.toString() !in TARGET_PACKAGES) return null

        windowRoot.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { focused ->
            if (focused.isEditable) return focused
        }
        return firstEditableDescendant(windowRoot)
    }

    private fun firstEditableDescendant(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODES_SCANNED) {
            val node = queue.removeFirst()
            visited++
            if (node.isEditable && node.isVisibleToUser) return node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { queue.addLast(it) }
            }
        }
        return null
    }

    private fun isShowingPlaceholder(node: AccessibilityNodeInfo): Boolean =
        node.isShowingHintText || (node.textSelectionStart < 0 && node.textSelectionEnd < 0)

    private fun logFocusedTextForDebug(node: AccessibilityNodeInfo) {
        if (!BuildConfig.DEBUG) return
        if (node.packageName?.toString() !in TARGET_PACKAGES) return
        if (isShowingPlaceholder(node)) {
            Log.d(TAG, "Focused WhatsApp field is empty (showing placeholder)")
            return
        }
        Log.d(TAG, "Focused WhatsApp field text: ${node.text}")
    }

    companion object {

        @Volatile
        var isRunning: Boolean = false
            private set

        private const val TAG = "SilvertongueService"
        private const val TEXT_CHANGE_THROTTLE_MS = 250L
        private val ZERO_WIDTH_CHARACTERS = Regex("[\\u200B-\\u200D\\uFEFF]")
        private const val MAX_NODES_SCANNED = 400
        private val TARGET_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    }
}
