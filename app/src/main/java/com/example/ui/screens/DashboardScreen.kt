package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBluetooth
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BluetoothDashboardState
import com.example.model.ConnectionStatus
import com.example.model.HardwareCapabilityState
import com.example.ui.components.BluetoothPulseRings
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusTone
import com.example.ui.components.rememberAnimatedBatteryLevel
import com.example.ui.theme.AmberProtocol
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.OnePlusRed
import com.example.ui.theme.OnePlusRedGlow
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun DashboardScreen(
    bluetoothState: BluetoothDashboardState,
    onRequestPermissions: () -> Unit,
    onScanOrRefresh: () -> Unit,
    onStopScan: () -> Unit,
    onOpenSystemBluetooth: () -> Unit,
    onNavigateToAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val combinedBatteryAnimated = rememberAnimatedBatteryLevel(bluetoothState.battery.combinedPercent)
    val leftBatteryAnimated = rememberAnimatedBatteryLevel(bluetoothState.battery.leftPercent)
    val rightBatteryAnimated = rememberAnimatedBatteryLevel(bluetoothState.battery.rightPercent)
    val caseBatteryAnimated = rememberAnimatedBatteryLevel(bluetoothState.battery.casePercent)

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
            // Top Brand Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "NORDBUDS",
                            style = MaterialTheme.typography.headlineLarge,
                            color = TextPrimary,
                        )
                        Text(
                            text = stringResource(R.string.brand_tagline),
                            style = MaterialTheme.typography.labelSmall,
                            color = OnePlusRedGlow,
                        )
                    }
                    ConnectionBadge(status = bluetoothState.connectionStatus)
                }
            }

            // Permission Banner if missing
            if (bluetoothState.connectionStatus == ConnectionStatus.NO_PERMISSION) {
                item {
                    GlassCard(highlighted = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Bluetooth,
                                contentDescription = null,
                                tint = OnePlusRedGlow,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Nearby Bluetooth Permission Needed",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = "Grant BLUETOOTH_CONNECT & BLUETOOTH_SCAN to read live Nord Buds connection, codec, and battery telemetry.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = OnePlusRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_bt_permission_button"),
                        ) {
                            Text("Grant Bluetooth Permissions")
                        }
                    }
                }
            }

            // Hero Earbuds Visualization Card
            item {
                GlassCard(
                    highlighted = bluetoothState.connectionStatus == ConnectionStatus.CONNECTED,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_nord_buds_hero),
                            contentDescription = stringResource(R.string.cd_earbuds_hero),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.78f),
                                        ),
                                    ),
                                ),
                        )
                        BluetoothPulseRings(
                            isScanning = bluetoothState.connectionStatus == ConnectionStatus.SCANNING,
                            isConnected = bluetoothState.connectionStatus == ConnectionStatus.CONNECTED,
                            isCharging = bluetoothState.battery.isLeftCharging || bluetoothState.battery.isRightCharging,
                            modifier = Modifier.size(160.dp),
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bluetoothState.activeDevice?.name
                                        ?: if (bluetoothState.connectionStatus == ConnectionStatus.SCANNING) {
                                            "Scanning Nearby BLE Audio..."
                                        } else {
                                            "No Earbuds Connected"
                                        },
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = when {
                                        bluetoothState.activeDevice != null && bluetoothState.codec.isHardwareVerified ->
                                            "Codec: ${bluetoothState.codec.codecName} • ${bluetoothState.codec.sampleRateLabel} • ${bluetoothState.codec.bitsPerSampleLabel}"
                                        bluetoothState.activeDevice != null ->
                                            "Connected via ${if (bluetoothState.activeDevice.isA2dpConnected) "A2DP Audio" else "HFP"}"
                                        else -> "Pair OnePlus Nord Buds 3 Pro in Vivo X300 Bluetooth settings"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (bluetoothState.isAudioPlaybackActive) {
                                StatusPill(
                                    label = "LIVE AUDIO",
                                    tone = StatusTone.ACTIVE,
                                    icon = Icons.Filled.GraphicEq,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                if (bluetoothState.connectionStatus == ConnectionStatus.SCANNING) {
                                    onStopScan()
                                } else {
                                    onScanOrRefresh()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OnePlusRed,
                                contentColor = TextPrimary,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scan_refresh_button"),
                        ) {
                            Icon(
                                imageVector = if (bluetoothState.connectionStatus == ConnectionStatus.SCANNING) {
                                    Icons.AutoMirrored.Filled.BluetoothSearching
                                } else {
                                    Icons.Filled.Refresh
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (bluetoothState.connectionStatus == ConnectionStatus.SCANNING) {
                                    "Stop Scan"
                                } else {
                                    "Scan / Refresh"
                                },
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenSystemBluetooth,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_bt_settings_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SettingsBluetooth,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("BT Settings", color = TextPrimary)
                        }
                    }

                    AnimatedVisibility(
                        visible = bluetoothState.lastError != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Text(
                            text = bluetoothState.lastError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnePlusRedGlow,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }

            // Battery Telemetry Section
            item {
                GlassCard {
                    SectionHeader(
                        title = "Battery Telemetry",
                        subtitle = "Source: ${bluetoothState.battery.sourceLabel}",
                        trailing = {
                            StatusPill(
                                label = if (combinedBatteryAnimated != null) "LIVE READOUT" else "NOT EXPOSED",
                                tone = if (combinedBatteryAnimated != null) StatusTone.ACTIVE else StatusTone.NEUTRAL,
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Combined Bluetooth Battery Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ObsidianElevated)
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (bluetoothState.battery.isLeftCharging || bluetoothState.battery.isRightCharging) {
                                    Icons.Filled.BatteryChargingFull
                                } else {
                                    Icons.Filled.BatteryStd
                                },
                                contentDescription = null,
                                tint = if (combinedBatteryAnimated != null) EmeraldActive else TextTertiary,
                                modifier = Modifier.size(26.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Combined Headset Battery",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = if (combinedBatteryAnimated != null) {
                                        "Reported via standard Android Bluetooth stack"
                                    } else {
                                        "Connect earbuds to query Bluetooth battery"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                            }
                        }
                        Text(
                            text = combinedBatteryAnimated?.let { "$it%" } ?: "—",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (combinedBatteryAnimated != null) EmeraldActive else TextTertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("combined_battery_text"),
                        )
                    }

                    if (combinedBatteryAnimated != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { combinedBatteryAnimated / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = if (combinedBatteryAnimated <= 20) OnePlusRed else EmeraldActive,
                            trackColor = ObsidianElevated,
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Individual Left / Right / Case Cards (strictly honest)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SplitBatteryTile(
                            label = "LEFT BUD",
                            percent = leftBatteryAnimated,
                            isCharging = bluetoothState.battery.isLeftCharging,
                            modifier = Modifier.weight(1f),
                        )
                        SplitBatteryTile(
                            label = "RIGHT BUD",
                            percent = rightBatteryAnimated,
                            isCharging = bluetoothState.battery.isRightCharging,
                            modifier = Modifier.weight(1f),
                        )
                        SplitBatteryTile(
                            label = "CASE",
                            percent = caseBatteryAnimated,
                            isCharging = bluetoothState.battery.isCaseCharging,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (leftBatteryAnimated == null && rightBatteryAnimated == null && caseBatteryAnimated == null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x1AFFB020))
                                .padding(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = AmberProtocol,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Split L/R/Case percentages appear only when Google Fast Pair (0xFE2C) broadcasts them. Nord Buds 3 Pro otherwise expose combined battery over public Bluetooth.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }

            // Quick Action to Audio & EQ
            item {
                GlassCard(
                    onClick = onNavigateToAudio,
                    modifier = Modifier.testTag("quick_audio_card"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(OnePlusRed.copy(alpha = 0.18f))
                                    .border(1.dp, OnePlusRed.copy(alpha = 0.45f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Equalizer,
                                    contentDescription = null,
                                    tint = OnePlusRedGlow,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "5-Band Equalizer & Bass Boost",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = "Tune real Android AudioEffect DSP & custom profiles",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                            }
                        }
                        StatusPill(label = "OPEN", tone = StatusTone.CRIMSON)
                    }
                }
            }

            // Device Information & Active Codec
            item {
                GlassCard {
                    SectionHeader(
                        title = "Device & Codec Telemetry",
                        subtitle = "Verified hardware properties on Vivo X300",
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    InfoRow("Active Device", bluetoothState.activeDevice?.name ?: "Not connected")
                    InfoRow("Audio Codec", if (bluetoothState.codec.isHardwareVerified) bluetoothState.codec.codecName else "Unavailable")
                    InfoRow("Sample Rate", bluetoothState.codec.sampleRateLabel)
                    InfoRow("Bit Depth / Mode", "${bluetoothState.codec.bitsPerSampleLabel} • ${bluetoothState.codec.channelModeLabel}")
                    InfoRow("Manufacturer (GATT)", bluetoothState.manufacturerName ?: "Not exposed over BLE 0x180A")
                    InfoRow("Firmware Rev (GATT)", bluetoothState.firmwareRevision ?: "Not exposed over BLE 0x180A")
                    InfoRow(
                        "Bonded Audio Devices",
                        if (bluetoothState.bondedAudioDevices.isEmpty()) "None found" else bluetoothState.bondedAudioDevices.joinToString { it.name },
                    )
                    if (!bluetoothState.activeDevice?.uuids.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Discovered Profiles & GATT Services:",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        bluetoothState.activeDevice?.uuids?.take(6)?.forEach { uuidLabel ->
                            Text(
                                text = "• $uuidLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                            )
                        }
                    }
                }
            }

            // Transparent Hardware Protocol Matrix
            item {
                GlassCard {
                    SectionHeader(
                        title = "Hardware Protocol Transparency",
                        subtitle = "Honest breakdown of public Android APIs vs. OEM-locked protocols",
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    bluetoothState.capabilities.forEachIndexed { idx, cap ->
                        if (idx > 0) {
                            HorizontalDivider(
                                color = ObsidianBorder,
                                modifier = Modifier.padding(vertical = 10.dp),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cap.featureName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = cap.protocolName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            when (cap.status) {
                                HardwareCapabilityState.SUPPORTED_LIVE -> StatusPill("LIVE", StatusTone.ACTIVE, icon = Icons.Filled.CheckCircle)
                                HardwareCapabilityState.SUPPORTED_SYSTEM_AUDIO -> StatusPill("DSP ACTIVE", StatusTone.CRIMSON, icon = Icons.Filled.Equalizer)
                                HardwareCapabilityState.UNSUPPORTED_PROPRIETARY -> StatusPill("OEM LOCKED", StatusTone.WARNING, icon = Icons.Filled.Lock)
                                HardwareCapabilityState.UNAVAILABLE_NOT_EXPOSED -> StatusPill("WAITING", StatusTone.NEUTRAL)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = cap.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ConnectionBadge(status: ConnectionStatus) {
    when (status) {
        ConnectionStatus.CONNECTED -> StatusPill("CONNECTED", StatusTone.ACTIVE, icon = Icons.Filled.BluetoothConnected)
        ConnectionStatus.SCANNING -> StatusPill("SCANNING", StatusTone.CRIMSON, icon = Icons.AutoMirrored.Filled.BluetoothSearching)
        ConnectionStatus.BLUETOOTH_OFF -> StatusPill("BT OFF", StatusTone.WARNING, icon = Icons.Filled.BluetoothDisabled)
        ConnectionStatus.NO_PERMISSION -> StatusPill("PERMISSION", StatusTone.WARNING, icon = Icons.Filled.WarningAmber)
        ConnectionStatus.DISCONNECTED -> StatusPill("STANDBY", StatusTone.NEUTRAL, icon = Icons.Filled.Headphones)
    }
}

@Composable
private fun SplitBatteryTile(
    label: String,
    percent: Int?,
    isCharging: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianElevated)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = percent?.let { "$it%" } ?: "—",
            style = MaterialTheme.typography.titleLarge,
            color = when {
                percent == null -> TextTertiary
                isCharging -> EmeraldActive
                else -> TextPrimary
            },
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = when {
                isCharging -> "Charging"
                percent != null -> "Fast Pair"
                else -> "Not Exposed"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isCharging) EmeraldActive else TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            modifier = Modifier.weight(0.58f),
        )
    }
}
