# NordBuds Premium

A flagship AMOLED black & crimson Jetpack Compose Android companion application built with Kotlin and Material 3, tailored for **Vivo X300** and **OnePlus Nord Buds 3 Pro**.

## Features & Honest Protocol Architecture

NordBuds Premium strictly uses verified, public Android APIs and open Bluetooth specifications—it **never** simulates hardware data or exposes fake controls:

| Feature | Backing Android / Bluetooth Protocol | Availability |
|---|---|---|
| Connection Status & Audio Profiles | `BluetoothManager`, `BluetoothProfile.A2DP`, `BluetoothProfile.HEADSET` | Live |
| Active Audio Codec (AAC / SBC / LDAC / LHDC) | `BluetoothA2dp.getCodecStatus` | Live (Android 8+) |
| Combined Battery Level | `BluetoothDevice` Battery Metadata, `ACTION_BATTERY_LEVEL_CHANGED`, BLE Battery Service (`0x180F`) | Live when exposed by earbuds |
| Split L / R / Case Battery | Google Fast Pair Service (`0xFE2C`) BLE advertisement decoder | Live if earbuds broadcast `0xFE2C`; otherwise marked `Not Exposed` |
| Device / Firmware Info | BLE Device Information Service (`0x180A`) | Live if exposed by firmware |
| 5+ Band Equalizer & Custom Profiles | `android.media.audiofx.Equalizer` + DataStore persistence | Live on device AudioEffect HAL |
| Bass Boost | `android.media.audiofx.BassBoost` | Live if supported by HAL |
| ANC / Transparency / Touch Remapping | OnePlus / HeyMelody proprietary vendor RFCOMM/GATT protocol | Transparently marked `OEM Locked` with direct link to System Bluetooth Settings |

## Setup Instructions

1. **Open in Android Studio** (Ladybug or newer recommended) with JDK 17+.
2. **Build & Run on Vivo X300**:
   - Enable USB Debugging on your Vivo X300.
   - Run `:app:assembleDebug` or click **Run** in Android Studio.
3. **Permissions on Vivo X300 (OriginOS / Android 14–16)**:
   - Grant **Nearby devices** (`BLUETOOTH_CONNECT` & `BLUETOOTH_SCAN`). Location is **not** requested on Android 12+ (`neverForLocation`).
   - Grant **Notifications** (`POST_NOTIFICATIONS`) if you want update completion alerts.
   - To keep Bluetooth events reliable without wake-locks, NordBuds Premium uses system broadcasts and bounded 10-second scans—no background service exemptions are required.
4. **Signing a Release APK**:
   Configure environment variables for Gradle's `release` signing config:
   - `KEYSTORE_PATH`: Path to your `.jks` upload keystore (defaults to `my-upload-key.jks`).
   - `STORE_PASSWORD`: Keystore password.
   - `KEY_PASSWORD`: Key password for alias `upload`.
5. **GitHub Actions Automated Builds**:
   Push to `main` or tag `v1.0.1` to trigger `.github/workflows/android-apk.yml`. Add `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD` in GitHub Repository Secrets for signed release APKs.

## Update Center Verification Format

The Update Center supports GitHub Releases API JSON or a direct HTTPS JSON manifest:

```json
{
  "versionCode": 2,
  "versionName": "1.1.0",
  "releaseDate": "2026-12-01",
  "apkUrl": "https://example.com/NordBudsPremium-1.1.0.apk",
  "sha256": "<64-char-lowercase-sha256>",
  "minAndroidSdk": 24,
  "releaseNotes": ["Improved Fast Pair telemetry"],
  "isCritical": false
}
```
