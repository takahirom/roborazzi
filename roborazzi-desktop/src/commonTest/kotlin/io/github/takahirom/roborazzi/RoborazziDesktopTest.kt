package io.github.takahirom.roborazzi

import androidx.compose.material3.Text
import java.io.File
import org.junit.Test

class RoborazziDesktopTest {

  @Test
  fun testJsonSerialization() {
    captureRoboImage(
      content = {
        Text("Hello")
      }
    )
  }
}