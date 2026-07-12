package com.github.takahirom.roborazzi.sample

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.RoborazziRule.Options
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h500dp")
class SnapshotIssue {
    @get:Rule
    val roborazziRule = RoborazziRule(
        options = Options(
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(
                    changeThreshold = 0.001F
                )
            )
        )
    )

    @Composable
    fun DialogContent() {
        AlertDialog(
            title = { Text("title") },
            text = { Text("body") },
            containerColor = Color.White,
            onDismissRequest = { },
            confirmButton = {
                TextButton(
                    onClick = { },
                ) {
                    Text("CANCEL")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { },
                ) {
                    Text("OK")
                }
            }
        )
    }

    @Composable
    fun WithMaterial3Theme(content: @Composable () -> Unit) {

        MaterialTheme(
            colorScheme = lightColorScheme(
                surface = Color.White,
                onSurface = Color.Black
            )
        ) {
            content()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun withTonalElevationDefault() {
        runComposeUiTest {
            setContent {
                WithMaterial3Theme {
                    DialogContent()
                }
            }

            onNode(isDialog()).captureRoboImage()
            captureScreenRoboImage()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun withTonalElevationDisabled() {
        runComposeUiTest {
            setContent {
                WithMaterial3Theme {
                    CompositionLocalProvider(LocalTonalElevationEnabled provides true) {
                        DialogContent()
                    }
                }
            }

            onNode(isDialog()).captureRoboImage()
            captureScreenRoboImage()
        }
    }
}