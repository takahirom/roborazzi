package com.github.takahirom.roborazzi.sample.boxed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Minimal reproduction for issue #771: Multiple SDKs with LosslessWebPImageIoFormat
 * causes a ClassCastException.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [30, 35],
  qualifiers = RobolectricDeviceQualifiers.Pixel7
)
class RobolectricTestParameterInjectorWebPTest {

  @Test
  fun screenshotWithWebP() {
    captureRoboImage(
      roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(
          imageIoFormat = LosslessWebPImageIoFormat(),
        ),
      ),
    ) {
      Box(
        modifier = Modifier
          .size(100.dp)
          .background(Color.Red)
      )
    }
  }
}