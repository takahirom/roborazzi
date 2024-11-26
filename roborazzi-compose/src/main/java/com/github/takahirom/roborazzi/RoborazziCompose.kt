package com.github.takahirom.roborazzi

import android.app.Activity
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.test.core.app.ActivityScenario
import java.io.File

fun captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  content: @Composable () -> Unit,
) {
  captureRoboImage(
    file = fileWithRecordFilePathStrategy(filePath),
    roborazziOptions = roborazziOptions,
    content = content
  )
}

fun captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  content: @Composable () -> Unit,
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  registerActivityToRobolectricIfNeeded()

  val activityScenario = ActivityScenario.launch(RoborazziTransparentActivity::class.java)
  activityScenario.use {
    activityScenario.captureRoboImage(file, roborazziOptions){
      content()
    }

    // Closing the activity is necessary to prevent memory leaks.
    // If multiple captureRoboImage calls occur in a single test,
    // they can lead to an activity leak.
  }
}

fun captureRoboImage(
  filePath: String,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  content: (ActivityScenario<out Activity>) -> @Composable () -> Unit,
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  registerActivityToRobolectricIfNeeded()

  val activityScenario = ActivityScenario.launch(RoborazziTransparentActivity::class.java)

  activityScenario.use {
    val sizedPreview = content(activityScenario)
    activityScenario.captureRoboImage(filePath, roborazziOptions){
      sizedPreview()
    }

    // Closing the activity is necessary to prevent memory leaks.
    // If multiple captureRoboImage calls occur in a single test,
    // they can lead to an activity leak.
  }
}


private fun ActivityScenario<out ComponentActivity>.captureRoboImage(
  filePath: String,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  preview: @Composable () -> Unit,
) {
  captureRoboImage(
    file = fileWithRecordFilePathStrategy(filePath),
    roborazziOptions = roborazziOptions,
    preview = preview
  )
}

private fun ActivityScenario<out ComponentActivity>.captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  preview: @Composable () -> Unit,
) {
  
  onActivity { activity ->
    activity.setContent(content = { preview() })

    val composeView = activity.window.decorView
      .findViewById<ViewGroup>(android.R.id.content)
      .getChildAt(0) as ComposeView

    val viewRootForTest = composeView.getChildAt(0) as ViewRootForTest
    viewRootForTest.view.captureRoboImage(file, roborazziOptions)
  }
}