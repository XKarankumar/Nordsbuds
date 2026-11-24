package com.example.hardware

import com.example.model.EarbudsBatteryTelemetry
import java.util.UUID

/**
 * Pure protocol parsers for open, verified Bluetooth specifications:
 * - Google Fast Pair Service (GFPS) 0xFE2C battery notifications
 * - Standard Bluetooth SIG Battery Service (0x180F / 0x2A19)
 * - HFP +IPHONEACCEV battery telemetry
 * - Standard Bluetooth SIG Service UUID labels
 */
object BluetoothProtocolParser {

    val FAST_PAIR_SERVICE_UUID: UUID = UUID.fromString("0000FE2C-0000-1000-8000-00805F9B34FB")
    val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    val DEVICE_INFO_SERVICE_UUID: UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
    val MANUFACTURER_NAME_CHAR_UUID: UUID = UUID.fromString("00002A29-0000-1000-8000-00805F9B34FB")
    val MODEL_NUMBER_CHAR_UUID: UUID = UUID.fromString("00002A24-0000-1000-8000-00805F9B34FB")
    val FIRMWARE_REV_CHAR_UUID: UUID = UUID.fromString("00002A26-0000-1000-8000-00805F9B34FB")
    val HARDWARE_REV_CHAR_UUID: UUID = UUID.fromString("00002A27-0000-1000-8000-00805F9B34FB")

    fun isNordOrOnePlusAudioDevice(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val normalized = name.lowercase()
        return normalized.contains("nord buds") ||
            normalized.contains("oneplus") ||
            normalized.contains("buds 3") ||
            normalized.contains("buds pro")
    }

    /**
     * Parses BLE standard Battery Service (0x2A19) value (0..100).
     */
    fun parseStandardBatteryLevel(bytes: ByteArray?): Int? {
        if (bytes == null || bytes.isEmpty()) return null
        val level = bytes[0].toInt() and 0xFF
        return level.takeIf { it in 0..100 }
    }

    /**
     * Parses Google Fast Pair (0xFE2C) Service Data for Account Key / Battery TLV packets.
     * In Fast Pair BLE advertisements, when Account Key Filter + Battery Data is broadcast:
     * Salt / Flags / AccountKeyFilter (LTV) followed by Battery LTV (type 0b0011 = 3 or 0b0100 = 4, length 3 bytes: Left, Right, Case).
     * Each battery byte uses bit 7 for charging (1 = charging) and bits 0..6 for percentage (0..100, or 0x7F = unknown).
     */
    fun parseFastPairBatteryServiceData(serviceData: ByteArray?, timestampMillis: Long = System.currentTimeMillis()): EarbudsBatteryTelemetry? {
        if (serviceData == null || serviceData.size < 5) return null
        // Fast Pair encrypted/unencrypted account key frames start with 0x00 (version/flags) or LTV entries.
        // Scan safely for a 3-byte battery LTV block where type == 3 (show UI) or 4 (hide UI) and length == 3.
        var idx = 1
        while (idx < serviceData.size) {
            val header = serviceData[idx].toInt() and 0xFF
            val length = (header ushr 4) and 0x0F
            val type = header and 0x0F
            if (length == 0) {
                idx++
                continue
            }
            if ((type == 3 || type == 4) && length == 3 && idx + 3 < serviceData.size) {
                val leftRaw = serviceData[idx + 1].toInt() and 0xFF
                val rightRaw = serviceData[idx + 2].toInt() and 0xFF
                val caseRaw = serviceData[idx + 3].toInt() and 0xFF

                val leftVal = (leftRaw and 0x7F).takeIf { it in 0..100 }
                val rightVal = (rightRaw and 0x7F).takeIf { it in 0..100 }
                val caseVal = (caseRaw and 0x7F).takeIf { it in 0..100 }

                if (leftVal != null || rightVal != null || caseVal != null) {
                    val combined = listOfNotNull(leftVal, rightVal).takeIf { it.isNotEmpty() }?.average()?.toInt()
                    return EarbudsBatteryTelemetry(
                        combinedPercent = combined,
                        leftPercent = leftVal,
                        rightPercent = rightVal,
                        casePercent = caseVal,
                        isLeftCharging = (leftRaw and 0x80) != 0,
                        isRightCharging = (rightRaw and 0x80) != 0,
                        isCaseCharging = (caseRaw and 0x80) != 0,
                        sourceLabel = "Google Fast Pair (0xFE2C)",
                        lastUpdatedMillis = timestampMillis,
                    )
                }
            }
            idx += 1 + length
        }
        return null
    }

    /**
     * Parses HFP +IPHONEACCEV vendor-specific AT event args:
     * Format: [count, key1, val1, key2, val2, ...] where key == 1 is battery level (0..9 -> 10%..100%).
     */
    fun parseHfpAccessoryBatteryEvent(args: Array<out Any?>?): Int? {
        if (args == null || args.size < 3) return null
        val count = (args[0] as? Number)?.toInt() ?: return null
        var idx = 1
        for (pair in 0 until count) {
            if (idx + 1 >= args.size) break
            val key = (args[idx] as? Number)?.toInt()
            val value = (args[idx + 1] as? Number)?.toInt()
            if (key == 1 && value != null && value in 0..9) {
                return (value + 1) * 10
            }
            idx += 2
        }
        return null
    }

    fun describeUuid(uuid: UUID): String {
        val shortHex = uuid.toString().uppercase().substring(4, 8)
        return when (shortHex) {
            "110B" -> "A2DP Audio Sink (0x110B)"
            "110A" -> "A2DP Audio Source (0x110A)"
            "111E" -> "Hands-Free HFP (0x111E)"
            "1108" -> "Headset HSP (0x1108)"
            "110C" -> "AVRCP Remote Control (0x110C)"
            "110E" -> "AVRCP Controller (0x110E)"
            "1101" -> "Serial Port SPP / Vendor RFCOMM (0x1101)"
            "180F" -> "BLE Battery Service (0x180F)"
            "180A" -> "BLE Device Information (0x180A)"
            "184E" -> "BLE Audio Stream Control (0x184E)"
            "1853" -> "BLE Common Audio Service (0x1853)"
            "FE2C" -> "Google Fast Pair (0xFE2C)"
            else -> uuid.toString().uppercase()
        }
    }
}
