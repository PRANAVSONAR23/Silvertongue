package com.silvertongue.paraphraser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.silvertongue.paraphraser.paraphrase.ProviderId
import com.silvertongue.paraphraser.ui.theme.SilvertongueTheme

@Composable
fun MainScreen(
    state: MainUiState,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRepairService: () -> Unit,
    onProviderSelected: (ProviderId) -> Unit,
    onKeyOverrideChanged: (ProviderId, String) -> Unit,
    onTestInputChanged: (String) -> Unit,
    onParaphrase: () -> Unit
) {
    SilvertongueTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Silvertongue",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )

                SetupSection(
                    state = state,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onRepairService = onRepairService
                )

                OemSection()

                ProviderSection(
                    state = state,
                    onProviderSelected = onProviderSelected,
                    onKeyOverrideChanged = onKeyOverrideChanged
                )

                TestSection(
                    state = state,
                    onTestInputChanged = onTestInputChanged,
                    onParaphrase = onParaphrase
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun SetupSection(
    state: MainUiState,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRepairService: () -> Unit
) {
    val isServiceHealthy = state.isAccessibilityEnabled && state.isServiceRunning

    SectionCard(title = "Permissions") {
        PermissionRow(
            label = "Accessibility service",
            isGranted = isServiceHealthy,
            statusLabel = when {
                isServiceHealthy -> "Running"
                state.isAccessibilityEnabled -> "Switched on but not running"
                else -> "Not granted"
            },
            buttonLabel = "Open accessibility settings",
            onClick = onOpenAccessibilitySettings
        )
        if (state.isAccessibilityEnabled && !state.isServiceRunning) {
            Text(
                text = "Settings shows this service as on, but it is not actually running — Android usually calls this \"service is malfunctioning\". Restarting it below fixes that.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!isServiceHealthy) {
            RepairBlock(state = state, onRepairService = onRepairService)
        }
        PermissionRow(
            label = "Display over other apps",
            isGranted = state.canDrawOverlays,
            statusLabel = if (state.canDrawOverlays) "Granted" else "Not granted",
            buttonLabel = "Open overlay settings",
            onClick = onOpenOverlaySettings
        )
        if (state.canDrawOverlays && state.hasVendorOverlayGate) {
            Text(
                text = "Android reports this as granted, but your ROM keeps its own \"Display pop-up windows\" and \"while running in background\" switches that this check cannot see. If the bubble never appears, open the button above and confirm both are on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RepairBlock(state: MainUiState, onRepairService: () -> Unit) {
    if (state.canSelfRepair) {
        Button(
            onClick = onRepairService,
            enabled = !state.isRepairing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isRepairing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Turn the service back on")
            }
        }
        Text(
            text = "Clearing app data, updating the app or rebooting switches this service off. This button turns it back on without a trip to Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Text(
            text = "One-time setup to enable the repair button: connect the phone and run this on your computer, then reopen this screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = AccessibilityServiceRepair.GRANT_COMMAND,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    isGranted: Boolean,
    statusLabel: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!isGranted) {
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun OemSection() {
    val guidance = OemSetupGuide.forCurrentDevice() ?: return
    SectionCard(title = "Extra steps for ${guidance.vendorLabel}") {
        Text(
            text = "This ROM needs more than the two permissions above, or the overlay silently never appears.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        guidance.steps.forEachIndexed { index, step ->
            Text(
                text = "${index + 1}. $step",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ProviderSection(
    state: MainUiState,
    onProviderSelected: (ProviderId) -> Unit,
    onKeyOverrideChanged: (ProviderId, String) -> Unit
) {
    SectionCard(title = "Provider") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderId.entries.forEach { provider ->
                FilterChip(
                    selected = state.activeProvider == provider,
                    onClick = { onProviderSelected(provider) },
                    label = { Text(provider.displayName) }
                )
            }
        }

        ProviderId.entries.forEach { provider ->
            val isConfigured = provider in state.configuredProviders
            OutlinedTextField(
                value = state.keyOverrides[provider].orEmpty(),
                onValueChange = { onKeyOverrideChanged(provider, it) },
                label = { Text("${provider.displayName} API key") },
                supportingText = {
                    Text(
                        if (isConfigured) {
                            "Key configured. Leave blank to use the local.properties key."
                        } else {
                            "No key found in local.properties. Paste one here."
                        }
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TestSection(
    state: MainUiState,
    onTestInputChanged: (String) -> Unit,
    onParaphrase: () -> Unit
) {
    SectionCard(title = "Test without WhatsApp") {
        OutlinedTextField(
            value = state.testInput,
            onValueChange = onTestInputChanged,
            label = { Text("Raw message") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onParaphrase,
            enabled = !state.isParaphrasing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isParaphrasing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Paraphrase")
            }
        }

        state.errorMessage?.let { message ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.suggestions.forEach { suggestion ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
