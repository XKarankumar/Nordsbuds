package com.example

import android.app.Application
import com.example.data.CrashDiagnosticsManager
import com.example.data.SecureStorageRepository
import com.example.data.UpdateCenterRepository
import com.example.hardware.AudioEffectsController
import com.example.hardware.BluetoothEarbudsManager

class NordBudsApp : Application() {

    lateinit var storageRepository: SecureStorageRepository
        private set

    lateinit var crashDiagnosticsManager: CrashDiagnosticsManager
        private set

    lateinit var bluetoothEarbudsManager: BluetoothEarbudsManager
        private set

    lateinit var audioEffectsController: AudioEffectsController
        private set

    lateinit var updateCenterRepository: UpdateCenterRepository
        private set

    override fun onCreate() {
        super.onCreate()
        storageRepository = SecureStorageRepository(applicationContext)
        crashDiagnosticsManager = CrashDiagnosticsManager(applicationContext, storageRepository).apply {
            initialize()
        }
        bluetoothEarbudsManager = BluetoothEarbudsManager(applicationContext)
        audioEffectsController = AudioEffectsController()
        updateCenterRepository = UpdateCenterRepository(applicationContext)
    }
}
