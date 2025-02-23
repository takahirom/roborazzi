package com.github.takahirom.roborazzi

import android.app.Application
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows

/**
 * Workaround for https://github.com/takahirom/roborazzi/issues/100
 */
@ExperimentalRoborazziApi
fun registerRoborazziActivityToRobolectricIfNeeded() {
  try {
    val appContext: Application = ApplicationProvider.getApplicationContext()
    Shadows.shadowOf(appContext.packageManager).addActivityIfNotPresent(
      ComponentName(
        appContext,
        RoborazziActivity::class.java,
      )
    )
  } catch (e: ClassNotFoundException) {
    // Configured to run even without Robolectric
    e.printStackTrace()
  }
}