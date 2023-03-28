package com.github.takahirom.roborazzi

import android.app.Activity
import android.app.Application
import android.graphics.Rect
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
import com.dropbox.differ.ImageComparator
import java.io.File
import java.util.Locale
import org.hamcrest.Matcher
import org.hamcrest.Matchers


const val DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH = "build/outputs/roborazzi"
fun roborazziEnabled(): Boolean {
  return System.getProperty("roborazzi.test.record") == "true" || System.getProperty("roborazzi.test.verify") == "true"
}

fun roborazziVerifyEnabled(): Boolean {
  return System.getProperty("roborazzi.test.verify") == "true"
}

fun roborazziRecordingEnabled(): Boolean {
  return System.getProperty("roborazzi.test.record") == "true"
}

fun ViewInteraction.captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath("png"),
  roborazziOptions: RoborazziOptions = RoborazziOptions(),
) {
  if (!roborazziEnabled()) return
  captureRoboImage(
    file = File(filePath),
    roborazziOptions = roborazziOptions
  )
}

fun ViewInteraction.captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = RoborazziOptions(),
) {
  if (!roborazziEnabled()) return
  perform(ImageCaptureViewAction(roborazziOptions) { canvas ->
    saveOrVerify(canvas, file, roborazziOptions)
    canvas.release()
  })
}

fun ViewInteraction.captureRoboGif(
  filePath: String = DefaultFileNameGenerator.generateFilePath("gif"),
  roborazziOptions: RoborazziOptions = RoborazziOptions(),
  block: () -> Unit
) {
  // currently, gif compare is not supported
  if (!roborazziRecordingEnabled()) return
  captureRoboGif(File(filePath), roborazziOptions, block)
}

fun ViewInteraction.captureRoboGif(
  file: File,
  roborazziOptions: RoborazziOptions = RoborazziOptions(),
  block: () -> Unit
) {
  // currently, gif compare is not supported
  if (!roborazziRecordingEnabled()) return
  captureAndroidView(roborazziOptions, block).apply {
    saveGif(file)
    clear()
    result.getOrThrow()
  }
}

fun ViewInteraction.captureRoboLastImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath("png"),
  roborazziOptions: RoborazziOptions = RoborazziOptions(),
  block: () -> Unit
) {
  // currently, gif compare is not supported
  if (!roborazziRecordingEnabled()) return
  captureRoboLastImage(File(filePath), roborazziOptions, block)
}

fun ViewInteraction.captureRoboLastImage(
  file: File,
  roborazziOptions: RoborazziOptions = RoborazziOptions(),
  block: () -> Unit
) {
  if (!roborazziEnabled()) return
  captureAndroidView(roborazziOptions, block).apply {
    saveLastImage(file)
    clear()
    result.getOrThrow()
  }
}

fun ViewInteraction.captureRoboAllImage(
  fileNameCreator: (prefix: String) -> File,
  roborazziOptions: RoborazziOptions = RoborazziOptions(),
  block: () -> Unit
) {
  if (!roborazziEnabled()) return
  captureAndroidView(roborazziOptions, block).apply {
    saveAllImage(fileNameCreator)
    clear()
    result.getOrThrow()
  }

}

fun SemanticsNodeInteraction.captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath("png"),
  roborazziOptions: RoborazziOptions = RoborazziOptions(),
) {
  if (!roborazziEnabled()) return
  captureRoboImage(File(filePath), roborazziOptions)
}

fun SemanticsNodeInteraction.captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = RoborazziOptions(),
) {
  if (!roborazziEnabled()) return
  capture(
    rootComponent = RoboComponent.Compose(
      node = this.fetchSemanticsNode("fail to captureRoboImage"),
      roborazziOptions = roborazziOptions
    ),
    roborazziOptions = roborazziOptions,
  ) { canvas ->
    saveOrVerify(canvas, file, roborazziOptions)
    canvas.release()
  }
}

fun SemanticsNodeInteraction.captureRoboGif(
  composeRule: AndroidComposeTestRule<*, *>,
  filePath: String = DefaultFileNameGenerator.generateFilePath("gif"),
  roborazziOptions: RoborazziOptions = RoborazziOptions(),
  block: () -> Unit
) {
  // currently, gif compare is not supported
  if (!roborazziRecordingEnabled()) return
  captureComposeNode(
    composeRule = composeRule,
    roborazziOptions = roborazziOptions,
    block = block
  ).apply {
    saveGif(File(filePath))
    clear()
  }
}

fun SemanticsNodeInteraction.captureRoboGif(
  composeRule: AndroidComposeTestRule<*, *>,
  file: File,
  roborazziOptions: RoborazziOptions = RoborazziOptions(),
  block: () -> Unit
) {
  // currently, gif compare is not supported
  if (!roborazziRecordingEnabled()) return
  captureComposeNode(composeRule, roborazziOptions, block).apply {
    saveGif(file)
    clear()
  }
}

class CaptureResult(
  val result: Result<Unit>,
  val saveLastImage: (file: File) -> Unit,
  val saveAllImage: (file: (String) -> File) -> Unit,
  val saveGif: (file: File) -> Unit,
  val clear: () -> Unit
)

// Only for library, please don't use this directly
fun ViewInteraction.captureAndroidView(
  roborazziOptions: RoborazziOptions,
  block: () -> Unit
): CaptureResult {
  var removeListener = {}

  val canvases = mutableListOf<RoboCanvas>()

  val listener = ViewTreeObserver.OnGlobalLayoutListener {
    Handler(Looper.getMainLooper()).post {
      this@captureAndroidView.perform(
        ImageCaptureViewAction(roborazziOptions) { canvas ->
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
      ImageCaptureViewAction(roborazziOptions) { canvas ->
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
      saveGif(file, canvases, roborazziOptions)
    },
    saveLastImage = { file ->
      saveLastImage(canvases, file, roborazziOptions)
    },
    saveAllImage = { fileCreator ->
      saveAllImage(fileCreator, canvases, roborazziOptions)
    },
    clear = {
      canvases.forEach { it.release() }
      canvases.clear()
    }
  )
}

private fun saveLastImage(
  canvases: MutableList<RoboCanvas>,
  file: File,
  roborazziOptions: RoborazziOptions,
) {
  val roboCanvas = canvases.lastOrNull()
  if (roboCanvas == null) {
    println("Roborazzi could not capture for this test")
    return
  }
  saveOrVerify(roboCanvas, file, roborazziOptions)
}

// Only for library, please don't use this directly
fun SemanticsNodeInteraction.captureComposeNode(
  composeRule: AndroidComposeTestRule<*, *>,
  roborazziOptions: RoborazziOptions = RoborazziOptions(),
  block: () -> Unit
): CaptureResult {
  var removeListener = {}

  val canvases = mutableListOf<RoboCanvas>()

  val capture = {
    capture(
      rootComponent = RoboComponent.Compose(
        this@captureComposeNode.fetchSemanticsNode("roborazzi can't find component"),
        roborazziOptions
      ),
      roborazziOptions = roborazziOptions
    ) {
      canvases.add(it)
    }
  }
  val listener = ViewTreeObserver.OnGlobalLayoutListener(capture)
  try {
    // If there is already a screen, we should take the screenshot first not to miss the frame.
    capture()
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
        capture()
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
      saveGif(file, canvases, roborazziOptions)
    },
    saveLastImage = { file ->
      saveLastImage(canvases, file, roborazziOptions)
    },
    saveAllImage = { fileCreator ->
      saveAllImage(fileCreator, canvases, roborazziOptions)
    },
    clear = {
      canvases.forEach { it.release() }
      canvases.clear()
    })
}

private fun saveGif(
  file: File,
  canvases: MutableList<RoboCanvas>,
  roborazziOptions: RoborazziOptions,
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
      e.addFrame(canvas, roborazziOptions.recordOptions.resizeScale)
    }
  }
  e.finish()
}

private fun saveAllImage(
  fileCreator: (String) -> File,
  canvases: MutableList<RoboCanvas>,
  roborazziOptions: RoborazziOptions,
) {
  canvases.forEachIndexed { index, canvas ->
    saveOrVerify(canvas, fileCreator(index.toString()), roborazziOptions)
  }
}

private fun saveOrVerify(canvas: RoboCanvas, file: File, roborazziOptions: RoborazziOptions) {
  val resizeScale = roborazziOptions.recordOptions.resizeScale
  if (roborazziVerifyEnabled()) {
    val width = (canvas.croppedWidth * resizeScale).toInt()
    val height = (canvas.croppedHeight * resizeScale).toInt()
    val goldenRoboCanvas = if (file.exists()) {
      RoboCanvas.load(file)
    } else {
      RoboCanvas(width, height, true)
    }
    val changed = if (height == goldenRoboCanvas.height && width == goldenRoboCanvas.width) {
      val comparisonResult: ImageComparator.ComparisonResult =
        canvas.differ(goldenRoboCanvas, resizeScale)
      val changed = !roborazziOptions.verifyOptions.resultValidator(comparisonResult)
      log("${file.name} The differ result :$comparisonResult changed:$changed")
      changed
    } else {
      log("${file.name}  The image size is changed. actual = (${goldenRoboCanvas.width}, ${goldenRoboCanvas.height}), golden = (${canvas.croppedWidth}, ${canvas.croppedHeight})")
      true
    }

    if (changed) {
      RoboCanvas.generateCompareCanvas(
        goldenCanvas = goldenRoboCanvas,
        newCanvas = canvas,
        newCanvasResize = resizeScale
      )
        .save(
          file = File(file.parent, file.nameWithoutExtension + "_compare." + file.extension),
          resizeScale = resizeScale
        )
    }
  } else {
    // roborazzi.record is checked before
    canvas.save(file, resizeScale)
  }
}

private fun log(message: String) {
  println("Roborazzi: $message")
}

private class ImageCaptureViewAction(
  val roborazziOptions: RoborazziOptions,
  val saveAction: (RoboCanvas) -> Unit
) :
  ViewAction {
  override fun getConstraints(): Matcher<View> {
    return Matchers.any(View::class.java)
  }

  override fun getDescription(): String {
    return String.format(Locale.ROOT, "capture view to image")
  }

  override fun perform(uiController: UiController, view: View) {
    capture(
      rootComponent = RoboComponent.View(
        view = view,
        roborazziOptions,
      ),
      roborazziOptions = roborazziOptions,
      onCanvas = saveAction
    )
  }
}

internal fun capture(
  rootComponent: RoboComponent,
  roborazziOptions: RoborazziOptions,
  onCanvas: (RoboCanvas) -> Unit
) {
  when (roborazziOptions.captureType) {
    is RoborazziOptions.CaptureType.Dump -> captureDump(
      rootComponent = rootComponent,
      roborazziOptions = roborazziOptions.captureType,
      onCanvas = onCanvas
    )

    is RoborazziOptions.CaptureType.Screenshot -> {
      val image = rootComponent.image!!
      onCanvas(
        RoboCanvas(width = image.width, height = image.height, true).apply {
          drawImage(Rect(0, 0, image.width, image.height), image)
        }
      )
    }
  }
}
