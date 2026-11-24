package com.example.model

import com.squareup.moshi.JsonClass

enum class ConnectionStatus {
    NO_PERMISSION,
    BLUETOOTH_OFF,
    DISCONNECTED,
    SCANNING,
    CONNECTED,
}

enum class HardwareCapabilityState {
    SUPPORTED_LIVE,
    SUPPORTED_SYSTEM_AUDIO,
    UNSUPPORTED_PROPRIETARY,
    UNAVAILABLE_NOT_EXPOSED,
}

enum class AncPreviewMode(
    val title: String,
    val description: String,
) {
    ANC("Noise Cancellation", "49dB adaptive hybrid ANC"),
    TRANSPARENCY("Transparency", "Ambient voice pass-through"),
    NORMAL("Off / Normal", "Passive acoustic seal"),
}

data class EarbudsBatteryTelemetry(
    val combinedPercent: Int? = null,
    val leftPercent: Int? = null,
    val rightPercent: Int? = null,
    val casePercent: Int? = null,
    val isLeftCharging: Boolean = false,
    val isRightCharging: Boolean = false,
    val isCaseCharging: Boolean = false,
    val sourceLabel: String = "Unavailable",
    val lastUpdatedMillis: Long = 0L,
)

data class AudioCodecTelemetry(
    val codecName: String = "Unavailable",
    val sampleRateLabel: String = "—",
    val bitsPerSampleLabel: String = "—",
    val channelModeLabel: String = "—",
    val isHardwareVerified: Boolean = false,
)

data class DiscoveredBluetoothDevice(
    val name: String,
    val address: String,
    val isBonded: Boolean,
    val isA2dpConnected: Boolean,
    val isHeadsetConnected: Boolean,
    val rssiDbm: Int? = null,
    val isOnePlusTarget: Boolean = false,
    val uuids: List<String> = emptyList(),
)

data class ProtocolCapabilityItem(
    val featureName: String,
    val protocolName: String,
    val status: HardwareCapabilityState,
    val detail: String,
)

data class BluetoothDashboardState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val activeDevice: DiscoveredBluetoothDevice? = null,
    val bondedAudioDevices: List<DiscoveredBluetoothDevice> = emptyList(),
    val nearbyScanResults: List<DiscoveredBluetoothDevice> = emptyList(),
    val battery: EarbudsBatteryTelemetry = EarbudsBatteryTelemetry(),
    val codec: AudioCodecTelemetry = AudioCodecTelemetry(),
    val firmwareRevision: String? = null,
    val hardwareRevision: String? = null,
    val manufacturerName: String? = null,
    val capabilities: List<ProtocolCapabilityItem> = emptyList(),
    val isBluetoothSupported: Boolean = true,
    val isBleSupported: Boolean = true,
    val lastError: String? = null,
    val isAudioPlaybackActive: Boolean = false,
)

data class EqBandState(
    val index: Int,
    val centerFreqHz: Int,
    val minLevelMb: Int,
    val maxLevelMb: Int,
    val currentLevelMb: Int,
) {
    val normalizedLevel: Float
        get() {
            val span = (maxLevelMb - minLevelMb).coerceAtLeast(1)
            return ((currentLevelMb - minLevelMb).toFloat() / span).coerceIn(0f, 1f)
        }

    val formattedFrequency: String
        get() = if (centerFreqHz >= 1000) {
            val khz = centerFreqHz / 1000f
            if (khz % 1f == 0f) "${khz.toInt()} kHz" else "${"%.1f".format(khz)} kHz"
        } else {
            "$centerFreqHz Hz"
        }

    val formattedDb: String
        get() {
            val db = currentLevelMb / 100f
            val prefix = if (db > 0f) "+" else ""
            return "$prefix${"%.1f".format(db)} dB"
        }
}

@JsonClass(generateAdapter = true)
data class SoundProfile(
    val id: String,
    val name: String,
    val description: String,
    val bandOffsetsNormalized: List<Float>,
    val bassBoostStrength: Int,
    val isBuiltIn: Boolean = false,
)

data class AudioControlState(
    val isEqSupported: Boolean = false,
    val eqStatusMessage: String = "Initializing audio engine...",
    val bands: List<EqBandState> = emptyList(),
    val isBassBoostSupported: Boolean = false,
    val bassBoostStrength: Int = 0,
    val activeProfileId: String = "balanced",
    val profiles: List<SoundProfile> = emptyList(),
    val hardwarePresetNames: List<String> = emptyList(),
    val selectedAncInspectionMode: AncPreviewMode = AncPreviewMode.ANC,
)

@JsonClass(generateAdapter = true)
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val releaseDate: String,
    val apkUrl: String,
    val sha256: String,
    val minAndroidSdk: Int = 24,
    val releaseNotes: List<String>,
    val isCritical: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class VersionHistoryEntry(
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val highlights: List<String>,
)

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : UpdateDownloadState
    data object VerifyingIntegrity : UpdateDownloadState
    data class VerifiedReady(val apkFilePath: String, val sha256Verified: String, val versionName: String) : UpdateDownloadState
    data class Failed(val reason: String) : UpdateDownloadState
}

@JsonClass(generateAdapter = true)
data class CrashDiagnosticEntry(
    val id: String,
    val timestampIso: String,
    val appVersion: String,
    val deviceModel: String,
    val androidRelease: String,
    val sdkInt: Int,
    val threadName: String,
    val exceptionSummary: String,
    val stackTracePreview: String,
)

data class PermissionStatusSnapshot(
    val bluetoothConnectGranted: Boolean,
    val bluetoothScanGranted: Boolean,
    val locationGrantedIfRequired: Boolean,
    val notificationsGranted: Boolean,
    val canRequestPackageInstalls: Boolean,
    val requiresLegacyLocationForBle: Boolean,
)
