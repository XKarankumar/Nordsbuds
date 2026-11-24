# Testing & Bug-Fixing Documentation

## 1. Automated Test Suite (JVM & Robolectric)

Run the full unit and Robolectric test suite without an emulator:

```bash
gradle :app:testDebugUnitTest
```

### Included Test Coverage
- **`BluetoothProtocolParserTest`**:
  - Validates Google Fast Pair (`0xFE2C`) Left/Right/Case battery & charging flag decoding.
  - Validates standard BLE Battery Service (`0x2A19`) bounds (`0..100` vs invalid bytes).
  - Validates HFP `+IPHONEACCEV` AT command battery extraction.
  - Validates OnePlus / Nord Buds device name detection.
- **`UpdateCenterRepositoryTest`**:
  - Validates GitHub Releases API JSON parsing + SHA-256 extraction.
  - Validates direct manifest JSON parsing and semantic version code ordering.
  - Rejects non-HTTPS update endpoints.
- **`ExampleRobolectricTest`**:
  - Boots `MainActivity` under Robolectric SDK 36.
  - Verifies Compose UI navigation tabs (`Dashboard`, `Audio & EQ`, `Updates`, `System`) and core interactive elements.

## 2. Manual Verification Checklist (Vivo X300 + OnePlus Nord Buds 3 Pro)

1. **Cold Launch & Splash**:
   - Confirm smooth AMOLED `#000000` splash transition with zero white flash.
2. **Bluetooth Telemetry**:
   - Pair & connect OnePlus Nord Buds 3 Pro in Vivo X300 Bluetooth settings.
   - Open NordBuds Premium and grant Nearby Devices permission.
   - Verify connected device name, active codec (AAC/LHDC/SBC), and combined battery level update automatically when audio plays or battery changes.
3. **Equalizer & Bass Boost**:
   - Navigate to **Audio & EQ**, start music playback, and adjust the 5+ EQ bands and Bass Boost slider.
   - Save a custom profile, force-close the app, reopen, and verify the custom profile persists via DataStore.
4. **Battery Optimization Check**:
   - Turn off the Vivo X300 screen and verify animations pause immediately upon `ON_STOP` / screen-off.
   - Tap **Scan / Refresh** and confirm BLE scan stops automatically after 10 seconds.
5. **Diagnostics & Update Center**:
   - Toggle **Capture Crash Diagnostics Locally** OFF -> verify no logs are stored. Toggle ON -> record a non-fatal test snapshot -> verify JSON export scrubs Bluetooth MAC addresses.

## 3. Troubleshooting Common Issues

- **`AudioEffect HAL unavailable` on Equalizer tab**: Some Vivo/OEM audio configurations disable global session `0` effects when spatial audio or Dirac/Dolby system equalizers are exclusively locked. Disabling conflicting system-level sound enhancers in Vivo Sound Settings restores Android `Equalizer(0, 0)` access.
- **Split L/R/Case Battery shows `Not Exposed`**: Normal when earbuds only expose standard Bluetooth HFP metadata (`ACTION_BATTERY_LEVEL_CHANGED`) instead of Fast Pair `0xFE2C` broadcast packets.
- **APK Update Install Blocked**: Android requires explicit per-app permission for sideloaded updates (`Settings -> Install unknown apps`). Tap **Allow App Installs in Android Settings** inside the Update Center.
