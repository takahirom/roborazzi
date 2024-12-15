package com.github.takahirom.roborazzi.sample

import android.app.Activity
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziComposeActivityScenarioOption
import com.github.takahirom.roborazzi.RoborazziComposeComposableOption
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
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
class ComposeLambdaTest {
  @OptIn(ExperimentalRoborazziApi::class)
  @Test
  fun captureComposeLambdaImage() {
    captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/manual_compose.png") {
      Text("Hello Compose!")
    }
  }

  @OptIn(ExperimentalRoborazziApi::class)
  @Test
  fun captureComposeLambdaImageWithRoborazziComposeOptions() {
    captureRoboImage(
      "${roborazziSystemPropertyOutputDirectory()}/manual_compose_with_compose_options.png",
      roborazziComposeOptions = RoborazziComposeOptions {
        // We have several options to configure the test environment.
        fontScale(2f)

        /*
        We don't specify `inspectionMode` by default.
        The default value for `inspectionMode` in Compose is `false`.
        This is to maintain higher fidelity in tests.
        If you encounter issues integrating the library, you can set `inspectionMode` to `true`.

        inspectionMode(true)
         */

        // We can also configure the activity scenario and the composable content.
        addOption(
          object : RoborazziComposeComposableOption,
            RoborazziComposeActivityScenarioOption {
            override fun configureWithActivityScenario(scenario: ActivityScenario<out Activity>) {
              scenario.onActivity {
                it.window.decorView.setBackgroundColor(Color.BLUE)
              }
            }

            override fun configureWithComposable(content: @Composable () -> Unit): @Composable () -> Unit {
              return {
                Box(
                  Modifier
                    .padding(10.dp)
                    .background(color = androidx.compose.ui.graphics.Color.Red)
                    .padding(10.dp)
                ) {
                  content()
                }
              }
            }
          }
        )
      },
    ) {
      Text("Hello Compose!")
    }
  }
}