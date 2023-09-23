package com.github.takahirom.roborazzi.sample

import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.*
import com.github.takahirom.roborazzi.RoborazziRule.Options
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RuleTestWithPath {
  @get:Rule
  val roborazziRule = RoborazziRule(
    options = Options(
      outputDirectoryPath = "$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/custom_outputDirectoryPath",
      outputFileProvider = { description, directory, fileExtension ->
        File(
          directory,
          "custom_outputFileProvider-${description.testClass.name}.${description.methodName}.$fileExtension"
        )
      },
      roborazziOptions = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
          outputDirectoryPath = "$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/custom_compare_outputDirectoryPath",
        )
      )
    ),
  )

  @Test
  fun captureRoboImage() {
    launch(MainActivity::class.java)
    onView(isRoot()).captureRoboImage()
  }

  @Test
  fun captureRoboImageWithPath() {
    launch(MainActivity::class.java)
    onView(isRoot()).captureRoboImage("$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/custom_file.png")
  }

  @Test
  fun shouldNotUseVerifyTestReporter() {
    launch(MainActivity::class.java)
    val currentCaptureRepoter = provideRoborazziContext().options.reportOptions.captureResultReporter
    assert(
      currentCaptureRepoter !is RoborazziOptions.CaptureResultReporter.DefaultCaptureResultReporter
    )
  }
}
