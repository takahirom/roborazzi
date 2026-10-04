package com.github.takahirom.roborazzi.sample.boxed

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.material3.Text
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.github.takahirom.roborazzi.size
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [30],
  qualifiers = RobolectricDeviceQualifiers.NexusOne
)
class CaptureRoboImageSetupBeforeLaunchTest {
  @Test
  fun setupOptionsAreAppliedBeforeTheActivityIsCreated() {
    boxedEnvironment {
      val configuration = captureAndGetCreatedConfiguration {
        size(widthDp = 300, heightDp = 400)
        fontScale(1.5f)
      }

      assertEquals(300, configuration.screenWidthDp)
      assertEquals(400, configuration.screenHeightDp)
      assertEquals(1.5f, configuration.fontScale)
    }
  }

  @Test
  fun widthOnlySizeIsAppliedBeforeTheActivityIsCreated() {
    boxedEnvironment {
      val configuration = captureAndGetCreatedConfiguration {
        size(widthDp = 300)
      }

      assertEquals(300, configuration.screenWidthDp)
    }
  }

  private fun captureAndGetCreatedConfiguration(
    block: RoborazziComposeOptions.Builder.() -> Unit
  ): Configuration {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val createdConfigurations = mutableListOf<Configuration>()
    val callbacks = object : Application.ActivityLifecycleCallbacks {
      override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        createdConfigurations += Configuration(activity.resources.configuration)
      }

      override fun onActivityStarted(activity: Activity) = Unit
      override fun onActivityResumed(activity: Activity) = Unit
      override fun onActivityPaused(activity: Activity) = Unit
      override fun onActivityStopped(activity: Activity) = Unit
      override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
      override fun onActivityDestroyed(activity: Activity) = Unit
    }
    val qualifiersBefore = RuntimeEnvironment.getQualifiers()
    val output =
      File("${roborazziSystemPropertyOutputDirectory()}/${this::class.qualifiedName}.png")
    application.registerActivityLifecycleCallbacks(callbacks)
    try {
      captureRoboImage(
        file = output,
        roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        roborazziComposeOptions = RoborazziComposeOptions(block),
      ) {
        Text("Hello")
      }
    } finally {
      application.unregisterActivityLifecycleCallbacks(callbacks)
      output.delete()
    }

    // A setup change after launch would recreate the Activity and add a second entry.
    assertEquals(1, createdConfigurations.size)
    assertEquals(qualifiersBefore, RuntimeEnvironment.getQualifiers())
    return createdConfigurations.single()
  }
}
