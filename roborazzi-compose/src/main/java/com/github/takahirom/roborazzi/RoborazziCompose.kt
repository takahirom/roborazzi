package com.github.takahirom.roborazzi

import android.app.Application
import android.content.ComponentName
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.robolectric.Shadows

fun captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath("png"),
  roborazziOptions: RoborazziOptions = RoborazziOptions(),
  content: @Composable () -> Unit,
) {
  captureRoboImage(
    file = File(filePath),
    roborazziOptions = roborazziOptions,
    content = content
  )
}

fun captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = RoborazziOptions(),
  content: @Composable () -> Unit,
) {
  if (!roborazziEnabled()) return
  registerRoborazziActivityToRobolectricIfNeeded()
  val activityScenario = ActivityScenario.launch(RoborazziTransparentActivity::class.java)
  activityScenario.onActivity { activity ->
    activity.setContent(content = content)
    val composeView = activity.window.decorView
      .findViewById<ViewGroup>(android.R.id.content)
      .getChildAt(0) as ComposeView
    val viewRootForTest = composeView.getChildAt(0) as ViewRootForTest
    viewRootForTest.view.captureRoboImage(file, roborazziOptions)
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
