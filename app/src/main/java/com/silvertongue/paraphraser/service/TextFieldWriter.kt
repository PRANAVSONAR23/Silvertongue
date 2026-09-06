package com.silvertongue.paraphraser.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

enum class InsertionOutcome { SET_TEXT, CLIPBOARD_PASTE, FAILED }

object TextFieldWriter {

    private const val TAG = "SilvertongueWriter"

    fun insert(context: Context, node: AccessibilityNodeInfo, text: String): InsertionOutcome {
        if (supportsSetText(node)) {
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                moveCursorToEnd(node, text.length)
                return InsertionOutcome.SET_TEXT
            }
            Log.w(TAG, "ACTION_SET_TEXT was advertised but rejected; trying clipboard paste")
        } else {
            Log.w(
                TAG,
                "ACTION_SET_TEXT missing from node action list (${node.actionList.map { it.id }}); trying clipboard paste"
            )
        }

        return if (pasteFromClipboard(context, node, text)) {
            InsertionOutcome.CLIPBOARD_PASTE
        } else {
            Log.e(TAG, "Both ACTION_SET_TEXT and ACTION_PASTE failed on the focused node")
            InsertionOutcome.FAILED
        }
    }

    private fun supportsSetText(node: AccessibilityNodeInfo): Boolean =
        node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }

    private fun moveCursorToEnd(node: AccessibilityNodeInfo, position: Int) {
        val arguments = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, position)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, position)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments)
    }

    private fun pasteFromClipboard(
        context: Context,
        node: AccessibilityNodeInfo,
        text: String
    ): Boolean {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))

        val selectAll = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                node.text?.length ?: 0
            )
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectAll)

        if (!node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return false
        moveCursorToEnd(node, text.length)
        return true
    }

    private const val CLIP_LABEL = "silvertongue_rewrite"
}
