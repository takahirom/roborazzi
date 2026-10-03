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
@OptIn(InternalRoborazziApi::class)
fun captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  roborazziComposeOptions: RoborazziComposeOptions = RoborazziComposeOptions(),
  content: @Composable () -> Unit,
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  runWithRoborazziComposeActivity(roborazziComposeOptions, content) { activityScenario, configuredContent ->
    activityScenario.captureRoboImage(
      file = file,
      roborazziOptions = roborazziOptions,
      doBeforeCapture = { roborazziComposeOptions.beforeCapture() },
      content = { configuredContent() }
    )
  }
}

/**
 * Launches the Roborazzi activity for [roborazziComposeOptions], applies the options to
 * [content] and runs [block] with the scenario and the configured content. The caller decides
 * what to do with them (capture one image, record a video, ...).
 *
 * Environment changes (qualifiers, font scale) made by the options are restored and the
 * scenario is closed after [block] returns or throws, and `afterCapture()` of the options runs
 * even when [block] fails.
 */
@InternalRoborazziApi
fun runWithRoborazziComposeActivity(
  roborazziComposeOptions: RoborazziComposeOptions,
  content: @Composable () -> Unit,
  block: (
    activityScenario: ActivityScenario<out ComponentActivity>,
    configuredContent: @Composable () -> Unit,
  ) -> Unit,
) {
  val savedQualifiers = RuntimeEnvironment.getQualifiers()
  val savedFontScale = RuntimeEnvironment.getFontScale()
  try {
    // Apply the environment before launch; changing it afterwards recreates the Activity.
    roborazziComposeOptions.applySetup()
    launchRoborazziActivity(roborazziComposeOptions) { activityScenario ->
      val configuredContent =
        roborazziComposeOptions.configuredAfterSetup(activityScenario) { content() }
      try {
        block(activityScenario, configuredContent)
      } finally {
        roborazziComposeOptions.afterCapture()
      }
    }
  } finally {
    // Restore only after the scenario has closed, so cleanup does not recreate its activity.
    RuntimeEnvironment.setQualifiers(savedQualifiers)
    if (RuntimeEnvironment.getFontScale() != savedFontScale) {
      RuntimeEnvironment.setFontScale(savedFontScale)
    }
  }
}

/**
 * Sets [content] as the content of the activity of this scenario. Used by callers of
 * [runWithRoborazziComposeActivity] that drive the UI themselves instead of capturing once.
 */
@InternalRoborazziApi
fun ActivityScenario<out ComponentActivity>.setRoborazziContent(content: @Composable () -> Unit) {
  onActivity { activity -> activity.setContent(content = { content() }) }
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
      doBeforeScreenCapture = { doBeforeCapture() },
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
