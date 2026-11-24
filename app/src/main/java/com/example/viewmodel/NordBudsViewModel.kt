package com.example.viewmodel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.CrashDiagnosticsManager
import com.example.data.SecureStorageRepository
import com.example.data.UpdateCenterRepository
import com.example.data.UpdateCheckResult
import com.example.hardware.AudioEffectsController
import com.example.hardware.BluetoothEarbudsManager
import com.example.model.AncPreviewMode
import com.example.model.AudioControlState
import com.example.model.BluetoothDashboardState
import com.example.model.CrashDiagnosticEntry
import com.example.model.PermissionStatusSnapshot
import com.example.model.SoundProfile
import com.example.model.UpdateDownloadState
import com.example.model.UpdateManifest
import com.example.model.VersionHistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class UpdateCenterUiState(
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val manifestUrl: String = SecureStorageRepository.DEFAULT_GITHUB_RELEASE_URL,
    val lastCheckedMillis: Long = 0L,
    val isChecking: Boolean = false,
    val checkMessage: String? = null,
    val availableUpdate: UpdateManifest? = null,
    val downloadState: UpdateDownloadState = UpdateDownloadState.Idle,
    val versionHistory: List<VersionHistoryEntry> = emptyList(),
)

class NordBudsViewModel(
    private val appContext: Context,
    private val bluetoothManager: BluetoothEarbudsManager,
    private val audioEffectsController: AudioEffectsController,
    private val storageRepository: SecureStorageRepository,
    private val crashDiagnosticsManager: CrashDiagnosticsManager,
    private val updateCenterRepository: UpdateCenterRepository,
) : ViewModel() {

    val bluetoothState: StateFlow<BluetoothDashboardState> = bluetoothManager.state
    val audioState: StateFlow<AudioControlState> = audioEffectsController.state
    val diagnosticEntries: StateFlow<List<CrashDiagnosticEntry>> = crashDiagnosticsManager.entries

    val diagnosticsConsent: StateFlow<Boolean> = storageRepository.diagnosticsConsentFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _permissions = MutableStateFlow(readPermissionSnapshot())
    val permissions: StateFlow<PermissionStatusSnapshot> = _permissions.asStateFlow()

    private val _updateState = MutableStateFlow(
        UpdateCenterUiState(
            versionHistory = updateCenterRepository.builtInVersionHistory,
        ),
    )
    val updateState: StateFlow<UpdateCenterUiState> = _updateState.asStateFlow()

    private val _ancPreviewMode = MutableStateFlow(AncPreviewMode.ANC)
    val ancPreviewMode: StateFlow<AncPreviewMode> = _ancPreviewMode.asStateFlow()

    init {
        viewModelScope.launch {
            val savedProfiles = storageRepository.customProfilesFlow.first()
            val activeId = storageRepository.activeProfileIdFlow.first()
            audioEffectsController.initialize(savedProfiles, activeId)

            val savedUrl = storageRepository.updateManifestUrlFlow.first()
            val lastCheck = storageRepository.lastUpdateCheckFlow.first()
            _updateState.update {
                it.copy(
                    manifestUrl = savedUrl,
                    lastCheckedMillis = lastCheck,
                )
            }
        }

        viewModelScope.launch {
            storageRepository.customProfilesFlow.collect { custom ->
                audioEffectsController.updateProfilesList(custom)
            }
        }
    }

    fun onAppStart() {
        refreshPermissions()
        bluetoothManager.startObserving()
    }

    fun onAppStop() {
        bluetoothManager.stopObserving()
    }

    fun refreshPermissions() {
        _permissions.value = readPermissionSnapshot()
        bluetoothManager.refreshAll()
    }

    fun triggerBluetoothScan() {
        bluetoothManager.startBoundedBleScan()
    }

    fun stopBluetoothScan() {
        bluetoothManager.stopBleScan()
    }

    fun refreshBluetoothState() {
        bluetoothManager.refreshAll()
        bluetoothManager.probeConnectedDeviceGatt()
    }

    fun updateEqBand(bandIndex: Int, normalized: Float) {
        audioEffectsController.updateBandNormalized(bandIndex, normalized)
    }

    fun updateBassBoost(strength: Int) {
        audioEffectsController.setBassBoostStrength(strength)
    }

    fun applySoundProfile(profile: SoundProfile) {
        audioEffectsController.applyProfile(profile)
        viewModelScope.launch {
            storageRepository.setActiveProfileId(profile.id)
        }
    }

    fun saveCurrentAsCustomProfile(name: String, description: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        val currentAudio = audioState.value
        val newProfile = SoundProfile(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = trimmedName,
            description = description.trim().ifBlank { "Custom 5-band acoustic curve" },
            bandOffsetsNormalized = currentAudio.bands.map { it.normalizedLevel },
            bassBoostStrength = currentAudio.bassBoostStrength,
            isBuiltIn = false,
        )
        viewModelScope.launch {
            val existingCustom = storageRepository.customProfilesFlow.first()
            val updated = existingCustom + newProfile
            storageRepository.saveCustomProfiles(updated)
            storageRepository.setActiveProfileId(newProfile.id)
            audioEffectsController.updateProfilesList(updated)
            audioEffectsController.applyProfile(newProfile)
        }
    }

    fun deleteCustomProfile(profileId: String) {
        viewModelScope.launch {
            val existingCustom = storageRepository.customProfilesFlow.first()
            val updated = existingCustom.filterNot { it.id == profileId }
            storageRepository.saveCustomProfiles(updated)
            audioEffectsController.updateProfilesList(updated)
            if (audioState.value.activeProfileId == profileId) {
                val balanced = audioEffectsController.builtInProfiles.first()
                applySoundProfile(balanced)
            }
        }
    }

    fun selectAncInspectionMode(mode: AncPreviewMode) {
        _ancPreviewMode.value = mode
    }

    fun setDiagnosticsConsent(enabled: Boolean) {
        crashDiagnosticsManager.updateConsentInMemory(enabled)
        viewModelScope.launch {
            storageRepository.setDiagnosticsConsent(enabled)
            if (!enabled) {
                crashDiagnosticsManager.clearAll()
            }
        }
    }

    fun clearDiagnostics() {
        crashDiagnosticsManager.clearAll()
    }

    fun recordTestDiagnosticWarning() {
        if (!diagnosticsConsent.value) return
        crashDiagnosticsManager.recordCrash(
            threadName = Thread.currentThread().name,
            throwable = IllegalStateException("User-triggered diagnostic test snapshot (non-fatal)"),
        )
    }

    fun createDiagnosticsExportIntent(): Intent? = crashDiagnosticsManager.exportDiagnosticsShareIntent()

    fun createBugReportIntent(summary: String, reproSteps: String): Intent =
        crashDiagnosticsManager.buildBugReportIntent(summary, reproSteps)

    fun updateManifestUrl(url: String) {
        _updateState.update { it.copy(manifestUrl = url) }
        viewModelScope.launch {
            storageRepository.setUpdateManifestUrl(url)
        }
    }

    fun checkForUpdates() {
        val currentUrl = _updateState.value.manifestUrl
        if (_updateState.value.isChecking) return

        viewModelScope.launch {
            _updateState.update {
                it.copy(
                    isChecking = true,
                    checkMessage = null,
                    downloadState = UpdateDownloadState.Idle,
                )
            }
            val result = updateCenterRepository.checkForUpdates(currentUrl)
            val now = System.currentTimeMillis()
            storageRepository.setLastUpdateCheckMillis(now)

            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _updateState.update {
                        it.copy(
                            isChecking = false,
                            lastCheckedMillis = now,
                            availableUpdate = result.manifest,
                            checkMessage = "Version ${result.manifest.versionName} available for verified download.",
                        )
                    }
                }
                is UpdateCheckResult.UpToDate -> {
                    _updateState.update {
                        it.copy(
                            isChecking = false,
                            lastCheckedMillis = now,
                            availableUpdate = null,
                            checkMessage = "You are on the latest release (v${result.currentVersionName}).",
                        )
                    }
                }
                is UpdateCheckResult.Error -> {
                    _updateState.update {
                        it.copy(
                            isChecking = false,
                            lastCheckedMillis = now,
                            checkMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    fun downloadVerifiedUpdate() {
        val manifest = _updateState.value.availableUpdate ?: return
        viewModelScope.launch {
            updateCenterRepository.downloadAndVerifyApk(manifest).collect { dlState ->
                _updateState.update { it.copy(downloadState = dlState) }
            }
        }
    }

    fun buildInstallUpdateIntent(apkPath: String): Intent =
        updateCenterRepository.buildInstallIntent(apkPath)

    fun openBluetoothSettingsIntent(): Intent =
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun openUnknownSourcesSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:${appContext.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }

    private fun readPermissionSnapshot(): PermissionStatusSnapshot {
        val connectGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val scanGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        val legacyLocationGranted = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val canInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { appContext.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }
        return PermissionStatusSnapshot(
            bluetoothConnectGranted = connectGranted,
            bluetoothScanGranted = scanGranted,
            locationGrantedIfRequired = legacyLocationGranted,
            notificationsGranted = notificationsGranted,
            canRequestPackageInstalls = canInstall,
            requiresLegacyLocationForBle = Build.VERSION.SDK_INT <= Build.VERSION_CODES.R,
        )
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothManager.release()
        audioEffectsController.release()
    }

    class Factory(
        private val appContext: Context,
        private val bluetoothManager: BluetoothEarbudsManager,
        private val audioEffectsController: AudioEffectsController,
        private val storageRepository: SecureStorageRepository,
        private val crashDiagnosticsManager: CrashDiagnosticsManager,
        private val updateCenterRepository: UpdateCenterRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NordBudsViewModel(
                appContext = appContext,
                bluetoothManager = bluetoothManager,
                audioEffectsController = audioEffectsController,
                storageRepository = storageRepository,
                crashDiagnosticsManager = crashDiagnosticsManager,
                updateCenterRepository = updateCenterRepository,
            ) as T
        }
    }
}
