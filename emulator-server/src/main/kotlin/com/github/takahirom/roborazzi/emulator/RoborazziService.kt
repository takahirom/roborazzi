package com.github.takahirom.roborazzi.emulator

import android.content.Context
import android.content.Intent
import androidx.compose.ui.tooling.PreviewActivity
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.github.takahirom.roborazzi.emulator.RoboInstances.executeOnMain
import kotlinx.coroutines.CoroutineDispatcher
import org.robolectric.Robolectric
import roborazzi.emulator.ComposePreview
import roborazzi.emulator.RoborazziWireGrpc


class RoborazziService(val dispatcher: CoroutineDispatcher) :
  RoborazziWireGrpc.RoborazziImplBase() {

  val androidContext: Context
    get() = InstrumentationRegistry.getInstrumentation().targetContext

  inline fun <reified T> systemService(): T {
    return ContextCompat.getSystemService(androidContext, T::class.java)!!
  }

  override suspend fun launchComposePreview(request: ComposePreview) = executeOnMain {
    println("$request")

    try {
      val activityController = Robolectric.buildActivity(PreviewActivity::class.java)

      activityController.newIntent(Intent().apply {
        putExtra("composable", request.previewMethod)
      })
      activityController.setup()

      val activity: PreviewActivity = activityController.get()
      println(activity.intent)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
}
