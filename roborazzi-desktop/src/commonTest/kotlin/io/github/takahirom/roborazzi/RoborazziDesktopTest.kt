package io.github.takahirom.roborazzi

import androidx.compose.material3.Text
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.isRoot
import java.io.File
import org.junit.Test

class RoborazziDesktopTest {

  @Test
  fun testJsonSerialization() {
    captureRoboImage(
      test = {
        this as DesktopComposeUiTest
        onNode(isRoot()).captureToImage()
      }
      content = {
        Text("Hello")
      }
    )
  }
}