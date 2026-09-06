package com.silvertongue.paraphraser.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.silvertongue.paraphraser.service.ParaphraserAccessibilityService
import kotlinx.coroutines.delay

object AccessibilityServiceRepair {

    fun isAvailable(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun repair(context: Context): Boolean {
        if (!isAvailable(context)) return false

        val component = ComponentName(context, ParaphraserAccessibilityService::class.java)
            .flattenToString()
        val resolver = context.contentResolver

        return runCatching {
            val others = Settings.Secure
                .getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty()
                .split(':')
                .filter { it.isNotBlank() && !it.equals(component, ignoreCase = true) }

            Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                others.joinToString(":")
            )
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)

            delay(REBIND_DELAY_MS)

            Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                (others + component).joinToString(":")
            )
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)

            delay(REBIND_DELAY_MS)
            PermissionStatus.isAccessibilityServiceEnabled(context)
        }.getOrDefault(false)
    }

    const val GRANT_COMMAND =
        "adb shell pm grant com.silvertongue.paraphraser android.permission.WRITE_SECURE_SETTINGS"

    private const val REBIND_DELAY_MS = 600L
}
