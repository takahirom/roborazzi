package com.github.takahirom.roborazzi

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.espresso.BaseLayerComponent
import androidx.test.espresso.GraphHolder
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import org.robolectric.RuntimeEnvironment

@SuppressLint("WrongConstant")
fun ViewInteraction.roboNativeGraphicsEmulator(
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  System.setProperty("java.awt.headless", "false");
  val uiController = getUiController()
  perform(ImageCaptureViewAction(roborazziOptions) { canvas ->
    val onClick: (x: Int, y: Int) -> Unit = { x, y ->
      Handler(Looper.getMainLooper()).post {
        perform(
          GeneralClickAction(
            Tap.SINGLE,
            { view ->
              val screenPos = IntArray(2)
              view.getLocationOnScreen(screenPos)
              val screenX = screenPos[0] + x
              val screenY = screenPos[1] + y
              floatArrayOf(screenX.toFloat(), screenY.toFloat())
            },
            Press.FINGER,
            InputDevice.SOURCE_UNKNOWN,
            MotionEvent.BUTTON_PRIMARY
          )
        )
      }
    }
    var loop = true
    var isDump = false
    val nativeGraphicsEmulator = NativeGraphicsEmulator()
    nativeGraphicsEmulator.start(
      initialCanvas = canvas,
      onClick = onClick,
      onClose = {
        loop = false
      },
      onRotateButtonClicked = {
        onViewWithInteraction { view ->
          val activity =
            getActivity(view)
          val currentOrientation: Int =
            activity.resources.configuration.orientation
          val isPortraitOrUndefined =
            currentOrientation == Configuration.ORIENTATION_PORTRAIT || currentOrientation == Configuration.ORIENTATION_UNDEFINED
          if (isPortraitOrUndefined) {
            RuntimeEnvironment.setQualifiers("+land");
          } else {
            RuntimeEnvironment.setQualifiers("+port");
          }
        }
      },
      onDarkLightButtonClicked = {
        onViewWithInteraction { view ->
          val activity = getActivity(view)
          val currentNightMode: Int =
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
          val isNightMode = currentNightMode == Configuration.UI_MODE_NIGHT_YES
          if (isNightMode) {
            RuntimeEnvironment.setQualifiers("+notnight");
          } else {
            RuntimeEnvironment.setQualifiers("+night");
          }
        }
      },
      onSelectedDeviceChange = { device ->
        if (device == "Select device") return@start
        onViewWithInteraction {
          val qualifier =
            RobolectricDeviceQualifiers.allDevices().firstOrNull { it.name == device }?.qualifier
              ?: return@onViewWithInteraction
          RuntimeEnvironment.setQualifiers(qualifier)
        }
      },
      onDump = {
        isDump = !isDump
      }
    )
    canvas.release()
    while (loop) {
      perform(ImageCaptureViewAction(roborazziOptions.let {
        it.copy(
          captureType = if (isDump) {
            RoborazziOptions.CaptureType.Dump(
              depthSlideSize = 300
            )
          } else {
            it.captureType
          }
        )
      }
      ) { newCanvas ->
        nativeGraphicsEmulator.onFrame(
          NativeGraphicsEmulatorFrame.UiState(
            canvas = newCanvas,
            qualifier = RuntimeEnvironment.getQualifiers(),
            devices = RobolectricDeviceQualifiers.allDevices().map { it.name },
          )
        )
        newCanvas.release()
      })
      if (isDump) {
        uiController.loopMainThreadForAtLeast(1000)
        Thread.sleep(1000)
      } else {
        uiController.loopMainThreadForAtLeast(1000 / 6)
        Thread.sleep(1000 / 6)
      }
    }
  })
}

private fun ViewInteraction.onViewWithInteraction(onView: (View) -> Unit) {
  Handler(Looper.getMainLooper()).post {
    check { view, noViewFoundException ->
      onView(view)
    }
  }
}

private fun getActivity(view: View): Activity {
  fun Context.getActivity(): Activity? {
    println(this)
    if (this is Activity) return this
    if (this is ContextWrapper) return baseContext.getActivity()
    return null
  }

  val activity =
    if (view.context::class.java.name == "com.android.internal.policy.DecorContext") {
      (view as ViewGroup).getChildAt(0).context.getActivity()!!
    } else {
      view.context.getActivity()!!
    }
  return activity
}

private fun getUiController(): UiController {
  val declaredMethod = GraphHolder::class.java.getDeclaredMethod("baseLayer")
  declaredMethod.isAccessible = true
  val uiController = (declaredMethod.invoke(null) as BaseLayerComponent).uiController()
  return uiController
}
