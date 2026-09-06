package com.silvertongue.paraphraser.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object SettingsLauncher {

    fun openOverlayPermission(activity: Activity) {
        val vendorEditor = Intent(MIUI_PERMISSION_EDITOR)
            .putExtra(MIUI_PACKAGE_EXTRA, activity.packageName)
        if (runCatching { activity.startActivity(vendorEditor) }.isSuccess) return

        runCatching {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        }
    }

    fun openAccessibilitySettings(activity: Activity) {
        runCatching { activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    private const val MIUI_PERMISSION_EDITOR = "miui.intent.action.APP_PERM_EDITOR"
    private const val MIUI_PACKAGE_EXTRA = "extra_pkgname"
}
