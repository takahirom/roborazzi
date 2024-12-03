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
import androidx.test.espresso.Espresso
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
  if (!roborazziOptions.taskType.isEnabled()) return
  registerRoborazziActivityToRobolectricIfNeeded()
  // Views needs to be laid out before we can capture them
  Espresso.onIdle()

  val activityScenario = ActivityScenario.launch(RoborazziTransparentActivity::class.java)
  activityScenario.use {
    activityScenario.onActivity { activity ->
      activity.setContent(content = content)
      val windowRoots = fetchRobolectricWindowRoots()
      if (windowRoots.size <= 1) {
        val composeView = activity.window.decorView
          .findViewById<ViewGroup>(android.R.id.content)
          .getChildAt(0) as ComposeView
        val viewRootForTest = composeView.getChildAt(0) as ViewRootForTest
        viewRootForTest.view.captureRoboImage(file, roborazziOptions)
      } else {
        // For dialog
//        windowRoots[1].decorView.rootView.captureRoboImage(file, roborazziOptions)
//        captureScreenRoboImage()
        captureRootsInternal(windowRoots.drop(1), roborazziOptions, file)
      }
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
