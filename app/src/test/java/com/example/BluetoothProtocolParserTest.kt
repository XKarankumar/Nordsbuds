package com.example

import com.example.hardware.BluetoothProtocolParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothProtocolParserTest {

    @Test
    fun `parseStandardBatteryLevel accepts 0 to 100 and rejects invalid bytes`() {
        assertEquals(85, BluetoothProtocolParser.parseStandardBatteryLevel(byteArrayOf(85)))
        assertEquals(0, BluetoothProtocolParser.parseStandardBatteryLevel(byteArrayOf(0)))
        assertEquals(100, BluetoothProtocolParser.parseStandardBatteryLevel(byteArrayOf(100)))
        assertNull(BluetoothProtocolParser.parseStandardBatteryLevel(byteArrayOf(127)))
        assertNull(BluetoothProtocolParser.parseStandardBatteryLevel(byteArrayOf()))
        assertNull(BluetoothProtocolParser.parseStandardBatteryLevel(null))
    }

    @Test
    fun `parseFastPairBatteryServiceData extracts left right and case percentages with charging flags`() {
        // Version byte 0x00, AccountKeyFilter LTV length 2 type 0 (header 0x20, 2 dummy bytes),
        // Battery LTV length 3 type 3 (header 0x33), Left=90% (0x5A), Right=85% charging (0x80|0x55 = 0xD5), Case=60% (0x3C)
        val payload = byteArrayOf(
            0x00,
            0x20, 0x11, 0x22,
            0x33, 0x5A, 0xD5.toByte(), 0x3C,
        )
        val result = BluetoothProtocolParser.parseFastPairBatteryServiceData(payload, timestampMillis = 1234L)
        assertNotNull(result)
        assertEquals(90, result?.leftPercent)
        assertEquals(85, result?.rightPercent)
        assertEquals(60, result?.casePercent)
        assertFalse(result?.isLeftCharging ?: true)
        assertTrue(result?.isRightCharging ?: false)
        assertFalse(result?.isCaseCharging ?: true)
        assertEquals(87, result?.combinedPercent)
    }

    @Test
    fun `parseHfpAccessoryBatteryEvent converts 0 to 9 scale into 10 to 100 percent`() {
        val args = arrayOf<Any>(1, 1, 7) // count=1, key=1 (battery), val=7 -> 80%
        assertEquals(80, BluetoothProtocolParser.parseHfpAccessoryBatteryEvent(args))
        assertNull(BluetoothProtocolParser.parseHfpAccessoryBatteryEvent(arrayOf(1, 2, 5)))
    }

    @Test
    fun `isNordOrOnePlusAudioDevice matches Nord Buds 3 Pro`() {
        assertTrue(BluetoothProtocolParser.isNordOrOnePlusAudioDevice("OnePlus Nord Buds 3 Pro"))
        assertTrue(BluetoothProtocolParser.isNordOrOnePlusAudioDevice("Nord Buds 3"))
        assertFalse(BluetoothProtocolParser.isNordOrOnePlusAudioDevice("Generic Speaker"))
    }
}
