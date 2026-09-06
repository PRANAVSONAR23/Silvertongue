package com.silvertongue.paraphraser.ui

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.silvertongue.paraphraser.service.ParaphraserAccessibilityService

object PermissionStatus {

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isAccessibilityServiceRunning(): Boolean = ParaphraserAccessibilityService.isRunning

    fun isVendorWithExtraOverlayGate(): Boolean =
        android.os.Build.MANUFACTURER.lowercase().let { manufacturer ->
            listOf("xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "iqoo")
                .any { manufacturer.contains(it) }
        }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (TextUtils.isEmpty(enabledServices)) return false

        val component = ComponentName(context, ParaphraserAccessibilityService::class.java)
        val flattened = component.flattenToString()
        val shortFlattened = component.flattenToShortString()

        return enabledServices.split(':').any { entry ->
            entry.equals(flattened, ignoreCase = true) ||
                entry.equals(shortFlattened, ignoreCase = true)
        }
    }
}
