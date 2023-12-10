package com.github.takahirom.roborazzi.sample

import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [30],
  qualifiers = RobolectricDeviceQualifiers.NexusOne
)
class WindowCaptureTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun composeDialog() {
    composeTestRule.setContent {
      Column(
        modifier = androidx.compose.ui.Modifier
          .background(Color.Cyan)
          .fillMaxSize()
      ) {
        Text("Under the dialog")
        AlertDialog(
          onDismissRequest = { },
          title = { Text("ComposeAlertDialogTitle") },
          text = { Text("Text") },
          confirmButton = {
            Text("OK")
          },
          dismissButton = {
            Text("Cancel")
          }
        )
      }
    }

    captureScreenRoboImage()
  }

  @Test
  fun androidDialog() {
    composeTestRule.setContent {
      Column(
        modifier = androidx.compose.ui.Modifier
          .background(Color.Cyan)
          .fillMaxSize()
      ) {
        Text("Under the dialog")
        val context = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(Unit) {
          AlertDialog.Builder(context)
            .setTitle("ViewAlertDialogTitle")
            .setMessage("Text")
            .setPositiveButton("OK") { _, _ -> }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
        }
      }
    }

    captureScreenRoboImage()
  }

  @Test
  fun noDialog() {
    composeTestRule.setContent {
      Column(
        modifier = androidx.compose.ui.Modifier
          .background(Color.Cyan)
          .fillMaxSize()
      ) {
        Text("Content")
      }
    }

    captureScreenRoboImage()
  }
}
