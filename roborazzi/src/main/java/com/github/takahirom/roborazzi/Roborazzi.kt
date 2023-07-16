package com.github.takahirom.roborazzi

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoActivityResumedException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.platform.app.InstrumentationRegistry
import com.dropbox.differ.ImageComparator
import io.github.takahirom.roborazzi.CompareReportCaptureResult
import java.io.File
import java.util.Locale
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.core.IsEqual


const val DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH = "build/outputs/roborazzi"
var ROBORAZZI_DEBUG = false

fun roborazziEnabled(): Boolean {
  val isEnabled = roborazziRecordingEnabled() ||
    roborazziCompareEnabled() ||
    roborazziVerifyEnabled()
  debugLog {
    "roborazziEnabled: $isEnabled \n" +
      "roborazziRecordingEnabled(): ${roborazziRecordingEnabled()}\n" +
      "roborazziCompareEnabled(): ${roborazziCompareEnabled()}\n" +
      "roborazziVerifyEnabled(): ${roborazziVerifyEnabled()}\n" +
      "roborazziDefaultResizeScale(): ${roborazziDefaultResizeScale()}\n" +
      "roborazziDefaultNamingStrategy(): ${roborazziDefaultNamingStrategy()}\n" +
      "RoborazziContext: ${provideRoborazziContext()}\n"
  }
  return isEnabled
}

fun roborazziCompareEnabled(): Boolean {
  return System.getProperty("roborazzi.test.compare") == "true"
}

fun roborazziVerifyEnabled(): Boolean {
  return System.getProperty("roborazzi.test.verify") == "true"
}

fun roborazziRecordingEnabled(): Boolean {
  return System.getProperty("roborazzi.test.record") == "true"
}

fun roborazziDefaultResizeScale(): Double {
  return checkNotNull(System.getProperty("roborazzi.record.resizeScale", "1.0")).toDouble()
}

fun roborazziDefaultNamingStrategy(): DefaultFileNameGenerator.DefaultNamingStrategy {
  return DefaultFileNameGenerator.DefaultNamingStrategy
    .fromOptionName(
      optionName = checkNotNull(
        System.getProperty(
          "roborazzi.record.namingStrategy",
          DefaultFileNameGenerator.DefaultNamingStrategy.TestPackageAndClassAndMethod.optionName
        )
      )
    )
}

fun ViewInteraction.captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath("png"),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziEnabled()) return
  captureRoboImage(
    file = File(filePath),
    roborazziOptions = roborazziOptions
  )
}

fun ViewInteraction.captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziEnabled()) return
  perform(ImageCaptureViewAction(roborazziOptions) { canvas ->
    processOutputImageAndReport(
      canvas = canvas,
      goaldenFile = file,
      roborazziOptions = roborazziOptions
    )
    canvas.release()
  })
}

fun View.captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath("png"),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziEnabled()) return
  captureRoboImage(
    file = File(filePath),
    roborazziOptions = roborazziOptions
  )
}

fun View.captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziEnabled()) return

  val targetView = this@captureRoboImage
  if (targetView.isAttachedToWindow) {
    onView(IsEqual(targetView)).captureRoboImage(
      file = file,
      roborazziOptions = roborazziOptions
    )
  } else {
    fun Context.getActivity(): Activity? {
      if (this is Activity) return this
      if (this is ContextWrapper) return baseContext.getActivity()
      return null
    }

    val activity = requireNotNull(targetView.context.getActivity()) { "View should have Activity" }
    val targetParent = targetView.parent as? ViewGroup
    val layoutParams = targetView.layoutParams
    targetParent?.removeView(targetView)

    val viewGroup = activity.window.decorView
      .findViewById(android.R.id.content) as ViewGroup
    viewGroup.addView(
      targetView, ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
    )
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    // If we use Espresso.onView(), the image will have window background color.
    targetView.drawToBitmap().captureRoboImage(file, roborazziOptions)
    viewGroup.removeView(targetView)

    targetParent?.addView(targetView, layoutParams)
  }
}

fun Bitmap.captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath("png"),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziEnabled()) return
  captureRoboImage(
    file = File(filePath),
    roborazziOptions = roborazziOptions
  )
}

fun Bitmap.captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziEnabled()) return
  val image = this
  val canvas = RoboCanvas(
    width = image.width,
    height = image.height,
    filled = true,
    bufferedImageType = roborazziOptions.recordOptions.pixelBitConfig.toBufferedImageType()
  ).apply {
    drawImage(Rect(0, 0, image.width, image.height), image)
  }
  processOutputImageAndReport(
    canvas = canvas,
    goaldenFile = file,
    roborazziOptions = roborazziOptions
  )
  canvas.release()
}

fun ViewInteraction.captureRoboGif(
  filePath: String = DefaultFileNameGenerator.generateFilePath("gif"),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  block: () -> Unit
) {
  // currently, gif compare is not supported
  if (!roborazziRecordingEnabled()) return
  captureRoboGif(File(filePath), roborazziOptions, block)
}

fun ViewInteraction.captureRoboGif(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
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
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  block: () -> Unit
) {
  if (!roborazziEnabled()) return
  captureRoboLastImage(File(filePath), roborazziOptions, block)
}

fun ViewInteraction.captureRoboLastImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
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
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
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
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziEnabled()) return
  captureRoboImage(File(filePath), roborazziOptions)
}

fun SemanticsNodeInteraction.captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziEnabled()) return
  capture(
    rootComponent = RoboComponent.Compose(
      node = this.fetchSemanticsNode("fail to captureRoboImage"),
      roborazziOptions = roborazziOptions
    ),
    roborazziOptions = roborazziOptions,
  ) { canvas ->
    processOutputImageAndReport(
      canvas = canvas,
      goaldenFile = file,
      roborazziOptions = roborazziOptions
    )
    canvas.release()
  }
}

fun SemanticsNodeInteraction.captureRoboGif(
  composeRule: AndroidComposeTestRule<*, *>,
  filePath: String = DefaultFileNameGenerator.generateFilePath("gif"),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
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
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
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

@InternalRoborazziApi
fun ViewInteraction.captureAndroidView(
  roborazziOptions: RoborazziOptions,
  block: () -> Unit
): CaptureResult {
  var removeListener = {}

  val canvases = mutableListOf<RoboCanvas>()

  val handler = Handler(Looper.getMainLooper())
  val listener = ViewTreeObserver.OnGlobalLayoutListener {
    handler.postAtFrontOfQueue {
      this@captureAndroidView.perform(
        ImageCaptureViewAction(roborazziOptions) { canvas ->
          canvases.addIfChanged(canvas, roborazziOptions)
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
      handler.postAtFrontOfQueue {
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
        canvases.addIfChanged(canvas, roborazziOptions)
      }
    )
    perform(viewTreeListenerAction)
  } catch (e: NoActivityResumedException) {
    // It seems there is no resumed activity, so wait
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
        onIdle()
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

private fun MutableList<RoboCanvas>.addIfChanged(
  next: RoboCanvas,
  roborazziOptions: RoborazziOptions
) {
  val prev = this.lastOrNull() ?: run {
    this.add(next)
    return
  }
  val differ: ImageComparator.ComparisonResult =
    prev.differ(next, 1.0)
  if (!roborazziOptions.compareOptions.resultValidator(differ)) {
    this.add(next)
  } else {
    // If the image is not changed, we should release the image.
    next.release()
  }
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
  processOutputImageAndReport(
    canvas = roboCanvas,
    goaldenFile = file,
    roborazziOptions = roborazziOptions
  )
}

// Only for library, please don't use this directly
fun SemanticsNodeInteraction.captureComposeNode(
  composeRule: AndroidComposeTestRule<*, *>,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  block: () -> Unit
): CaptureResult {
  val canvases = mutableListOf<RoboCanvas>()

  val capture = {
    capture(
      rootComponent = RoboComponent.Compose(
        this@captureComposeNode.fetchSemanticsNode("roborazzi can't find component"),
        roborazziOptions
      ),
      roborazziOptions = roborazziOptions
    ) {
      canvases.addIfChanged(it, roborazziOptions)
    }
  }
  val handler = Handler(Looper.getMainLooper())
  val composeApplyObserver = Snapshot.registerApplyObserver { _, _ ->
    handler.postAtFrontOfQueue {
      try {
        capture()
      } catch (e: IllegalStateException) {
        // No compose hierarchies found in the app, so wait
        e.printStackTrace()
      }

    }
  }
  try {
    // If there is already a screen, we should take the screenshot first not to miss the frame.
    capture()
  } catch (e: IllegalStateException) {
    // No compose hierarchies found in the app, so wait
  }
  return CaptureResult(
    result = runCatching {
      try {
        block()
      } catch (e: Exception) {
        throw e
      } finally {
        composeRule.waitForIdle()
        composeApplyObserver.dispose()
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
    processOutputImageAndReport(
      canvas = canvas,
      goaldenFile = fileCreator(index.toString()),
      roborazziOptions = roborazziOptions
    )
  }
}

private fun processOutputImageAndReport(
  canvas: RoboCanvas,
  goaldenFile: File,
  roborazziOptions: RoborazziOptions
) {
  val forbiddenFileSuffixes = listOf("_compare", "_actual")
  forbiddenFileSuffixes.forEach {
    if (goaldenFile.nameWithoutExtension.endsWith(it)) {
      throw IllegalArgumentException("The file name should not end with $it because it is reserved for Roborazzi")
    }
  }
  val recordOptions = roborazziOptions.recordOptions
  val resizeScale = recordOptions.resizeScale
  if (roborazziCompareEnabled() || roborazziVerifyEnabled()) {
    val width = (canvas.croppedWidth * resizeScale).toInt()
    val height = (canvas.croppedHeight * resizeScale).toInt()
    val goldenRoboCanvas = if (goaldenFile.exists()) {
      RoboCanvas.load(goaldenFile, recordOptions.pixelBitConfig.toBufferedImageType())
    } else {
      RoboCanvas(
        width = width,
        height = height,
        filled = true,
        bufferedImageType = recordOptions.pixelBitConfig.toBufferedImageType()
      )
    }
    val changed = if (height == goldenRoboCanvas.height && width == goldenRoboCanvas.width) {
      val comparisonResult: ImageComparator.ComparisonResult =
        canvas.differ(goldenRoboCanvas, resizeScale)
      val changed = !roborazziOptions.compareOptions.resultValidator(comparisonResult)
      log("${goaldenFile.name} The differ result :$comparisonResult changed:$changed")
      changed
    } else {
      log("${goaldenFile.name} The image size is changed. actual = (${goldenRoboCanvas.width}, ${goldenRoboCanvas.height}), golden = (${canvas.croppedWidth}, ${canvas.croppedHeight})")
      true
    }

    val result: CompareReportCaptureResult = if (changed) {
      val compareFile = File(
        goaldenFile.parent,
        goaldenFile.nameWithoutExtension + "_compare." + goaldenFile.extension
      )
      val compareCanvas = RoboCanvas.generateCompareCanvas(
        goldenCanvas = goldenRoboCanvas,
        newCanvas = canvas,
        newCanvasResize = resizeScale,
        bufferedImageType = recordOptions.pixelBitConfig.toBufferedImageType()
      )
      compareCanvas
        .save(
          file = compareFile,
          resizeScale = resizeScale
        )
      compareCanvas.release()

      val actualFile = if (roborazziRecordingEnabled()) {
        // If record option is enabled, we should save the actual file as the golden file.
        goaldenFile
      } else {
        File(
          goaldenFile.parent,
          goaldenFile.nameWithoutExtension + "_actual." + goaldenFile.extension
        )
      }
      canvas
        .save(
          file = actualFile,
          resizeScale = resizeScale
        )
      if (goaldenFile.exists()) {
        CompareReportCaptureResult.Changed(
          compareFile = compareFile,
          actualFile = actualFile,
          goldenFile = goaldenFile,
          timestampNs = System.nanoTime(),
        )
      } else {
        CompareReportCaptureResult.Added(
          compareFile = compareFile,
          actualFile = actualFile,
          timestampNs = System.nanoTime(),
        )
      }
    } else {
      CompareReportCaptureResult.Unchanged(
        goldenFile = goaldenFile,
        timestampNs = System.nanoTime(),
      )
    }
    roborazziOptions.compareOptions.roborazziCompareReporter.report(result)
  } else {
    // roborazzi.record is checked before
    canvas.save(goaldenFile, resizeScale)
  }
}

private fun log(message: String) {
  println("Roborazzi: $message")
}

private inline fun debugLog(crossinline message: () -> String) {
  if (ROBORAZZI_DEBUG) {
    println("Roborazzi Debug: ${message()}")
  }
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
      dumpOptions = roborazziOptions.captureType,
      recordOptions = roborazziOptions.recordOptions,
      onCanvas = onCanvas
    )

    is RoborazziOptions.CaptureType.Screenshot -> {
      val image = rootComponent.image!!
      onCanvas(
        RoboCanvas(
          width = image.width,
          height = image.height,
          filled = true,
          bufferedImageType = roborazziOptions.recordOptions.pixelBitConfig.toBufferedImageType()
        ).apply {
          drawImage(Rect(0, 0, image.width, image.height), image)
        }
      )
    }
  }
}
