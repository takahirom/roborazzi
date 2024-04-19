package io.github.takahirom.roborazzi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

class IosTest {
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun test() {
    runComposeUiTest {
      setContent {
        MaterialTheme {
          Row {
            listOf(1.0F, 0.2F, 0.0F).forEach { alpha ->
              Column {
                Button(
                  modifier = Modifier.alpha(alpha),
                  onClick = { /*TODO*/ }) {
                  Text("Hello World5")
                }
                Box(
                  modifier = Modifier
                    .background(Color.Red.copy(alpha = alpha), MaterialTheme.shapes.small)
                    .size(100.dp),
                )
                Box(
                  modifier = Modifier
                    .background(Color.Green.copy(alpha = alpha), MaterialTheme.shapes.small)
                    .size(100.dp),
                )
                Box(
                  modifier = Modifier
                    .background(Color.Blue.copy(alpha = alpha), MaterialTheme.shapes.small)
                    .size(100.dp),
                )
              }
            }
          }
        }
      }
      onAllNodesWithText("Hello", substring = true)[0].captureRoboImage(
        composeUiTest = this,
        filePath = "ios_button.png"
      )
      onRoot().captureRoboImage(
        composeUiTest = this,
        filePath = "ios.png"
      )
    }
  }
}