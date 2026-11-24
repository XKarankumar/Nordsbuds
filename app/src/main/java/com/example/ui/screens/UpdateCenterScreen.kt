package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.model.PermissionStatusSnapshot
import com.example.model.UpdateDownloadState
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
import com.example.viewmodel.UpdateCenterUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UpdateCenterScreen(
    updateState: UpdateCenterUiState,
    permissions: PermissionStatusSnapshot,
    onUpdateManifestUrl: (String) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadVerifiedUpdate: () -> Unit,
    onInstallVerifiedApk: (String) -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var urlDraft by remember(updateState.manifestUrl) { mutableStateOf(updateState.manifestUrl) }

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
                        text = "UPDATE CENTER",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = "HTTPS Release Checks, SHA-256 Verification & User-Confirmed Install",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            // Current Version & Endpoint Card
            item {
                GlassCard(highlighted = updateState.availableUpdate != null) {
                    SectionHeader(
                        title = "NordBuds Premium v${updateState.currentVersionName}",
                        subtitle = "Build ${updateState.currentVersionCode} • Last check: ${formatTimestamp(updateState.lastCheckedMillis)}",
                        trailing = {
                            StatusPill(
                                label = "SHA-256",
                                tone = StatusTone.ACTIVE,
                                icon = Icons.Filled.VerifiedUser,
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = urlDraft,
                        onValueChange = {
                            urlDraft = it
                            onUpdateManifestUrl(it)
                        },
                        label = { Text("HTTPS GitHub Release / Manifest URL") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("update_url_input"),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onCheckForUpdates,
                        enabled = !updateState.isChecking,
                        colors = ButtonDefaults.buttonColors(containerColor = OnePlusRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("check_updates_button"),
                    ) {
                        if (updateState.isChecking) {
                            CircularProgressIndicator(
                                color = TextPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking HTTPS Endpoint...")
                        } else {
                            Icon(
                                imageVector = Icons.Filled.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check for New Version")
                        }
                    }

                    updateState.checkMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (updateState.availableUpdate != null) EmeraldActive else TextSecondary,
                        )
                    }
                }
            }

            // Available Update & Secure Download / Install Card
            updateState.availableUpdate?.let { manifest ->
                item {
                    GlassCard(highlighted = true) {
                        SectionHeader(
                            title = "Release v${manifest.versionName}",
                            subtitle = "Published ${manifest.releaseDate} • Min SDK ${manifest.minAndroidSdk}",
                            trailing = {
                                StatusPill(
                                    label = if (manifest.isCritical) "CRITICAL" else "AVAILABLE",
                                    tone = StatusTone.CRIMSON,
                                )
                            },
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Expected SHA-256: ${manifest.sha256.ifBlank { "Missing in manifest" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        manifest.releaseNotes.forEach { note ->
                            Text(
                                text = "• $note",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        when (val dl = updateState.downloadState) {
                            UpdateDownloadState.Idle -> {
                                Button(
                                    onClick = onDownloadVerifiedUpdate,
                                    colors = ButtonDefaults.buttonColors(containerColor = OnePlusRed),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("download_update_button"),
                                ) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download & Verify APK")
                                }
                            }
                            is UpdateDownloadState.Downloading -> {
                                LinearProgressIndicator(
                                    progress = { dl.progressPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape),
                                    color = OnePlusRedGlow,
                                    trackColor = ObsidianElevated,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Downloading ${dl.progressPercent}% (${dl.bytesDownloaded / 1024} KB)...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary,
                                )
                            }
                            UpdateDownloadState.VerifyingIntegrity -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        color = EmeraldActive,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Verifying SHA-256 digest & package signature...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = EmeraldActive,
                                    )
                                }
                            }
                            is UpdateDownloadState.VerifiedReady -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldActive, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SHA-256 & Package ID Verified",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = EmeraldActive,
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                if (!permissions.canRequestPackageInstalls) {
                                    OutlinedButton(
                                        onClick = onOpenUnknownSourcesSettings,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Filled.Security, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Allow App Installs in Android Settings", color = TextPrimary)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Button(
                                    onClick = { onInstallVerifiedApk(dl.apkFilePath) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldActive),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("install_verified_apk_button"),
                                ) {
                                    Icon(Icons.Filled.InstallMobile, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Install Verified v${dl.versionName} (User Confirmed)")
                                }
                            }
                            is UpdateDownloadState.Failed -> {
                                Text(
                                    text = dl.reason,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnePlusRedGlow,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = onDownloadVerifiedUpdate,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Retry Verified Download", color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            }

            // Version History & Release Notes
            item {
                SectionHeader(
                    title = "Version History",
                    subtitle = "Verified release notes & milestones",
                    trailing = {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = TextSecondary,
                        )
                    },
                )
            }

            items(updateState.versionHistory, key = { it.versionCode }) { entry ->
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "v${entry.versionName}",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                        )
                        StatusPill(label = entry.releaseDate, tone = StatusTone.NEUTRAL)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = ObsidianBorder)
                    Spacer(modifier = Modifier.height(8.dp))
                    entry.highlights.forEach { bullet ->
                        Text(
                            text = "• $bullet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    if (millis <= 0L) return "Never"
    val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
