package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.view.MetroPlayerApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(AndroidJUnit4::class)
@Config(instrumentedPackages = ["androidx.loader.content"])
class AppCrashTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testAppLaunch() {
        ShadowLog.stream = System.out
        try {
            composeTestRule.setContent {
                MetroPlayerApp()
            }
            composeTestRule.waitForIdle()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
