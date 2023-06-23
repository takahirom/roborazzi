package com.github.takahirom.roborazzi.sample

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.provideRoborazziContext

/**
 * sample of providing roborazzi options
 */
@OptIn(ExperimentalRoborazziApi::class)
class CustomRoborazziSampleTestRunner : AndroidJUnitRunner() {
  override fun onCreate(arguments: Bundle?) {
    provideRoborazziContext().setRunnerOverrideFileCreator { description, outputDirectory, name ->
      outputDirectory.resolve("custom_test_runner/${description.className}_${description.methodName}_$name.png")
    }
    // You can also override the output directory.
    provideRoborazziContext().setRunnerOverrideOutputDirectory("build/output/roborazzi/your_screenshot_dir")
    // You can also override the RoborazziOptions.
    provideRoborazziContext().setRunnerOverrideRoborazziOptions(
      RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(
          resizeScale = 0.5
        )
      )
    )

    super.onCreate(arguments)
  }

  override fun onDestroy() {
    super.onDestroy()
    // You should clear override settings. Otherwise, the override settings will be applied to the next test.
    provideRoborazziContext().clearRunnerOverrideFileCreator()
    provideRoborazziContext().clearRunnerOverrideOutputDirectory()
    provideRoborazziContext().clearRunnerOverrideRoborazziOptions()
  }
}