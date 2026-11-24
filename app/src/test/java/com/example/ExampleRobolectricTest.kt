package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app name matches NordBuds Premium`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NordBuds Premium", appName)
    }

    @Test
    fun `navigation tabs switch between Dashboard, Audio, Updates, and Diagnostics`() {
        // Advance past splash overlay
        composeRule.mainClock.advanceTimeBy(1600L)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_tab_audio").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("AUDIO & DSP").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_updates").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("UPDATE CENTER").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_diagnostics").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("SYSTEM & DIAGNOSTICS").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_dashboard").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Battery Telemetry").assertIsDisplayed()
    }
}
