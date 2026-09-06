package com.silvertongue.paraphraser.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ContextThemeWrapper
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.silvertongue.paraphraser.AppGraph
import com.silvertongue.paraphraser.R
import com.silvertongue.paraphraser.data.OverlayPosition
import com.silvertongue.paraphraser.data.SettingsRepository
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OverlayController(
    private val context: Context,
    private val bridge: FocusedTextBridge,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository = AppGraph.settings
) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val lifecycleOwner = OverlayLifecycleOwner()

    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var requestJob: Job? = null

    private var uiState by mutableStateOf<OverlayUiState>(OverlayUiState.Collapsed)
    private var isExpanded by mutableStateOf(false)

    private var isAttached = false
    private var isSuppressed = false
    private var bubbleX = 0
    private var bubbleY = 0

    fun showCollapsed() {
        if (isSuppressed) return
        if (!ensureAttached()) return
        if (isExpanded) return
        composeView?.visibility = View.VISIBLE
    }

    fun hide() {
        if (!isAttached) return
        collapse()
        composeView?.visibility = View.GONE
    }

    fun clearSuppression() {
        isSuppressed = false
    }

    fun destroy() {
        requestJob?.cancel()
        val view = composeView ?: return
        runCatching { windowManager.removeView(view) }
        lifecycleOwner.onDestroy()
        composeView = null
        layoutParams = null
        isAttached = false
    }

    private fun ensureAttached(): Boolean {
        if (isAttached) return true
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission not granted; cannot attach overlay window")
            return false
        }

        val params = collapsedLayoutParams()
        val themedContext = ContextThemeWrapper(context, R.style.Theme_Silvertongue)
        val view = ComposeView(themedContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { OverlayRoot() }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    collapse()
                    true
                } else {
                    false
                }
            }
        }

        lifecycleOwner.attachTo(view)
        lifecycleOwner.onCreate()
        lifecycleOwner.onResume()

        return try {
            windowManager.addView(view, params)
            composeView = view
            layoutParams = params
            isAttached = true
            restorePosition()
            true
        } catch (error: WindowManager.BadTokenException) {
            Log.e(TAG, "Overlay window rejected by WindowManager", error)
            lifecycleOwner.onDestroy()
            false
        }
    }

    @androidx.compose.runtime.Composable
    private fun OverlayRoot() {
        if (isExpanded) {
            OverlayPanel(
                state = uiState,
                onSuggestionSelected = ::applySuggestion,
                onDismiss = ::collapse
            )
        } else {
            OverlayBubble(
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { expand() },
                        onLongPress = { suppressUntilReopen() }
                    )
                }.pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            moveBubble(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
                        },
                        onDragEnd = { persistPosition() }
                    )
                }
            )
        }
    }

    private fun expand() {
        if (requestJob?.isActive == true) return

        val rawText = bridge.readFocusedText()?.trim().orEmpty()
        if (rawText.isEmpty()) {
            Toast.makeText(context, "Nothing to rewrite", Toast.LENGTH_SHORT).show()
            return
        }

        uiState = OverlayUiState.Loading
        isExpanded = true
        applyLayoutParams(expandedLayoutParams())

        requestJob = scope.launch {
            val result = AppGraph.paraphraseRepository.paraphrase(rawText)
            uiState = result.fold(
                onSuccess = { outcome ->
                    OverlayUiState.Suggestions(
                        items = outcome.suggestions,
                        fallbackProvider = outcome.provider.displayName.takeIf { outcome.usedFallback }
                    )
                },
                onFailure = { OverlayUiState.Failed(it.message ?: "Could not rewrite that") }
            )
        }
    }

    private fun collapse() {
        requestJob?.cancel()
        requestJob = null
        if (!isExpanded) return
        isExpanded = false
        uiState = OverlayUiState.Collapsed
        applyLayoutParams(collapsedLayoutParams())
    }

    private fun applySuggestion(suggestion: String) {
        val inserted = bridge.writeFocusedText(suggestion)
        if (!inserted) {
            uiState = OverlayUiState.Failed("Could not write into the message box")
            return
        }
        collapse()
    }

    private fun suppressUntilReopen() {
        isSuppressed = true
        collapse()
        composeView?.visibility = View.GONE
        Toast.makeText(context, "Hidden until you reopen WhatsApp", Toast.LENGTH_SHORT).show()
    }

    private fun moveBubble(deltaX: Int, deltaY: Int) {
        val params = layoutParams ?: return
        val view = composeView ?: return
        val metrics = context.resources.displayMetrics
        bubbleX = (bubbleX + deltaX).coerceIn(0, metrics.widthPixels - view.width)
        bubbleY = (bubbleY + deltaY).coerceIn(0, metrics.heightPixels - view.height)
        params.x = bubbleX
        params.y = bubbleY
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun persistPosition() {
        scope.launch { settings.saveOverlayPosition(OverlayPosition(bubbleX, bubbleY)) }
    }

    private fun restorePosition() {
        scope.launch {
            val stored = settings.overlayPosition.first()
            val view = composeView ?: return@launch
            val metrics = context.resources.displayMetrics
            bubbleX = if (stored.x == SettingsRepository.UNSET_POSITION) {
                metrics.widthPixels - DEFAULT_EDGE_INSET_PX
            } else {
                stored.x
            }
            bubbleY = if (stored.y == SettingsRepository.UNSET_POSITION) {
                (metrics.heightPixels * DEFAULT_VERTICAL_FRACTION).roundToInt()
            } else {
                stored.y
            }
            if (!isExpanded) {
                val params = layoutParams ?: return@launch
                params.x = bubbleX
                params.y = bubbleY
                runCatching { windowManager.updateViewLayout(view, params) }
            }
        }
    }

    private fun applyLayoutParams(params: WindowManager.LayoutParams) {
        val view = composeView ?: return
        layoutParams = params
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun collapsedLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        BASE_FLAGS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bubbleX
        y = bubbleY
    }

    private fun expandedLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        BASE_FLAGS or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = (context.resources.displayMetrics.heightPixels * PANEL_VERTICAL_FRACTION).roundToInt()
    }

    companion object {
        private const val TAG = "SilvertongueOverlay"
        private const val DEFAULT_EDGE_INSET_PX = 160
        private const val DEFAULT_VERTICAL_FRACTION = 0.55f
        private const val PANEL_VERTICAL_FRACTION = 0.18f
        private const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
    }
}
