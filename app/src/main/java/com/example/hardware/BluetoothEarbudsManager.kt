package com.example.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothCodecConfig
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.example.model.AudioCodecTelemetry
import com.example.model.BluetoothDashboardState
import com.example.model.ConnectionStatus
import com.example.model.DiscoveredBluetoothDevice
import com.example.model.EarbudsBatteryTelemetry
import com.example.model.HardwareCapabilityState
import com.example.model.ProtocolCapabilityItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Battery-efficient, lifecycle-aware Bluetooth & BLE telemetry manager.
 * - Uses system broadcasts & profile proxies instead of polling loops.
 * - Uses bounded 10-second low-power BLE scans on explicit user request only.
 * - Never fabricates battery or ANC hardware telemetry.
 */
class BluetoothEarbudsManager(
    private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _state = MutableStateFlow(
        BluetoothDashboardState(
            isBluetoothSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH),
            isBleSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE),
        ),
    )
    val state: StateFlow<BluetoothDashboardState> = _state.asStateFlow()

    private var a2dpProxy: BluetoothA2dp? = null
    private var headsetProxy: BluetoothHeadset? = null
    private var isReceiverRegistered = false
    private var scanTimeoutJob: Job? = null
    private var activeGatt: BluetoothGatt? = null
    private var isScanningInternal = false

    private val gattQueue = ArrayDeque<Pair<java.util.UUID, java.util.UUID>>()

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = proxy as? BluetoothA2dp
                BluetoothProfile.HEADSET -> headsetProxy = proxy as? BluetoothHeadset
            }
            refreshAll()
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = null
                BluetoothProfile.HEADSET -> headsetProxy = null
            }
            refreshAll()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    refreshAll()
                }
                "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> {
                    val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                    if (level in 0..100) {
                        _state.update { current ->
                            current.copy(
                                battery = current.battery.copy(
                                    combinedPercent = level,
                                    sourceLabel = "Android Bluetooth HFP/Metadata",
                                    lastUpdatedMillis = System.currentTimeMillis(),
                                ),
                            )
                        }
                    }
                    refreshAll()
                }
                BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT -> {
                    val cmd = intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD)
                    if (cmd == "+IPHONEACCEV") {
                        @Suppress("DEPRECATION")
                        val args = intent.getSerializableExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS) as? Array<*>
                        val hfpPercent = BluetoothProtocolParser.parseHfpAccessoryBatteryEvent(args)
                        if (hfpPercent != null) {
                            _state.update { current ->
                                current.copy(
                                    battery = current.battery.copy(
                                        combinedPercent = hfpPercent,
                                        sourceLabel = "HFP +IPHONEACCEV Event",
                                        lastUpdatedMillis = System.currentTimeMillis(),
                                    ),
                                )
                            }
                        }
                    }
                }
                BluetoothDevice.ACTION_UUID -> {
                    refreshAll()
                }
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!hasScanPermission()) return
            val device = result.device ?: return
            val name = safeDeviceName(device) ?: result.scanRecord?.deviceName
            val fastPairData = result.scanRecord?.getServiceData(ParcelUuid(BluetoothProtocolParser.FAST_PAIR_SERVICE_UUID))
            val parsedFastPair = BluetoothProtocolParser.parseFastPairBatteryServiceData(fastPairData)

            if (parsedFastPair != null && (BluetoothProtocolParser.isNordOrOnePlusAudioDevice(name) ||
                    _state.value.activeDevice?.address == device.address)
            ) {
                _state.update { current ->
                    current.copy(
                        battery = parsedFastPair,
                    )
                }
            }

            if (name != null || BluetoothProtocolParser.isNordOrOnePlusAudioDevice(name)) {
                val entry = DiscoveredBluetoothDevice(
                    name = name ?: "BLE Audio Device",
                    address = device.address ?: "Unknown",
                    isBonded = safeBondState(device) == BluetoothDevice.BOND_BONDED,
                    isA2dpConnected = false,
                    isHeadsetConnected = false,
                    rssiDbm = result.rssi,
                    isOnePlusTarget = BluetoothProtocolParser.isNordOrOnePlusAudioDevice(name),
                )
                _state.update { current ->
                    val merged = (current.nearbyScanResults.filterNot { it.address == entry.address } + entry)
                        .sortedWith(compareByDescending<DiscoveredBluetoothDevice> { it.isOnePlusTarget }.thenByDescending { it.rssiDbm ?: -120 })
                    current.copy(nearbyScanResults = merged.take(12))
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            stopBleScan()
            _state.update { it.copy(lastError = "BLE scan failed (code $errorCode)") }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!hasConnectPermission()) {
                closeGatt()
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                closeGatt()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || !hasConnectPermission()) {
                closeGatt()
                return
            }
            gattQueue.clear()
            if (gatt.getService(BluetoothProtocolParser.BATTERY_SERVICE_UUID) != null) {
                gattQueue.add(BluetoothProtocolParser.BATTERY_SERVICE_UUID to BluetoothProtocolParser.BATTERY_LEVEL_CHAR_UUID)
            }
            if (gatt.getService(BluetoothProtocolParser.DEVICE_INFO_SERVICE_UUID) != null) {
                gattQueue.add(BluetoothProtocolParser.DEVICE_INFO_SERVICE_UUID to BluetoothProtocolParser.MANUFACTURER_NAME_CHAR_UUID)
                gattQueue.add(BluetoothProtocolParser.DEVICE_INFO_SERVICE_UUID to BluetoothProtocolParser.FIRMWARE_REV_CHAR_UUID)
                gattQueue.add(BluetoothProtocolParser.DEVICE_INFO_SERVICE_UUID to BluetoothProtocolParser.HARDWARE_REV_CHAR_UUID)
            }
            val discoveredLabels = gatt.services.map { BluetoothProtocolParser.describeUuid(it.uuid) }
            if (discoveredLabels.isNotEmpty()) {
                _state.update { current ->
                    val updatedActive = current.activeDevice?.let { dev ->
                        dev.copy(uuids = (dev.uuids + discoveredLabels).distinct())
                    }
                    current.copy(activeDevice = updatedActive)
                }
            }
            readNextGattCharacteristic(gatt)
        }

        @Deprecated("Deprecated in API 33; kept for API 24-32 compatibility")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristicBytes(characteristic.uuid, characteristic.value)
            }
            readNextGattCharacteristic(gatt)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristicBytes(characteristic.uuid, value)
            }
            readNextGattCharacteristic(gatt)
        }
    }

    @SuppressLint("MissingPermission")
    private fun readNextGattCharacteristic(gatt: BluetoothGatt) {
        if (!hasConnectPermission()) {
            closeGatt()
            return
        }
        while (gattQueue.isNotEmpty()) {
            val (serviceUuid, charUuid) = gattQueue.removeFirst()
            val characteristic = gatt.getService(serviceUuid)?.getCharacteristic(charUuid)
            if (characteristic != null && gatt.readCharacteristic(characteristic)) {
                return
            }
        }
        // Finished reading standard GATT attributes; disconnect immediately to conserve earbud & phone battery.
        closeGatt()
    }

    private fun handleCharacteristicBytes(uuid: java.util.UUID, value: ByteArray?) {
        if (value == null || value.isEmpty()) return
        when (uuid) {
            BluetoothProtocolParser.BATTERY_LEVEL_CHAR_UUID -> {
                val pct = BluetoothProtocolParser.parseStandardBatteryLevel(value)
                if (pct != null) {
                    _state.update { current ->
                        current.copy(
                            battery = current.battery.copy(
                                combinedPercent = pct,
                                sourceLabel = "BLE Battery Service (0x180F)",
                                lastUpdatedMillis = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            }
            BluetoothProtocolParser.MANUFACTURER_NAME_CHAR_UUID -> {
                val text = value.decodeToString().trim().takeIf { it.isNotEmpty() }
                _state.update { it.copy(manufacturerName = text) }
            }
            BluetoothProtocolParser.FIRMWARE_REV_CHAR_UUID -> {
                val text = value.decodeToString().trim().takeIf { it.isNotEmpty() }
                _state.update { it.copy(firmwareRevision = text) }
            }
            BluetoothProtocolParser.HARDWARE_REV_CHAR_UUID -> {
                val text = value.decodeToString().trim().takeIf { it.isNotEmpty() }
                _state.update { it.copy(hardwareRevision = text) }
            }
        }
    }

    fun startObserving() {
        registerReceiverIfNeeded()
        val adapter = bluetoothAdapter
        if (hasConnectPermission() && adapter != null) {
            runCatching {
                if (a2dpProxy == null) {
                    adapter.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
                }
                if (headsetProxy == null) {
                    adapter.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
                }
            }
        }
        refreshAll()
    }

    fun stopObserving() {
        stopBleScan()
        closeGatt()
        unregisterReceiverIfNeeded()
    }

    fun release() {
        stopObserving()
        bluetoothAdapter?.let { adapter ->
            a2dpProxy?.let { runCatching { adapter.closeProfileProxy(BluetoothProfile.A2DP, it) } }
            headsetProxy?.let { runCatching { adapter.closeProfileProxy(BluetoothProfile.HEADSET, it) } }
        }
        a2dpProxy = null
        headsetProxy = null
    }

    @SuppressLint("MissingPermission")
    fun refreshAll() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _state.update {
                it.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    lastError = "Bluetooth adapter unavailable on this device",
                )
            }
            return
        }

        if (!hasConnectPermission()) {
            _state.update {
                it.copy(
                    connectionStatus = ConnectionStatus.NO_PERMISSION,
                    activeDevice = null,
                    bondedAudioDevices = emptyList(),
                    capabilities = buildCapabilityMatrix(hasBattery = false, hasSplitBattery = false, hasDevice = false),
                )
            }
            return
        }

        if (!adapter.isEnabled) {
            _state.update {
                it.copy(
                    connectionStatus = ConnectionStatus.BLUETOOTH_OFF,
                    activeDevice = null,
                    battery = EarbudsBatteryTelemetry(),
                    codec = AudioCodecTelemetry(),
                    capabilities = buildCapabilityMatrix(hasBattery = false, hasSplitBattery = false, hasDevice = false),
                )
            }
            return
        }

        val a2dpConnected = runCatching { a2dpProxy?.connectedDevices.orEmpty() }.getOrDefault(emptyList())
        val headsetConnected = runCatching { headsetProxy?.connectedDevices.orEmpty() }.getOrDefault(emptyList())
        val bonded = runCatching { adapter.bondedDevices.orEmpty() }.getOrDefault(emptySet())

        val connectedAddresses = (a2dpConnected + headsetConnected).map { it.address }.toSet()

        val audioDevices = bonded.filter { device ->
            val majorClass = device.bluetoothClass?.majorDeviceClass
            majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                connectedAddresses.contains(device.address) ||
                BluetoothProtocolParser.isNordOrOnePlusAudioDevice(safeDeviceName(device))
        }.map { device ->
            val name = safeDeviceName(device) ?: "Bluetooth Audio"
            val uuids = runCatching {
                device.uuids?.map { BluetoothProtocolParser.describeUuid(it.uuid) }.orEmpty()
            }.getOrDefault(emptyList())
            DiscoveredBluetoothDevice(
                name = name,
                address = device.address,
                isBonded = true,
                isA2dpConnected = a2dpConnected.any { it.address == device.address },
                isHeadsetConnected = headsetConnected.any { it.address == device.address },
                isOnePlusTarget = BluetoothProtocolParser.isNordOrOnePlusAudioDevice(name),
                uuids = uuids,
            )
        }.sortedWith(
            compareByDescending<DiscoveredBluetoothDevice> { it.isA2dpConnected || it.isHeadsetConnected }
                .thenByDescending { it.isOnePlusTarget },
        )

        val rawActiveDevice = (a2dpConnected + headsetConnected)
            .sortedByDescending { BluetoothProtocolParser.isNordOrOnePlusAudioDevice(safeDeviceName(it)) }
            .firstOrNull()

        val activeModel = rawActiveDevice?.let { dev ->
            audioDevices.firstOrNull { it.address == dev.address } ?: DiscoveredBluetoothDevice(
                name = safeDeviceName(dev) ?: "Connected Earbuds",
                address = dev.address,
                isBonded = safeBondState(dev) == BluetoothDevice.BOND_BONDED,
                isA2dpConnected = a2dpConnected.any { it.address == dev.address },
                isHeadsetConnected = headsetConnected.any { it.address == dev.address },
                isOnePlusTarget = BluetoothProtocolParser.isNordOrOnePlusAudioDevice(safeDeviceName(dev)),
                uuids = runCatching { dev.uuids?.map { BluetoothProtocolParser.describeUuid(it.uuid) }.orEmpty() }.getOrDefault(emptyList()),
            )
        }

        val platformBattery = rawActiveDevice?.let { readPlatformBatteryLevel(it) }
        val codecInfo = rawActiveDevice?.let { readA2dpCodecInfo(it) } ?: AudioCodecTelemetry()
        val isPlaybackActive = isBluetoothAudioRoutingActive()

        _state.update { current ->
            val updatedBattery = when {
                rawActiveDevice == null -> EarbudsBatteryTelemetry()
                platformBattery != null -> current.battery.copy(
                    combinedPercent = platformBattery,
                    sourceLabel = if (current.battery.leftPercent != null) current.battery.sourceLabel else "Android Bluetooth Metadata",
                    lastUpdatedMillis = System.currentTimeMillis(),
                )
                else -> current.battery
            }

            val nextStatus = when {
                isScanningInternal -> ConnectionStatus.SCANNING
                activeModel != null -> ConnectionStatus.CONNECTED
                else -> ConnectionStatus.DISCONNECTED
            }

            current.copy(
                connectionStatus = nextStatus,
                activeDevice = activeModel,
                bondedAudioDevices = audioDevices,
                battery = updatedBattery,
                codec = codecInfo,
                capabilities = buildCapabilityMatrix(
                    hasBattery = updatedBattery.combinedPercent != null,
                    hasSplitBattery = updatedBattery.leftPercent != null || updatedBattery.rightPercent != null || updatedBattery.casePercent != null,
                    hasDevice = activeModel != null,
                ),
                isAudioPlaybackActive = isPlaybackActive,
                lastError = null,
            )
        }
    }

    /**
     * Triggers a single transient GATT query on the currently connected device to read standard
     * Battery Service (0x180F) and Device Info Service (0x180A) if supported by firmware.
     */
    @SuppressLint("MissingPermission")
    fun probeConnectedDeviceGatt() {
        val adapter = bluetoothAdapter ?: return
        if (!hasConnectPermission()) return
        val activeAddr = _state.value.activeDevice?.address ?: return
        val device = runCatching { adapter.getRemoteDevice(activeAddr) }.getOrNull() ?: return
        closeGatt()
        runCatching {
            activeGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    /**
     * Starts a battery-efficient, bounded 10-second low-power BLE scan.
     */
    @SuppressLint("MissingPermission")
    fun startBoundedBleScan() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || !hasScanPermission()) {
            refreshAll()
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            _state.update { it.copy(lastError = "BLE scanner unavailable") }
            return
        }
        if (isScanningInternal) return

        isScanningInternal = true
        _state.update { it.copy(connectionStatus = ConnectionStatus.SCANNING, lastError = null) }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        runCatching {
            scanner.startScan(null, settings, bleScanCallback)
        }.onFailure { err ->
            isScanningInternal = false
            _state.update { it.copy(lastError = err.message ?: "Unable to start BLE scan") }
            refreshAll()
            return
        }

        // Also probe connected GATT briefly if already paired & connected
        probeConnectedDeviceGatt()

        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(10_000L)
            stopBleScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        if (!isScanningInternal) return
        isScanningInternal = false
        if (hasScanPermission()) {
            runCatching { bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback) }
        }
        refreshAll()
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        activeGatt?.let { gatt ->
            runCatching {
                if (hasConnectPermission()) {
                    gatt.disconnect()
                    gatt.close()
                }
            }
        }
        activeGatt = null
    }

    @SuppressLint("MissingPermission")
    private fun readPlatformBatteryLevel(device: BluetoothDevice): Int? {
        if (!hasConnectPermission()) return null
        return runCatching {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val level = method.invoke(device) as? Int ?: -1
            level.takeIf { it in 0..100 }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun readA2dpCodecInfo(device: BluetoothDevice): AudioCodecTelemetry {
        val proxy = a2dpProxy ?: return AudioCodecTelemetry()
        if (!hasConnectPermission()) return AudioCodecTelemetry()
        return runCatching {
            val codecStatus = proxy.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java)
                .invoke(proxy, device) as? android.bluetooth.BluetoothCodecStatus
            val config: BluetoothCodecConfig = codecStatus?.codecConfig ?: return AudioCodecTelemetry()
            val codecName = when (config.codecType) {
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC -> "SBC"
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC -> "AAC"
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX -> "aptX"
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD -> "aptX HD"
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC -> "LDAC"
                else -> "Vendor / LHDC (${config.codecType})"
            }
            val sampleRate = when (config.sampleRate) {
                BluetoothCodecConfig.SAMPLE_RATE_44100 -> "44.1 kHz"
                BluetoothCodecConfig.SAMPLE_RATE_48000 -> "48.0 kHz"
                BluetoothCodecConfig.SAMPLE_RATE_88200 -> "88.2 kHz"
                BluetoothCodecConfig.SAMPLE_RATE_96000 -> "96.0 kHz"
                else -> "Auto"
            }
            val bits = when (config.bitsPerSample) {
                BluetoothCodecConfig.BITS_PER_SAMPLE_16 -> "16-bit"
                BluetoothCodecConfig.BITS_PER_SAMPLE_24 -> "24-bit"
                BluetoothCodecConfig.BITS_PER_SAMPLE_32 -> "32-bit"
                else -> "Auto"
            }
            val channel = when (config.channelMode) {
                BluetoothCodecConfig.CHANNEL_MODE_STEREO -> "Stereo"
                BluetoothCodecConfig.CHANNEL_MODE_MONO -> "Mono"
                else -> "Stereo"
            }
            AudioCodecTelemetry(
                codecName = codecName,
                sampleRateLabel = sampleRate,
                bitsPerSampleLabel = bits,
                channelModeLabel = channel,
                isHardwareVerified = true,
            )
        }.getOrDefault(AudioCodecTelemetry())
    }

    private fun isBluetoothAudioRoutingActive(): Boolean {
        val am = audioManager ?: return false
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val hasBtOutput = outputs.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        return hasBtOutput && am.isMusicActive
    }

    private fun buildCapabilityMatrix(
        hasBattery: Boolean,
        hasSplitBattery: Boolean,
        hasDevice: Boolean,
    ): List<ProtocolCapabilityItem> = listOf(
        ProtocolCapabilityItem(
            featureName = "Bluetooth A2DP / HFP Status & Codec",
            protocolName = "Android BluetoothProfile & CodecStatus",
            status = if (hasDevice) HardwareCapabilityState.SUPPORTED_LIVE else HardwareCapabilityState.UNAVAILABLE_NOT_EXPOSED,
            detail = if (hasDevice) "Live connection & active codec read directly from Android Bluetooth stack." else "Connect earbuds to read live Bluetooth profile & codec telemetry.",
        ),
        ProtocolCapabilityItem(
            featureName = "Combined Earbuds Battery",
            protocolName = "Android HFP Metadata / BLE 0x180F",
            status = if (hasBattery) HardwareCapabilityState.SUPPORTED_LIVE else HardwareCapabilityState.UNAVAILABLE_NOT_EXPOSED,
            detail = if (hasBattery) "Real battery percentage exposed by Bluetooth metadata or GATT." else "Waiting for connected earbuds to report battery level.",
        ),
        ProtocolCapabilityItem(
            featureName = "L / R / Case Split Battery",
            protocolName = "Google Fast Pair (0xFE2C) / OEM GATT",
            status = if (hasSplitBattery) HardwareCapabilityState.SUPPORTED_LIVE else HardwareCapabilityState.UNSUPPORTED_PROPRIETARY,
            detail = if (hasSplitBattery) "Parsed from live Fast Pair (0xFE2C) advertisement." else "Nord Buds 3 Pro split L/R/Case readings use HeyMelody proprietary frames unless Fast Pair 0xFE2C broadcasts them.",
        ),
        ProtocolCapabilityItem(
            featureName = "5+ Band Equalizer & Bass Boost",
            protocolName = "android.media.audiofx (AudioEffect HAL)",
            status = HardwareCapabilityState.SUPPORTED_SYSTEM_AUDIO,
            detail = "Real DSP audio processing on Android output pipeline.",
        ),
        ProtocolCapabilityItem(
            featureName = "ANC / Transparency / Touch Remapping",
            protocolName = "OnePlus / HeyMelody Proprietary RFCOMM",
            status = HardwareCapabilityState.UNSUPPORTED_PROPRIETARY,
            detail = "Vendor-locked binary protocol not available in public Android SDKs. Use Vivo X300 system Bluetooth settings or HeyMelody.",
        ),
    )

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String? =
        if (hasConnectPermission()) runCatching { device.name }.getOrNull() else null

    @SuppressLint("MissingPermission")
    private fun safeBondState(device: BluetoothDevice): Int =
        if (hasConnectPermission()) runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE) else BluetoothDevice.BOND_NONE

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun registerReceiverIfNeeded() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_UUID)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            addAction(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)
        }
        ContextCompat.registerReceiver(
            context,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        isReceiverRegistered = true
    }

    private fun unregisterReceiverIfNeeded() {
        if (!isReceiverRegistered) return
        runCatching { context.unregisterReceiver(bluetoothReceiver) }
        isReceiverRegistered = false
    }
}
