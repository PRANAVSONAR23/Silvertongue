package com.silvertongue.paraphraser.ui

import android.os.Build

data class OemSetupGuidance(val vendorLabel: String, val steps: List<String>)

object OemSetupGuide {

    fun forCurrentDevice(): OemSetupGuidance? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.containsAny("xiaomi", "redmi", "poco") -> OemSetupGuidance(
                vendorLabel = "Xiaomi / Redmi / POCO (MIUI, HyperOS)",
                steps = listOf(
                    "Settings > Apps > Manage apps > Silvertongue > Other permissions > turn on BOTH \"Display pop-up windows\" and \"Display pop-up windows while running in background\". The second one is the trap: without it the bubble never appears even though the permission row above says Granted.",
                    "Settings > Apps > Manage apps > Silvertongue > Autostart > on.",
                    "Settings > Battery & performance > App battery saver > Silvertongue > No restrictions.",
                    "Open Recents, swipe down on the Silvertongue card, tap the padlock to lock it in memory.",
                    "Accessibility on MIUI lives at Settings > Additional settings > Accessibility > Downloaded apps, not the top-level Settings > Accessibility.",
                    "Re-check the accessibility toggle after every reboot. MIUI turns it back off silently."
                )
            )
            manufacturer.containsAny("oppo", "realme") -> OemSetupGuidance(
                vendorLabel = "OPPO / realme (ColorOS)",
                steps = listOf(
                    "Settings > Apps > Silvertongue > Allow floating windows.",
                    "Settings > Apps > Auto Launch (or Startup Manager) > enable Silvertongue.",
                    "Settings > Battery > Power Saving > Silvertongue > Allow background activity.",
                    "Re-check the accessibility toggle after a reboot; ColorOS often turns it back off."
                )
            )
            manufacturer.containsAny("vivo", "iqoo") -> OemSetupGuidance(
                vendorLabel = "vivo / iQOO (Funtouch OS or OriginOS)",
                steps = listOf(
                    "Settings > More settings > Permission manager > Silvertongue > Floating windows > allow.",
                    "i Manager > App manager > Autostart manager > enable Silvertongue.",
                    "Settings > Battery > High background power consumption > allow Silvertongue.",
                    "Re-check the accessibility toggle after a reboot."
                )
            )
            manufacturer.containsAny("oneplus") -> OemSetupGuidance(
                vendorLabel = "OnePlus (OxygenOS)",
                steps = listOf(
                    "Settings > Apps > Silvertongue > Display over other apps > allow.",
                    "Settings > Battery > Battery optimisation > Silvertongue > Don't optimise.",
                    "Settings > Battery > More settings > disable \"Deep optimisation\" and \"Sleep standby optimisation\".",
                    "Lock Silvertongue in Recents."
                )
            )
            manufacturer.containsAny("samsung") -> OemSetupGuidance(
                vendorLabel = "Samsung (One UI)",
                steps = listOf(
                    "Settings > Battery > Background usage limits > make sure Silvertongue is not in \"Sleeping apps\" or \"Deep sleeping apps\".",
                    "Settings > Apps > Silvertongue > Battery > Unrestricted."
                )
            )
            else -> null
        }
    }

    private fun String.containsAny(vararg needles: String) = needles.any { contains(it) }
}
