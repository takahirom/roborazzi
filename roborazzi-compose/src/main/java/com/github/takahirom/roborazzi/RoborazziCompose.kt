package com.github.takahirom.roborazzi

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
    applierBuilder = {},
    content = content
  )
}

fun captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  content: @Composable () -> Unit,
) {
  captureRoboImage(
    file = file,
    roborazziOptions = roborazziOptions,
    applierBuilder = {},
    content = content
  )
}

@ExperimentalRoborazziApi
fun captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  applierBuilder: RoborazziComposeApplierBuilder.() -> Unit = {
  },
  content: @Composable () -> Unit,
) {
  captureRoboImage(
    file = fileWithRecordFilePathStrategy(filePath),
    roborazziOptions = roborazziOptions,
    applierBuilder = applierBuilder,
    content = content
  )
}

@ExperimentalRoborazziApi
fun captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  applierBuilder: RoborazziComposeApplierBuilder.() -> Unit = {
  },
  content: @Composable () -> Unit,
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  launchRoborazziTransparentActivity { activityScenario ->
    val applierBuilder = RoborazziComposeApplierBuilder().apply(applierBuilder)
    val appliedContent = applierBuilder.apply(activityScenario) {
      content()
    }
    activityScenario.captureRoboImage(file, roborazziOptions) {
      appliedContent()
    }
  }
}

private fun launchRoborazziTransparentActivity(block: (ActivityScenario<RoborazziTransparentActivity>) -> Unit = {}) {
  registerActivityToRobolectricIfNeeded()

  val activityScenario = ActivityScenario.launch(RoborazziTransparentActivity::class.java)

  // Closing the activity is necessary to prevent memory leaks.
  // If multiple captureRoboImage calls occur in a single test,
  // they can lead to an activity leak.
  return activityScenario.use { block(activityScenario) }
}


private fun ActivityScenario<out ComponentActivity>.captureRoboImage(
  filePath: String,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  content: @Composable () -> Unit,
) {
  captureRoboImage(
    file = fileWithRecordFilePathStrategy(filePath),
    roborazziOptions = roborazziOptions,
    content = content
  )
}

private fun ActivityScenario<out ComponentActivity>.captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  content: @Composable () -> Unit,
) {

  onActivity { activity ->
    activity.setContent(content = { content() })

    val composeView = activity.window.decorView
      .findViewById<ViewGroup>(android.R.id.content)
      .getChildAt(0) as ComposeView

    val viewRootForTest = composeView.getChildAt(0) as ViewRootForTest
    viewRootForTest.view.captureRoboImage(file, roborazziOptions)
  }
}