package com.github.takahirom.roborazzi

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.ui.layout.Layout
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w200dp-h400dp-notnight-160dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeConfigurationRestorationTest {
  @get:Rule val temporaryFolder = TemporaryFolder()

  @Test fun restorationDoesNotRecreateActivityAfterCapture() {
    val qualifiers = RuntimeEnvironment.getQualifiers()
    val observer = ActivityObserver()
    val application = RuntimeEnvironment.getApplication()
    application.registerActivityLifecycleCallbacks(observer)
    try {
      var createdAtCaptureEnd = -1
      var capturedScenario: ActivityScenario<out Activity>? = null
      var capturedActivity: Activity? = null
      capture(2f,
        object : RoborazziComposeActivityScenarioOption {
          override fun configureWithActivityScenario(scenario: ActivityScenario<out Activity>) {
            capturedScenario = scenario
          }
        },
        object : RoborazziComposeCaptureOption {
          override fun beforeCapture() = Unit
          override fun afterCapture() {
            createdAtCaptureEnd = observer.created
            val scenario = checkNotNull(capturedScenario)
            scenario.onActivity { capturedActivity = it }
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
          }
        }
      )
      assertEquals(createdAtCaptureEnd, observer.created)
      assertSame(capturedActivity, observer.lastDestroyed)
      assertEquals(2f, observer.fontAtDestroy, 0f)
      assertEquals(Configuration.UI_MODE_NIGHT_YES, observer.nightAtDestroy)
      assertEquals(Lifecycle.State.DESTROYED, capturedScenario!!.state)
      assertRestored(qualifiers, 1f)
    } finally {
      application.unregisterActivityLifecycleCallbacks(observer)
    }
  }

  @Test fun consecutiveCapturesRestoreOriginalNonDefaultFontScale() {
    RuntimeEnvironment.setFontScale(1.25f)
    val qualifiers = RuntimeEnvironment.getQualifiers()
    capture(2f)
    assertRestored(qualifiers, 1.25f)
    capture(1.5f)
    assertRestored(qualifiers, 1.25f)
  }

  @Test fun beforeCaptureFailureStillRestoresAndRunsAfterCapture() {
    val qualifiers = RuntimeEnvironment.getQualifiers()
    val failure = ExpectedFailure()
    var afterCalled = false
    assertFailure(failure) {
      capture(2f, object : RoborazziComposeCaptureOption {
        override fun beforeCapture() { throw failure }
        override fun afterCapture() { afterCalled = true }
      })
    }
    assertTrue(afterCalled)
    assertRestored(qualifiers, 1f)
    capture(1.5f)
    assertRestored(qualifiers, 1f)
  }

  @Test fun afterCaptureFailureStillRestores() {
    val qualifiers = RuntimeEnvironment.getQualifiers()
    val failure = ExpectedFailure()
    assertFailure(failure) {
      capture(2f, object : RoborazziComposeCaptureOption {
        override fun beforeCapture() = Unit
        override fun afterCapture() { throw failure }
      })
    }
    assertRestored(qualifiers, 1f)
  }

  @Test fun configurationFailureClosesScenarioAndRestores() {
    val qualifiers = RuntimeEnvironment.getQualifiers()
    val failure = ExpectedFailure()
    var capturedScenario: ActivityScenario<out Activity>? = null
    assertFailure(failure) {
      capture(2f, object : RoborazziComposeActivityScenarioOption {
        override fun configureWithActivityScenario(scenario: ActivityScenario<out Activity>) {
          capturedScenario = scenario
          throw failure
        }
      })
    }
    assertEquals(Lifecycle.State.DESTROYED, capturedScenario!!.state)
    assertRestored(qualifiers, 1f)
  }

  private fun capture(fontScale: Float, vararg options: RoborazziComposeOption) {
    captureRoboImage(
      file = temporaryFolder.newFile("capture-${captureIndex++}.png"),
      roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
      roborazziComposeOptions = RoborazziComposeOptions {
        size(20, 20)
        fontScale(fontScale)
        uiMode(Configuration.UI_MODE_NIGHT_YES)
        options.forEach { addOption(it) }
      }
    ) {
      Layout(content = {}) { _, constraints ->
        layout(constraints.minWidth, constraints.minHeight) {}
      }
    }
  }

  private var captureIndex = 0
  private class ExpectedFailure : RuntimeException()

  private fun assertFailure(expected: ExpectedFailure, block: () -> Unit) {
    try {
      block()
      fail("Expected capture failure")
    } catch (actual: ExpectedFailure) {
      assertSame(expected, actual)
    }
  }

  @Suppress("DEPRECATION") // SDK 33 exposes font scaling through scaledDensity.
  private fun assertRestored(qualifiers: String, fontScale: Float) {
    assertEquals(qualifiers, RuntimeEnvironment.getQualifiers())
    assertEquals(fontScale, RuntimeEnvironment.getFontScale(), 0f)
    val resources = RuntimeEnvironment.getApplication().resources
    assertEquals(fontScale, resources.configuration.fontScale, 0f)
    assertEquals(resources.displayMetrics.density * fontScale,
      resources.displayMetrics.scaledDensity, 0.001f)
  }

  private class ActivityObserver : Application.ActivityLifecycleCallbacks {
    var created = 0
    var lastDestroyed: Activity? = null
    var fontAtDestroy = 0f
    var nightAtDestroy = 0
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) { created++ }
    override fun onActivityDestroyed(activity: Activity) {
      lastDestroyed = activity
      fontAtDestroy = activity.resources.configuration.fontScale
      nightAtDestroy = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    }
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
  }
}
