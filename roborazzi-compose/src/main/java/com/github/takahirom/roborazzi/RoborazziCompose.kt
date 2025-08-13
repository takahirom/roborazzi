package com.github.takahirom.roborazzi

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RuntimeEnvironment
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
  captureRoboImage(
    file = file,
    roborazziOptions = roborazziOptions,
    roborazziComposeOptions = RoborazziComposeOptions(),
    content = content
  )
}

@ExperimentalRoborazziApi
fun captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  roborazziComposeOptions: RoborazziComposeOptions = RoborazziComposeOptions(),
  content: @Composable () -> Unit,
) {
  captureRoboImage(
    file = fileWithRecordFilePathStrategy(filePath),
    roborazziOptions = roborazziOptions,
    roborazziComposeOptions = roborazziComposeOptions,
    content = content
  )
}

@ExperimentalRoborazziApi
fun captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  roborazziComposeOptions: RoborazziComposeOptions = RoborazziComposeOptions(),
  content: @Composable () -> Unit,
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  launchRoborazziActivity(roborazziComposeOptions) { activityScenario ->
    // Save current qualifiers before any modifications
    val savedQualifiers = RuntimeEnvironment.getQualifiers()
    
    val configuredContent = roborazziComposeOptions
      .configured(activityScenario) {
        content()
      }
    try {
      activityScenario.captureRoboImage(
        file = file,
        roborazziOptions = roborazziOptions,
        doBeforeCapture = { roborazziComposeOptions.beforeCapture() },
        content = { configuredContent() }
      )
    } finally {
      roborazziComposeOptions.afterCapture()
      // Restore original qualifiers
      RuntimeEnvironment.setQualifiers(savedQualifiers)
    }
  }
}

private fun launchRoborazziActivity(
  roborazziComposeOptions: RoborazziComposeOptions,
  block: (ActivityScenario<out ComponentActivity>) -> Unit = {}
) {
  val activityScenario = roborazziComposeOptions.createScenario {
    createActivityScenario(theme = android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
  }

  // Closing the activity is necessary to prevent memory leaks.
  // If multiple captureRoboImage calls occur in a single test,
  // they can lead to an activity leak.
  return activityScenario.use { block(activityScenario) }
}

internal fun createActivityScenario(theme: Int): ActivityScenario<out ComponentActivity> {
  registerRoborazziActivityToRobolectricIfNeeded()
  return ActivityScenario.launch(
    RoborazziActivity.createIntent(
      context = ApplicationProvider.getApplicationContext(),
      theme = theme
    )
  )
}


private fun ActivityScenario<out androidx.activity.ComponentActivity>.captureRoboImage(
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
  doBeforeCapture: () -> Unit = {},
  content: @Composable () -> Unit,
) {
  onActivity { activity ->
    activity.setContent(content = { content() })
    captureScreenIfMultipleWindows(
      file = file,
      roborazziOptions = roborazziOptions,
      captureSingleComponent = {
        val composeView = activity.window.decorView
          .findViewById<ViewGroup>(android.R.id.content)
          .getChildAt(0) as ComposeView

        @SuppressLint("VisibleForTests")
        val viewRootForTest = composeView.getChildAt(0) as ViewRootForTest
        doBeforeCapture()
        viewRootForTest.view.captureRoboImage(file, roborazziOptions)
      }
    )
  }
}
