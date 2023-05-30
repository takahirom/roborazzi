package com.github.takahirom.roborazzi.sample

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
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
class NoComposeManualTest {
  @Test
  @Config(qualifiers = "+land")
  fun captureRoboImageSample() {
    // launch
    ActivityScenario.launch(MainActivity::class.java)
    // screen level image
    onView(ViewMatchers.isRoot())
      .captureRoboImage()
  }
}
