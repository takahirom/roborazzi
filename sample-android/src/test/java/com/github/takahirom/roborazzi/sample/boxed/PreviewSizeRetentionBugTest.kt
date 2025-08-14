package com.github.takahirom.roborazzi.sample.boxed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.github.takahirom.roborazzi.size
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import android.graphics.BitmapFactory
import org.junit.Assert

/**
 * Test to reproduce the bug where Preview annotation attributes (heightDp/widthDp)
 * incorrectly affect subsequent previews when using ComposablePreviewScanner.
 * 
 * Bug description: When one preview has heightDp=600, subsequent previews 
 * without explicit size specifications also get rendered at 600dp height 
 * instead of default size.
 * 
 * This test should FAIL before the fix, demonstrating the bug.
 */
@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30])
class PreviewSizeRetentionBugTest {

    @Composable
    fun TestContent(text: String, color: Color = Color.Blue) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color.White
            )
        }
    }

    private fun getImageDimensions(filePath: String): Pair<Int, Int> {
        val file = File(filePath)
        if (!file.exists()) {
            throw AssertionError("Image file does not exist: $filePath")
        }
        val bitmap = BitmapFactory.decodeFile(filePath)
            ?: throw AssertionError("Failed to decode bitmap: $filePath")
        return bitmap.width to bitmap.height
    }

    @Test
    fun testPreviewSizeRetentionBug_shouldFailBeforeFix() {
        boxedEnvironment {
            // Use absolute path for output directory
            val outputDir = File(roborazziSystemPropertyOutputDirectory()).absolutePath
            
            // Files to clean up
            val file1 = "$outputDir/preview1_sized.png"
            val file2 = "$outputDir/preview2_default.png"
            val file3 = "$outputDir/preview3_sized.png"
            val file4 = "$outputDir/preview4_default.png"
            
            try {
                // Capture 1: With explicit size (600x800)
                captureRoboImage(
                    filePath = file1,
                    roborazziOptions = RoborazziOptions(
                        taskType = RoborazziTaskType.Record
                    ),
                    roborazziComposeOptions = RoborazziComposeOptions {
                        size(widthDp = 600, heightDp = 800)
                    }
                ) {
                    TestContent("Preview 1 - Sized (600x800)", Color.Red)
                }
                
                // Get actual dimensions of first capture
                val (width1, height1) = getImageDimensions(file1)
                
                // Calculate expected pixel size based on density (assuming default density of 1.0)
                // In Robolectric, default density is usually 1.0, so 600dp = 600px
                val expectedWidth1 = 600
                val expectedHeight1 = 800
                
                // Verify first capture has expected size
                Assert.assertEquals(
                    "Unexpected width for Preview 1",
                    expectedWidth1, width1
                )
                Assert.assertEquals(
                    "Unexpected height for Preview 1",
                    expectedHeight1, height1
                )
                
                // Capture 2: WITHOUT explicit size (should use default)
                captureRoboImage(
                    filePath = file2,
                    roborazziOptions = RoborazziOptions(
                        taskType = RoborazziTaskType.Record
                    ),
                    roborazziComposeOptions = RoborazziComposeOptions {
                        // No size specified - should use default
                    }
                ) {
                    TestContent("Preview 2 - Default Size", Color.Blue)
                }
                
                // Get actual dimensions of second capture
                val (width2, height2) = getImageDimensions(file2)
                
                // BUG: Preview 2 will have the same size as Preview 1 (600x800)
                // instead of using default size
                
                // This assertion should FAIL, demonstrating the bug
                Assert.assertFalse(
                    "BUG: Preview 2 retained Preview 1's size ${width1}x${height1} instead of using default",
                    width2 == width1 && height2 == height1
                )
                
                // Additional test: Capture 3 with different size, then Capture 4 without size
                captureRoboImage(
                    filePath = file3,
                    roborazziOptions = RoborazziOptions(
                        taskType = RoborazziTaskType.Record
                    ),
                    roborazziComposeOptions = RoborazziComposeOptions {
                        size(widthDp = 400, heightDp = 600)
                    }
                ) {
                    TestContent("Preview 3 - Sized (400x600)", Color.Green)
                }
                
                val (width3, height3) = getImageDimensions(file3)
                
                captureRoboImage(
                    filePath = file4,
                    roborazziOptions = RoborazziOptions(
                        taskType = RoborazziTaskType.Record
                    ),
                    roborazziComposeOptions = RoborazziComposeOptions {
                        // No size specified - should use default
                    }
                ) {
                    TestContent("Preview 4 - Default Size", Color.Yellow)
                }
                
                val (width4, height4) = getImageDimensions(file4)
                
                // This should also FAIL - Preview 4 will have Preview 3's size
                Assert.assertFalse(
                    "BUG: Preview 4 retained Preview 3's size ${width3}x${height3} instead of using default",
                    width4 == width3 && height4 == height3
                )
            } finally {
                // Clean up - runs even if assertions fail
                listOf(file1, file2, file3, file4).forEach { File(it).delete() }
            }
        }
    }
    
    @Test
    fun testMultiplePreviewsWithSameCode_simulatesComposablePreviewScanner() {
        boxedEnvironment {
            val outputDir = File(roborazziSystemPropertyOutputDirectory()).absolutePath
            
            // This simulates what ComposablePreviewScanner does:
            // It calls the same composable multiple times with different configurations
            
            val results = mutableListOf<Pair<String, Pair<Int, Int>>>()
            val filesToDelete = mutableListOf<String>()
            
            try {
                // Simulate scanning multiple @Preview annotations
                val previews = listOf(
                    Triple("preview1", 600, 800),  // @Preview(widthDp = 600, heightDp = 800)
                    Triple("preview2", 0, 0),       // @Preview (no size specified)
                    Triple("preview3", 400, 600),  // @Preview(widthDp = 400, heightDp = 600)
                    Triple("preview4", 0, 0)        // @Preview (no size specified)
                )
                
                for ((name, widthDp, heightDp) in previews) {
                    val filePath = "$outputDir/${name}.png"
                    filesToDelete.add(filePath)
                    
                    // Simulate what ComposablePreviewScanner does for each preview
                    val options = RoborazziComposeOptions {
                        if (widthDp > 0 || heightDp > 0) {
                            size(widthDp = widthDp, heightDp = heightDp)
                        }
                    }
                    
                    captureRoboImage(
                        filePath = filePath,
                        roborazziOptions = RoborazziOptions(
                            taskType = RoborazziTaskType.Record
                        ),
                        roborazziComposeOptions = options
                    ) {
                        TestContent("$name (${if (widthDp > 0) "${widthDp}x${heightDp}" else "default"})")
                    }
                    
                    val dimensions = getImageDimensions(filePath)
                    results.add(name to dimensions)
                }
                
                // Check for the bug
                val preview1Size = results[0].second
                val preview2Size = results[1].second
                val preview3Size = results[2].second
                val preview4Size = results[3].second
                
                // These assertions should FAIL, demonstrating the bug
                Assert.assertNotEquals(
                    "BUG: preview2 retained preview1's size ${preview1Size.first}x${preview1Size.second}",
                    preview1Size, preview2Size
                )
                Assert.assertNotEquals(
                    "BUG: preview4 retained preview3's size ${preview3Size.first}x${preview3Size.second}",
                    preview3Size, preview4Size
                )
            } finally {
                // Clean up - runs even if assertions fail
                filesToDelete.forEach { File(it).delete() }
            }
        }
    }
}