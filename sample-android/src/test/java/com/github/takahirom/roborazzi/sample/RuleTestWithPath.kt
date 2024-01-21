package com.github.takahirom.roborazzi.sample

import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.*
import com.github.takahirom.roborazzi.RoborazziRule.Options
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RuleTestWithPath {
  @get:Rule
  val roborazziRule = RoborazziRule(
    captureRoot = onView(isRoot()),
    options = Options(
      captureType = RoborazziRule.CaptureType.LastImage(),
      outputDirectoryPath = "${roborazziSystemPropertyOutputDirectory()}/custom_outputDirectoryPath",
      outputFileProvider = { description, directory, fileExtension ->
        File(
          directory,
          "custom_outputFileProvider-${description.testClass.name}.${description.methodName}.$fileExtension"
        )
      },
      roborazziOptions = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
          outputDirectoryPath = "${roborazziSystemPropertyOutputDirectory()}/custom_compare_outputDirectoryPath",
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
    onView(isRoot()).captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/custom_file.png")
  }

  @Test
  fun roboOutputNameTest() {
    // For last image
    launch(MainActivity::class.java)
    assert(roboOutputName() == "com.github.takahirom.roborazzi.sample.RuleTestWithPath.roboOutputNameTest")
  }
}
