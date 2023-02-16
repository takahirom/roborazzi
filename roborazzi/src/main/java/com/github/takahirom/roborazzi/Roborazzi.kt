package com.github.takahirom.roborazzi

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.core.view.doOnNextLayout
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import java.io.File
import java.util.Locale

const val DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH = "build/outputs/roborazzi"
fun ViewInteraction.captureRoboImage(filePath: String) {
  captureRoboImage(File(filePath))
}

fun ViewInteraction.captureRoboImage(file: File) {
  perform(ImageCaptureViewAction { canvas ->
    canvas.save(file)
    canvas.release()
  })
}

fun ViewInteraction.captureRoboGif(filePath: String, block: () -> Unit) {
  captureRoboGif(File(filePath), block)
}

fun ViewInteraction.captureRoboGif(file: File, block: () -> Unit) {
  captureAndroidView(block).apply {
    saveGif(file)
    clear()
    result.getOrThrow()
  }
}

fun ViewInteraction.captureRoboLastImage(filePath: String, block: () -> Unit) {
  captureRoboGif(File(filePath), block)
}

fun ViewInteraction.captureRoboLastImage(file: File, block: () -> Unit) {
  captureAndroidView(block).apply {
    saveLastImage(file)
    clear()
    result.getOrThrow()
  }
}

fun ViewInteraction.captureRoboAllImage(
  fileNameCreator: (prefix: String) -> File,
  block: () -> Unit
) {
  captureAndroidView(block).apply {
    saveAllImage(fileNameCreator)
    clear()
    result.getOrThrow()
  }

}

fun SemanticsNodeInteraction.captureRoboImage(filePath: String) {
  captureRoboImage(File(filePath))
}

fun SemanticsNodeInteraction.captureRoboImage(file: File) {
  capture(RoboComponent.Compose(this.fetchSemanticsNode("fail to captureRoboImage"))) { canvas ->
    canvas.save(file)
    canvas.release()
  }
}

fun SemanticsNodeInteraction.captureRoboGif(
  composeRule: AndroidComposeTestRule<*, *>,
  filePath: String,
  block: () -> Unit
) {
  captureComposeNode(composeRule, block).saveGif(File(filePath))
}

fun SemanticsNodeInteraction.captureRoboGif(
  composeRule: AndroidComposeTestRule<*, *>,
  file: File,
  block: () -> Unit
) {
  captureComposeNode(composeRule, block).saveGif(file)
}

enum class CaptureType {
  /**
   * Generate test last image
   */
  LastImage,

  /**
   * Generate Each layout change images like TestClass_method_0.png
   */
  AllImage,

  /**
   * Generate gif image
   */
  Gif
}

data class CaptureMode(
  val captureType: CaptureType = CaptureType.Gif,
  val onlyFail: Boolean = false
)

class CaptureResult(
  val result: Result<Unit>,
  val saveLastImage: (file: File) -> Unit,
  val saveAllImage: (file: (String) -> File) -> Unit,
  val saveGif: (file: File) -> Unit,
  val clear: () -> Unit
)

fun ViewInteraction.captureAndroidView(block: () -> Unit): CaptureResult {
  var removeListener = {}

  val canvases = mutableListOf<RoboCanvas>()

  val listener = ViewTreeObserver.OnGlobalLayoutListener {
    Handler(Looper.getMainLooper()).post {
      this@captureAndroidView.perform(
        ImageCaptureViewAction { canvas ->
          canvases.add(canvas)
        }
      )
    }
  }
  val viewTreeListenerAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> {
      return Matchers.any(View::class.java)
    }

    override fun getDescription(): String {
      return String.format(Locale.ROOT, "capture view to image")
    }

    override fun perform(uiController: UiController, view: View) {
      val viewTreeObserver = view.viewTreeObserver
      viewTreeObserver.addOnGlobalLayoutListener(listener)
      removeListener = {
        viewTreeObserver.removeOnGlobalLayoutListener(listener)
      }
    }
  }

  val instrumentation = InstrumentationRegistry.getInstrumentation()
  val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
      Handler(Looper.getMainLooper()).post {
        perform(viewTreeListenerAction)
      }
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }
  }
  try {
    // If there is already a screen, we should take the screenshot first not to miss the frame.
    perform(
      ImageCaptureViewAction { canvas ->
        canvases.add(canvas)
      }
    )
    perform(viewTreeListenerAction)
  } catch (e: Exception) {
    // It seems there is no screen, so wait
    val application = instrumentation.targetContext.applicationContext as Application
    application.registerActivityLifecycleCallbacks(activityCallbacks)
  }
  return CaptureResult(
    result = runCatching {
      try {
        block()
      } catch (e: Exception) {
        throw e
      } finally {
        removeListener()
        val application = instrumentation.targetContext.applicationContext as Application
        application.unregisterActivityLifecycleCallbacks(activityCallbacks)
      }
    },
    saveGif = { file ->
      saveGif(file, canvases)
    },
    saveLastImage = { file ->
      saveLastImage(canvases, file)
    },
    saveAllImage = { fileCreator ->
      saveAllImage(fileCreator, canvases)
    },
    clear = {
      canvases.forEach { it.release() }
      canvases.clear()
    }
  )
}

private fun saveLastImage(
  canvases: MutableList<RoboCanvas>,
  file: File
) {
  val roboCanvas = canvases.lastOrNull()
  if (roboCanvas == null) {
    println("Roborazzi could not capture for this test")
  }
  roboCanvas?.save(file)
}

fun SemanticsNodeInteraction.captureComposeNode(
  composeRule: AndroidComposeTestRule<*, *>,
  block: () -> Unit
): CaptureResult {
  var removeListener = {}

  val canvases = mutableListOf<RoboCanvas>()

  val listener = ViewTreeObserver.OnGlobalLayoutListener {
    capture(RoboComponent.Compose(this@captureComposeNode.fetchSemanticsNode(""))) {
      canvases.add(it)
    }
  }
  try {
    // If there is already a screen, we should take the screenshot first not to miss the frame.
    capture(RoboComponent.Compose(this@captureComposeNode.fetchSemanticsNode(""))) {
      canvases.add(it)
    }
    val viewTreeObserver = composeRule.activity.window.decorView.viewTreeObserver
    viewTreeObserver.addOnGlobalLayoutListener(listener)
    removeListener = {
      viewTreeObserver.removeOnGlobalLayoutListener(listener)
    }
  } catch (e: Exception) {
    // It seems there is no screen, so wait
    composeRule.runOnIdle {
      val viewTreeObserver = composeRule.activity.window.decorView.viewTreeObserver
      composeRule.activity.window.decorView.doOnNextLayout {
        capture(RoboComponent.Compose(this@captureComposeNode.fetchSemanticsNode(""))) {
          canvases.add(it)
        }
      }
      viewTreeObserver.addOnGlobalLayoutListener(listener)
      removeListener = {
        viewTreeObserver.removeOnGlobalLayoutListener(listener)
      }
    }
  }
  return CaptureResult(
    result = runCatching {
      try {
        block()
      } catch (e: Exception) {
        throw e
      } finally {
        composeRule.waitForIdle()
        removeListener()
      }
    },
    saveGif = { file ->
      saveGif(file, canvases)
    },
    saveLastImage = { file ->
      saveLastImage(canvases, file)
    },
    saveAllImage = { fileCreator ->
      saveAllImage(fileCreator, canvases)
    },
    clear = {
      canvases.forEach { it.release() }
      canvases.clear()
    })
}

private fun saveGif(
  file: File,
  canvases: MutableList<RoboCanvas>
) {
  val e = AnimatedGifEncoder()
  e.setRepeat(0)
  e.start(file.outputStream())
  e.setDelay(1000)   // 1 frame per sec
  if (canvases.isNotEmpty()) {
    e.setSize(
      canvases.maxOf { it.croppedWidth },
      canvases.maxOf { it.croppedHeight }
    )
    canvases.forEach { canvas ->
      e.addFrame(canvas)
    }
  }
  e.finish()
}

private fun saveAllImage(
  fileCreator: (String) -> File,
  canvases: MutableList<RoboCanvas>
) {
  canvases.forEachIndexed { index, canvas ->
    canvas.save(fileCreator(index.toString()))
  }
}

private class ImageCaptureViewAction(val saveAction: (RoboCanvas) -> Unit) : ViewAction {
  override fun getConstraints(): Matcher<View> {
    return Matchers.any(View::class.java)
  }

  override fun getDescription(): String {
    return String.format(Locale.ROOT, "capture view to image")
  }

  override fun perform(uiController: UiController, view: View) {
    capture(RoboComponent.View(view), saveAction)
  }
}
