package com.github.takahirom.roborazzi

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowDisplay
import java.io.File
import kotlin.math.roundToInt

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
  registerRoborazziActivityToRobolectricIfNeeded(RoborazziTransparentActivity::class.java)
  val activityScenario = ActivityScenario.launch(RoborazziTransparentActivity::class.java)
  activityScenario.use {
    activityScenario.onActivity { activity ->
      activity.setContent(content = content)
      val composeView = activity.window.decorView
        .findViewById<ViewGroup>(android.R.id.content)
        .getChildAt(0) as ComposeView
      val viewRootForTest = composeView.getChildAt(0) as ViewRootForTest
      viewRootForTest.view.captureRoboImage(file, roborazziOptions)
    }

    // Closing the activity is necessary to prevent memory leaks.
    // If multiple captureRoboImage calls occur in a single test,
    // they can lead to an activity leak.
  }
}

/**
 * Workaround for https://github.com/takahirom/roborazzi/issues/100
 */
internal fun registerRoborazziActivityToRobolectricIfNeeded(
  activityClass: Class<out Activity>
) {
  try {
    val appContext: Application = ApplicationProvider.getApplicationContext()
    Shadows.shadowOf(appContext.packageManager).addActivityIfNotPresent(
      ComponentName(
        appContext.packageName,
        activityClass::class.java.name,
      )
    )
  } catch (e: ClassNotFoundException) {
    // Configured to run even without Robolectric
    e.printStackTrace()
  }
}
