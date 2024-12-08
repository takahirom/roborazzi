package com.github.takahirom.roborazzi

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.core.view.drawToBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoActivityResumedException
import androidx.test.espresso.Root
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.base.RootsOracle_Factory
import androidx.test.platform.app.InstrumentationRegistry
import com.dropbox.differ.ImageComparator
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.core.IsEqual
import java.io.File
import java.util.Locale


fun ViewInteraction.captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  captureRoboImage(
    file = fileWithRecordFilePathStrategy(filePath),
    roborazziOptions = roborazziOptions
  )
}

fun ViewInteraction.captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  perform(ImageCaptureViewAction(roborazziOptions) { _, canvas ->
    processOutputImageAndReportWithDefaults(
      canvas = canvas,
      goldenFile = file,
      roborazziOptions = roborazziOptions
    )
    canvas.release()
  })
}

fun View.captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  captureRoboImage(
    file = fileWithRecordFilePathStrategy(filePath),
    roborazziOptions = roborazziOptions
  )
}

fun View.captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziOptions.taskType.isEnabled()) return

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

/**
 * Capture the screen image including dialogs.
 */
@ExperimentalRoborazziApi
fun captureScreenRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  captureScreenRoboImage(
    file = fileWithRecordFilePathStrategy(filePath),
    roborazziOptions = roborazziOptions,
  )
}

/**
 * Capture the screen image including dialogs.
 */
@Suppress("INACCESSIBLE_TYPE")
@ExperimentalRoborazziApi
fun captureScreenRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  // Views needs to be laid out before we can capture them
  Espresso.onIdle()

  val rootsOracle = RootsOracle_Factory({ Looper.getMainLooper() })
    .get()
  // Invoke rootOracle.listActiveRoots() via reflection
  val listActiveRoots = rootsOracle.javaClass.getMethod("listActiveRoots")
  listActiveRoots.isAccessible = true
  @Suppress("UNCHECKED_CAST") val roots: List<Root> =
    listActiveRoots.invoke(rootsOracle) as List<Root>
  debugLog {
    "captureScreenRoboImage roots: ${roots.joinToString("\n") { it.toString() }}"
  }
  capture(
    rootComponent = RoboComponent.Screen(
      rootsOrderByDepth = roots.sortedBy { it.windowLayoutParams.get()?.type },
      roborazziOptions = roborazziOptions
    ),
    roborazziOptions = roborazziOptions,
  ) { _, canvas ->
    processOutputImageAndReportWithDefaults(
      canvas = canvas,
      goldenFile = file,
      roborazziOptions = roborazziOptions
    )
    canvas.release()
  }
}

fun Bitmap.captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  captureRoboImage(
    file = fileWithRecordFilePathStrategy(filePath),
    roborazziOptions = roborazziOptions
  )
}

fun Bitmap.captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  if (roborazziOptions.captureType is Dump) {
    throw IllegalStateException("Dump is not supported for Bitmap captureRoboImage()")
  }
  val image = this
  val canvas = AwtRoboCanvas(
    width = image.width,
    height = image.height,
    filled = true,
    bufferedImageType = roborazziOptions.recordOptions.pixelBitConfig.toBufferedImageType()
  ).apply {
    drawImage(Rect(0, 0, image.width, image.height), image)
  }
  processOutputImageAndReportWithDefaults(
    canvas = canvas,
    goldenFile = file,
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
  if (!roborazziOptions.taskType.isRecording()) return
  captureRoboGif(fileWithRecordFilePathStrategy(filePath), roborazziOptions, block)
}

fun ViewInteraction.captureRoboGif(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  block: () -> Unit
) {
  // currently, gif compare is not supported
  if (!roborazziOptions.taskType.isRecording()) return
  captureAndroidView(roborazziOptions = roborazziOptions, onEach = {}, block = block).apply {
    saveGif(file)
    clear()
    result.getOrThrow()
  }
}

fun ViewInteraction.captureRoboLastImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  block: () -> Unit
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  captureRoboLastImage(fileWithRecordFilePathStrategy(filePath), roborazziOptions, block)
}

fun ViewInteraction.captureRoboLastImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  block: () -> Unit
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  captureAndroidView(roborazziOptions = roborazziOptions, onEach = {}, block = block).apply {
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
  if (!roborazziOptions.taskType.isEnabled()) return
  captureAndroidView(roborazziOptions = roborazziOptions, onEach = {}, block = block).apply {
    saveAllImage(fileNameCreator)
    clear()
    result.getOrThrow()
  }

}

fun SemanticsNodeInteraction.captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  captureRoboImage(fileWithRecordFilePathStrategy(filePath), roborazziOptions)
}

fun SemanticsNodeInteraction.captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  capture(
    rootComponent = RoboComponent.Compose(
      node = this.fetchSemanticsNode("fail to captureRoboImage"),
      roborazziOptions = roborazziOptions
    ),
    roborazziOptions = roborazziOptions,
  ) { _, canvas ->
    processOutputImageAndReportWithDefaults(
      canvas = canvas,
      goldenFile = file,
      roborazziOptions = roborazziOptions
    )
    canvas.release()
  }
}

fun SemanticsNodeInteraction.captureRoboGif(
  composeRule: ComposeTestRule,
  filePath: String = DefaultFileNameGenerator.generateFilePath("gif"),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  block: () -> Unit
) {
  // currently, gif compare is not supported
  if (!roborazziOptions.taskType.isRecording()) return
  captureComposeNode(
    composeRule = composeRule,
    roborazziOptions = roborazziOptions,
    block = block
  ).apply {
    saveGif(fileWithRecordFilePathStrategy(filePath))
    clear()
    result.getOrThrow()
  }
}

fun SemanticsNodeInteraction.captureRoboGif(
  composeRule: ComposeTestRule,
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  block: () -> Unit
) {
  // currently, gif compare is not supported
  if (!roborazziOptions.taskType.isRecording()) return
  captureComposeNode(
    composeRule = composeRule,
    roborazziOptions = roborazziOptions,
    onEach = {},
    block = block
  ).apply {
    saveGif(file)
    clear()
    result.getOrThrow()
  }
}

@InternalRoborazziApi
class CaptureInternalResult(
  val result: Result<Unit>,
  val saveLastImage: (file: File) -> Unit,
  val saveAllImage: (file: (String) -> File) -> Unit,
  val saveGif: (file: File) -> Unit,
  val clear: () -> Unit
)

@InternalRoborazziApi
fun ViewInteraction.captureAndroidView(
  roborazziOptions: RoborazziOptions,
  onEach: () -> Unit = {},
  block: () -> Unit
): CaptureInternalResult {
  var removeListener = {}

  val canvases = mutableListOf<AwtRoboCanvas>()

  val handler = Handler(Looper.getMainLooper())
  val listener = ViewTreeObserver.OnGlobalLayoutListener {
    handler.postAtFrontOfQueue {
      this@captureAndroidView.perform(
        ImageCaptureViewAction(roborazziOptions) { _, canvas ->
          if (canvases.addIfChanged(canvas, roborazziOptions)) {
            onEach()
          }
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
      ImageCaptureViewAction(roborazziOptions) { _, canvas ->
        if (canvases.addIfChanged(canvas, roborazziOptions)) {
          onEach()
        }
      }
    )
    perform(viewTreeListenerAction)
  } catch (e: NoActivityResumedException) {
    // It seems there is no resumed activity, so wait
    val application = instrumentation.targetContext.applicationContext as Application
    application.registerActivityLifecycleCallbacks(activityCallbacks)
  }
  return CaptureInternalResult(
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

private fun MutableList<AwtRoboCanvas>.addIfChanged(
  next: AwtRoboCanvas,
  roborazziOptions: RoborazziOptions
): Boolean {
  val prev = this.lastOrNull() ?: run {
    this.add(next)
    return true
  }
  val differ: ImageComparator.ComparisonResult =
    prev.differ(next, 1.0, roborazziOptions.compareOptions.imageComparator)
  if (!roborazziOptions.compareOptions.resultValidator(differ)) {
    this.add(next)
    return true
  } else {
    // If the image is not changed, we should release the image.
    next.release()
    return false
  }
}

private fun saveLastImage(
  canvases: MutableList<AwtRoboCanvas>,
  file: File,
  roborazziOptions: RoborazziOptions,
) {
  val roboCanvas = canvases.lastOrNull()
  if (roboCanvas == null) {
    roborazziErrorLog("Roborazzi could not capture for this test")
    return
  }
  processOutputImageAndReportWithDefaults(
    canvas = roboCanvas,
    goldenFile = file,
    roborazziOptions = roborazziOptions
  )
}

// Only for library, please don't use this directly
fun SemanticsNodeInteraction.captureComposeNode(
  composeRule: ComposeTestRule,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  onEach: () -> Unit = {},
  block: () -> Unit
): CaptureInternalResult {
  val canvases = mutableListOf<AwtRoboCanvas>()

  val capture = {
    capture(
      rootComponent = RoboComponent.Compose(
        this@captureComposeNode.fetchSemanticsNode("roborazzi can't find component"),
        roborazziOptions
      ),
      roborazziOptions = roborazziOptions
    ) { _, canvas ->
      if (canvases.addIfChanged(canvas, roborazziOptions)) {
        onEach()
      }
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
  return CaptureInternalResult(
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
  canvases: MutableList<AwtRoboCanvas>,
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
  canvases: MutableList<AwtRoboCanvas>,
  roborazziOptions: RoborazziOptions,
) {
  canvases.forEachIndexed { index, canvas ->
    processOutputImageAndReportWithDefaults(
      canvas = canvas,
      goldenFile = fileCreator(index.toString()),
      roborazziOptions = roborazziOptions
    )
  }
}

private class ImageCaptureViewAction(
  val roborazziOptions: RoborazziOptions,
  val saveAction: (RoboComponent, AwtRoboCanvas) -> Unit
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
  onCanvas: (RoboComponent, AwtRoboCanvas) -> Unit
) {
  when (roborazziOptions.captureType) {
    is Dump -> captureDump(
      rootComponent = rootComponent,
      dumpOptions = roborazziOptions.captureType as Dump,
      recordOptions = roborazziOptions.recordOptions,
      onCanvas = onCanvas
    )

    is RoborazziOptions.CaptureType.Screenshot -> {
      val image = rootComponent.image
        ?: throw IllegalStateException("Unable to find the image of the target root component. Does the rendering element exist?")
      onCanvas(
        rootComponent,
        AwtRoboCanvas(
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


fun processOutputImageAndReportWithDefaults(
  canvas: RoboCanvas,
  goldenFile: File,
  roborazziOptions: RoborazziOptions,
) {
  processOutputImageAndReport(
    newRoboCanvas = canvas,
    goldenFile = goldenFile,
    roborazziOptions = roborazziOptions,
    emptyCanvasFactory = { width, height, filled, bufferedImageType ->
      AwtRoboCanvas(
        width = width,
        height = height,
        filled = filled,
        bufferedImageType = bufferedImageType
      )
    },
    canvasFactoryFromFile = { file, bufferedImageType ->
      AwtRoboCanvas.load(file, bufferedImageType, roborazziOptions.recordOptions.imageIoFormat)
    },
    comparisonCanvasFactory = { goldenCanvas, actualCanvas, resizeScale, bufferedImageType ->
      AwtRoboCanvas.generateCompareCanvas(
        AwtRoboCanvas.Companion.ComparisonCanvasParameters.create(
          goldenCanvas = goldenCanvas as AwtRoboCanvas,
          newCanvas = actualCanvas as AwtRoboCanvas,
          newCanvasResize = resizeScale,
          bufferedImageType = bufferedImageType,
          oneDpPx = run {
            val dip = 1f
            val r: Resources = ApplicationProvider.getApplicationContext<Context>().resources
            val px = TypedValue.applyDimension(
              TypedValue.COMPLEX_UNIT_DIP,
              dip,
              r.getDisplayMetrics()
            )
            (px * resizeScale).toFloat()
          },
          comparisonComparisonStyle = roborazziOptions.compareOptions.comparisonStyle
        )
      )
    }
  )
}