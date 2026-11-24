package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CrashDiagnosticEntry
import com.example.model.PermissionStatusSnapshot
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusTone
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.OnePlusRed
import com.example.ui.theme.OnePlusRedGlow
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun DiagnosticsScreen(
    permissions: PermissionStatusSnapshot,
    diagnosticsConsent: Boolean,
    diagnosticEntries: List<CrashDiagnosticEntry>,
    onRequestPermissions: () -> Unit,
    onToggleDiagnosticsConsent: (Boolean) -> Unit,
    onClearDiagnostics: () -> Unit,
    onRecordTestSnapshot: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onSubmitBugReport: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var bugSummary by remember { mutableStateOf("") }
    var bugRepro by remember { mutableStateOf("") }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column {
                    Text(
                        text = "SYSTEM & DIAGNOSTICS",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Permissions, Vivo X300 Battery Optimization & Consented Crash Logs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            // Permissions Center
            item {
                GlassCard {
                    SectionHeader(
                        title = "Least-Privilege Permissions",
                        subtitle = "Zero camera, microphone, contacts, or background location requests",
                        trailing = {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = OnePlusRedGlow,
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PermissionRow("BLUETOOTH_CONNECT", permissions.bluetoothConnectGranted)
                    PermissionRow("BLUETOOTH_SCAN (neverForLocation)", permissions.bluetoothScanGranted)
                    PermissionRow("POST_NOTIFICATIONS", permissions.notificationsGranted)
                    if (permissions.requiresLegacyLocationForBle) {
                        PermissionRow("ACCESS_FINE_LOCATION (Android <= 11)", permissions.locationGrantedIfRequired)
                    } else {
                        PermissionRow("Location Exemption (Android 12+)", true)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = OnePlusRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("request_permissions_button"),
                    ) {
                        Text("Verify / Request Runtime Permissions")
                    }
                }
            }

            // Battery Optimization & Vivo X300 Architecture
            item {
                GlassCard {
                    SectionHeader(
                        title = "Battery & Lifecycle Guardrails",
                        subtitle = "Optimized for Vivo X300 OriginOS power management",
                        trailing = {
                            StatusPill(
                                label = "ACTIVE",
                                tone = StatusTone.ACTIVE,
                                icon = Icons.Filled.BatterySaver,
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• Zero persistent background services or wake-locks.\n" +
                            "• Event-driven Bluetooth profile & broadcast receivers.\n" +
                            "• Bounded 10-second low-power BLE scans only on user action.\n" +
                            "• GPU animations automatically pause when screen turns off or app is backgrounded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            // Consent-Gated Crash Diagnostics
            item {
                GlassCard(highlighted = diagnosticsConsent) {
                    SectionHeader(
                        title = "Crash Diagnostics",
                        subtitle = "Encrypted locally with AndroidKeyStore AES-256-GCM",
                        trailing = {
                            StatusPill(
                                label = if (diagnosticsConsent) "OPTED IN" else "DISABLED",
                                tone = if (diagnosticsConsent) StatusTone.ACTIVE else StatusTone.NEUTRAL,
                                icon = Icons.Filled.Lock,
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Capture Crash Diagnostics Locally",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                            Text(
                                text = "Requires explicit consent. Bluetooth MAC addresses are automatically scrubbed before storage.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = diagnosticsConsent,
                            onCheckedChange = onToggleDiagnosticsConsent,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextPrimary,
                                checkedTrackColor = OnePlusRed,
                            ),
                            modifier = Modifier.testTag("diagnostics_consent_switch"),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onExportDiagnostics,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_diagnostics_button"),
                        ) {
                            Icon(Icons.Filled.IosShare, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export JSON", color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = onClearDiagnostics,
                            enabled = diagnosticEntries.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear (${diagnosticEntries.size})", color = TextPrimary)
                        }
                    }

                    if (diagnosticsConsent) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onRecordTestSnapshot,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Record Non-Fatal Test Snapshot", color = TextSecondary)
                        }
                    }

                    if (diagnosticEntries.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        diagnosticEntries.take(3).forEach { entry ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ObsidianElevated)
                                    .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                            ) {
                                Text(
                                    text = "${entry.timestampIso} • ${entry.deviceModel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary,
                                )
                                Text(
                                    text = entry.exceptionSummary,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = OnePlusRedGlow,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // Bug Reporting Card
            item {
                GlassCard {
                    SectionHeader(
                        title = "Submit Bug Report",
                        subtitle = "Pre-fills device info & consented diagnostics into an issue report",
                        trailing = {
                            Icon(
                                imageVector = Icons.Filled.BugReport,
                                contentDescription = null,
                                tint = OnePlusRedGlow,
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = bugSummary,
                        onValueChange = { bugSummary = it },
                        label = { Text("Issue Summary") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bug_summary_input"),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = bugRepro,
                        onValueChange = { bugRepro = it },
                        label = { Text("Steps to Reproduce") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bug_repro_input"),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onSubmitBugReport(bugSummary, bugRepro) },
                        colors = ButtonDefaults.buttonColors(containerColor = OnePlusRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_bug_report_button"),
                    ) {
                        Text("Create Bug Report")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun PermissionRow(name: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = if (granted) EmeraldActive else OnePlusRedGlow,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (granted) "Granted" else "Needed",
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) EmeraldActive else OnePlusRedGlow,
            )
        }
    }
    HorizontalDivider(color = ObsidianBorder)
}
