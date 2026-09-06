package com.silvertongue.paraphraser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.silvertongue.paraphraser.ui.AccessibilityServiceRepair
import com.silvertongue.paraphraser.ui.MainScreen
import com.silvertongue.paraphraser.ui.MainViewModel
import com.silvertongue.paraphraser.ui.PermissionStatus
import com.silvertongue.paraphraser.ui.SettingsLauncher
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            MainScreen(
                state = state,
                onOpenAccessibilitySettings = {
                    SettingsLauncher.openAccessibilitySettings(this)
                },
                onOpenOverlaySettings = { SettingsLauncher.openOverlayPermission(this) },
                onRepairService = {
                    lifecycleScope.launch {
                        viewModel.setRepairing(true)
                        AccessibilityServiceRepair.repair(this@MainActivity)
                        viewModel.setRepairing(false)
                        refreshPermissionState()
                    }
                },
                onProviderSelected = viewModel::onProviderSelected,
                onKeyOverrideChanged = viewModel::onKeyOverrideChanged,
                onTestInputChanged = viewModel::onTestInputChanged,
                onParaphrase = viewModel::paraphraseTestInput
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    private fun refreshPermissionState() {
        viewModel.refreshPermissions(
            isAccessibilityEnabled = PermissionStatus.isAccessibilityServiceEnabled(this),
            isServiceRunning = PermissionStatus.isAccessibilityServiceRunning(),
            canDrawOverlays = PermissionStatus.canDrawOverlays(this),
            hasVendorOverlayGate = PermissionStatus.isVendorWithExtraOverlayGate(),
            canSelfRepair = AccessibilityServiceRepair.isAvailable(this)
        )
    }
}
