package com.github.takahirom.roborazzi

import android.R
import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
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
  captureRoboImageComposeInternal(
    roborazziOptions = roborazziOptions,
    content = content,
    file = file,
    capture = { activity ->
      val composeView = activity.window.decorView
        .findViewById<ViewGroup>(R.id.content)
        .getChildAt(0) as ComposeView
      val viewRootForTest = composeView.getChildAt(0) as ViewRootForTest
      viewRootForTest.view.captureRoboImage(file, roborazziOptions)
    }
  )
  return
}

@ExperimentalRoborazziApi
fun captureScreenRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  content: @Composable () -> Unit,
) {
  captureScreenRoboImage(
    file = fileWithRecordFilePathStrategy(filePath),
    roborazziOptions = roborazziOptions,
    content = content
  )
}

fun captureScreenRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  content: @Composable () -> Unit,
) {
  captureRoboImageComposeInternal(
    roborazziOptions = roborazziOptions,
    content = content,
    file = file,
  ) {
    captureScreenRoboImage()
  }
  return
}

private fun captureRoboImageComposeInternal(
  roborazziOptions: RoborazziOptions,
  content: @Composable () -> Unit,
  file: File,
  capture: (activity: Activity) -> Unit,
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  registerRoborazziActivityToRobolectricIfNeeded()
  val activityScenario = ActivityScenario.launch(RoborazziTransparentActivity::class.java)
  activityScenario.use {
    activityScenario.onActivity { activity ->
      activity.setContent(content = content)

    }

    // Closing the activity is necessary to prevent memory leaks.
    // If multiple captureRoboImage calls occur in a single test,
    // they can lead to an activity leak.
  }
}

/**
 * Workaround for https://github.com/takahirom/roborazzi/issues/100
 */
private fun registerRoborazziActivityToRobolectricIfNeeded() {
  try {
    val appContext: Application = ApplicationProvider.getApplicationContext()
    Shadows.shadowOf(appContext.packageManager).addActivityIfNotPresent(
      ComponentName(
        appContext.packageName,
        RoborazziTransparentActivity::class.java.name,
      )
    )
  } catch (e: ClassNotFoundException) {
    // Configured to run even without Robolectric
    e.printStackTrace()
  }
}
