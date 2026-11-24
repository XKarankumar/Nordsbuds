package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoiseAware
import androidx.compose.material.icons.filled.NoiseControlOff
import androidx.compose.material.icons.filled.SettingsBluetooth
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AncPreviewMode
import com.example.model.AudioControlState
import com.example.model.SoundProfile
import com.example.ui.components.EqualizerAcousticWave
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusTone
import com.example.ui.theme.AmberProtocol
import com.example.ui.theme.CrimsonBorder
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.OnePlusRed
import com.example.ui.theme.OnePlusRedContainer
import com.example.ui.theme.OnePlusRedGlow
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioControlsScreen(
    audioState: AudioControlState,
    ancMode: AncPreviewMode,
    isAudioPlaybackActive: Boolean,
    onSelectAncMode: (AncPreviewMode) -> Unit,
    onBandChanged: (Int, Float) -> Unit,
    onBassBoostChanged: (Int) -> Unit,
    onApplyProfile: (SoundProfile) -> Unit,
    onSaveCustomProfile: (String, String) -> Unit,
    onDeleteCustomProfile: (String) -> Unit,
    onOpenSystemBluetooth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSaveDialog by remember { mutableStateOf(false) }

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
                        text = "AUDIO & DSP",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Hardware AudioEffect Equalizer, Bass Boost & Acoustic Protocols",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            // ANC / Transparency / Normal Mode Card (Honest Protocol Boundary)
            item {
                GlassCard {
                    SectionHeader(
                        title = "Noise Control Modes",
                        subtitle = "ANC • Transparency • Normal",
                        trailing = {
                            StatusPill(
                                label = "OEM LOCKED",
                                tone = StatusTone.WARNING,
                                icon = Icons.Filled.Lock,
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AncPreviewMode.entries.forEach { mode ->
                            val selected = mode == ancMode
                            val bgColor by animateColorAsState(
                                targetValue = if (selected) OnePlusRedContainer else ObsidianElevated,
                                animationSpec = spring(),
                                label = "anc_bg",
                            )
                            val borderColor by animateColorAsState(
                                targetValue = if (selected) CrimsonBorder else ObsidianBorder,
                                animationSpec = spring(),
                                label = "anc_border",
                            )
                            val icon = when (mode) {
                                AncPreviewMode.ANC -> Icons.Filled.NoiseAware
                                AncPreviewMode.TRANSPARENCY -> Icons.Filled.Hearing
                                AncPreviewMode.NORMAL -> Icons.Filled.NoiseControlOff
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(bgColor)
                                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                                    .clickable { onSelectAncMode(mode) }
                                    .padding(12.dp)
                                    .testTag("anc_mode_${mode.name.lowercase()}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = mode.title,
                                    tint = if (selected) OnePlusRedGlow else TextSecondary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) TextPrimary else TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${ancMode.title}: ${ancMode.description}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Protocol notice: OnePlus Nord Buds 3 Pro ANC switching uses proprietary HeyMelody vendor RFCOMM commands not exposed in public Android Bluetooth APIs. Use System Bluetooth Settings or HeyMelody to switch hardware ANC.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AmberProtocol,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenSystemBluetooth,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SettingsBluetooth,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Vivo System Bluetooth Settings", color = TextPrimary)
                    }
                }
            }

            // Real 5+ Band Hardware Equalizer
            item {
                GlassCard(highlighted = audioState.isEqSupported) {
                    SectionHeader(
                        title = "${audioState.bands.size.coerceAtLeast(5)}-Band Equalizer",
                        subtitle = audioState.eqStatusMessage,
                        trailing = {
                            StatusPill(
                                label = if (audioState.isEqSupported) "HAL LIVE" else "UNAVAILABLE",
                                tone = if (audioState.isEqSupported) StatusTone.ACTIVE else StatusTone.WARNING,
                                icon = Icons.Filled.Equalizer,
                            )
                        },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    EqualizerAcousticWave(
                        bandNormalizedLevels = audioState.bands.map { it.normalizedLevel },
                        isPlaybackActive = isAudioPlaybackActive && audioState.isEqSupported,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(82.dp),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (audioState.bands.isEmpty()) {
                        Text(
                            text = "This device's AudioEffect HAL did not expose global Equalizer bands on session 0.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    } else {
                        audioState.bands.forEach { band ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = band.formattedFrequency,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextPrimary,
                                    )
                                    Text(
                                        text = band.formattedDb,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = OnePlusRedGlow,
                                    )
                                }
                                Slider(
                                    value = band.normalizedLevel,
                                    onValueChange = { onBandChanged(band.index, it) },
                                    colors = SliderDefaults.colors(
                                        thumbColor = OnePlusRedGlow,
                                        activeTrackColor = OnePlusRed,
                                        inactiveTrackColor = ObsidianElevated,
                                    ),
                                    modifier = Modifier.testTag("eq_band_slider_${band.index}"),
                                )
                            }
                        }
                    }
                }
            }

            // Real Bass Boost Control
            item {
                GlassCard {
                    SectionHeader(
                        title = "Hardware Bass Boost",
                        subtitle = if (audioState.isBassBoostSupported) {
                            "android.media.audiofx.BassBoost active (${audioState.bassBoostStrength / 10}%)"
                        } else {
                            "BassBoost strength unsupported by current AudioEffect HAL"
                        },
                        trailing = {
                            StatusPill(
                                label = if (audioState.isBassBoostSupported) "${audioState.bassBoostStrength / 10}%" else "N/A",
                                tone = if (audioState.isBassBoostSupported) StatusTone.CRIMSON else StatusTone.NEUTRAL,
                                icon = Icons.Filled.GraphicEq,
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = audioState.bassBoostStrength / 1000f,
                        onValueChange = { onBassBoostChanged((it * 1000f).toInt()) },
                        enabled = audioState.isBassBoostSupported,
                        colors = SliderDefaults.colors(
                            thumbColor = OnePlusRedGlow,
                            activeTrackColor = OnePlusRed,
                            inactiveTrackColor = ObsidianElevated,
                        ),
                        modifier = Modifier.testTag("bass_boost_slider"),
                    )
                }
            }

            // Sound Profiles (Built-in + Custom Saved)
            item {
                GlassCard {
                    SectionHeader(
                        title = "Sound Profiles",
                        subtitle = "Apply calibrated DSP presets or save your own",
                        trailing = {
                            OutlinedButton(
                                onClick = { showSaveDialog = true },
                                enabled = audioState.isEqSupported,
                                modifier = Modifier.testTag("save_profile_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = OnePlusRedGlow,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save", color = TextPrimary)
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        audioState.profiles.forEach { profile ->
                            val isSelected = profile.id == audioState.activeProfileId
                            FilterChip(
                                selected = isSelected,
                                onClick = { onApplyProfile(profile) },
                                label = {
                                    Text(
                                        text = profile.name,
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                    )
                                },
                                trailingIcon = if (!profile.isBuiltIn) {
                                    {
                                        IconButton(
                                            onClick = { onDeleteCustomProfile(profile.id) },
                                            modifier = Modifier.size(20.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = "Delete ${profile.name}",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(15.dp),
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OnePlusRedContainer,
                                    containerColor = ObsidianElevated,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = ObsidianBorder,
                                    selectedBorderColor = CrimsonBorder,
                                ),
                                modifier = Modifier.testTag("profile_chip_${profile.id}"),
                            )
                        }
                    }

                    audioState.profiles.firstOrNull { it.id == audioState.activeProfileId }?.let { active ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = active.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }
            }

            // Touch Gesture Configuration Status (Read-Only Hardware Reference)
            item {
                GlassCard {
                    SectionHeader(
                        title = "Touch Gesture Map",
                        subtitle = "OnePlus Nord Buds 3 Pro default hardware bindings",
                        trailing = {
                            StatusPill(
                                label = "READ-ONLY",
                                tone = StatusTone.WARNING,
                                icon = Icons.Filled.TouchApp,
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    GestureRow("Single Tap (L / R)", "Play / Pause media")
                    HorizontalDivider(color = ObsidianBorder, modifier = Modifier.padding(vertical = 6.dp))
                    GestureRow("Double Tap (L / R)", "Next track / Answer or end call")
                    HorizontalDivider(color = ObsidianBorder, modifier = Modifier.padding(vertical = 6.dp))
                    GestureRow("Triple Tap (L / R)", "Previous track / Voice assistant")
                    HorizontalDivider(color = ObsidianBorder, modifier = Modifier.padding(vertical = 6.dp))
                    GestureRow("Touch & Hold (1s)", "Cycle ANC / Transparency")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Touch remapping writes directly to earbud EEPROM via proprietary HeyMelody BLE/RFCOMM packets and is intentionally locked here rather than simulated.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showSaveDialog) {
        SaveProfileDialog(
            onDismiss = { showSaveDialog = false },
            onConfirm = { name, desc ->
                onSaveCustomProfile(name, desc)
                showSaveDialog = false
            },
        )
    }
}

@Composable
private fun GestureRow(gesture: String, binding: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = gesture, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(text = binding, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun SaveProfileDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14161D),
        title = {
            Text("Save Custom Sound Profile", color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, description) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = OnePlusRed),
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}
