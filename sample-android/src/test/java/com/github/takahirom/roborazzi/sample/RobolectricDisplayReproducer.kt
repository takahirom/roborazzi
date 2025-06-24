package com.github.takahirom.roborazzi.sample

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay
import org.robolectric.shadows.ShadowLooper

/**
 * Reproduction test for Robolectric 4.15 display size behavior changes
 * 
 * This test reproduces the issue where Preview annotations with large widthDp and heightDp
 * values (like widthDp = 2000, heightDp = 1000) may behave differently after 
 * Robolectric version upgrade to 4.15.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RobolectricDisplayReproducer {
    
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()
    
    @org.junit.Before
    fun setUp() {
        // Enable Robolectric logging
        System.setProperty("robolectric.logging.enabled", "true")
        System.setProperty("robolectric.log", "stdout")
    }
    
    @Test
    fun testRobolectric415ResourcesSyncIssue() {
        println("\n=== Testing Robolectric 4.15 Resources Sync Issue ===")
        
        composeTestRule.activityRule.scenario.onActivity { activity ->
            val widthDp = 2000
            val heightDp = 1000
            val density = activity.resources.displayMetrics.density
            
            println("Target: ${widthDp}dp x ${heightDp}dp")
            
            // Apply shadow API like RoborazziComposeSizeOption does
            val display = ShadowDisplay.getDefaultDisplay()
            val shadowDisplay = shadowOf(display)
            
            val widthPx = (widthDp * density).toInt()
            val heightPx = (heightDp * density).toInt()
            
            shadowDisplay.setWidth(widthPx)
            shadowDisplay.setHeight(heightPx)
            
            activity.recreate()
            ShadowLooper.idleMainLooper()
        }
        
        // Test with Compose content to check if BoxWithConstraints gets correct size
        composeTestRule.setContent {
            TestAssertionComposable(expectedWidthDp = 2000, expectedHeightDp = 1000)
        }
        
        composeTestRule.waitForIdle()
    }
    
    @SuppressLint("UnusedBoxWithConstraintsScope")
    @Composable
    fun TestAssertionComposable(expectedWidthDp: Int, expectedHeightDp: Int) {
        val configuration = LocalConfiguration.current
        
        println("=== Assertion Test ===")
        println("Expected: ${expectedWidthDp}dp x ${expectedHeightDp}dp")
        println("Configuration: ${configuration.screenWidthDp}dp x ${configuration.screenHeightDp}dp")
        
        BoxWithConstraints {
            val actualWidthDp = maxWidth.value.toInt()
            val actualHeightDp = maxHeight.value.toInt()
            
            println("BoxWithConstraints: ${actualWidthDp}dp x ${actualHeightDp}dp")
            
            // These assertions will fail in Robolectric 4.15 due to Resources sync issue
            assert(actualWidthDp == expectedWidthDp) { 
                "Width mismatch: expected ${expectedWidthDp}dp, got ${actualWidthDp}dp" 
            }
            assert(actualHeightDp == expectedHeightDp) { 
                "Height mismatch: expected ${expectedHeightDp}dp, got ${actualHeightDp}dp" 
            }
            
            Text("Test assertion passed: ${actualWidthDp}dp x ${actualHeightDp}dp")
        }
    }
}