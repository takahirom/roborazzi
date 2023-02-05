package com.github.takahirom.roborazzi

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.core.view.doOnNextLayout
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.util.HumanReadables
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import java.io.File
import java.util.Locale
import kotlin.math.abs

fun ViewInteraction.captureRoboImage(filePath: String) {
  captureRoboImage(File(filePath))
}

fun ViewInteraction.captureRoboImage(file: File) {
  perform(ImageCaptureViewAction { canvas ->
    canvas.save(file)
  })
}

fun ViewInteraction.captureRoboGif(filePath: String, block: () -> Unit) {
  justCaptureRoboGif(File(filePath), block).save()
}

fun ViewInteraction.captureRoboGif(file: File, block: () -> Unit) {
  justCaptureRoboGif(file, block).save()
}

fun SemanticsNodeInteraction.captureRoboImage(filePath: String) {
  captureRoboImage(File(filePath))
}

fun SemanticsNodeInteraction.captureRoboImage(file: File) {
  capture(RoboComponent.Compose(this.fetchSemanticsNode("fail to captureRoboImage"))) { canvas ->
    canvas.save(file)
  }
}

fun SemanticsNodeInteraction.captureRoboGif(
  composeRule: AndroidComposeTestRule<*, *>,
  filePath: String,
  block: () -> Unit
) {
  justCaptureRoboGif(composeRule, File(filePath), block).save()
}

fun SemanticsNodeInteraction.captureRoboGif(
  composeRule: AndroidComposeTestRule<*, *>,
  file: File,
  block: () -> Unit
) {
  justCaptureRoboGif(composeRule, file, block).save()
}

class CaptureRoboGifResult(
  val result: Result<Unit>,
  val save: () -> Boolean
)

fun ViewInteraction.justCaptureRoboGif(file: File, block: () -> Unit): CaptureRoboGifResult {
  var removeListener = {}

  val canvases = mutableListOf<RoboCanvas>()

  val listener = ViewTreeObserver.OnGlobalLayoutListener {
    Handler(Looper.getMainLooper()).post {
      this@justCaptureRoboGif.perform(
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
  return CaptureRoboGifResult(
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
    }
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
}

fun SemanticsNodeInteraction.justCaptureRoboGif(
  composeRule: AndroidComposeTestRule<*, *>,
  file: File,
  block: () -> Unit
): CaptureRoboGifResult {
  var removeListener = {}

  val canvases = mutableListOf<RoboCanvas>()

  val listener = ViewTreeObserver.OnGlobalLayoutListener {
    capture(RoboComponent.Compose(this@justCaptureRoboGif.fetchSemanticsNode(""))) {
      canvases.add(it)
    }
  }
  try {
    // If there is already a screen, we should take the screenshot first not to miss the frame.
    capture(RoboComponent.Compose(this@justCaptureRoboGif.fetchSemanticsNode(""))) {
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
        capture(RoboComponent.Compose(this@justCaptureRoboGif.fetchSemanticsNode(""))) {
          canvases.add(it)
        }
      }
      viewTreeObserver.addOnGlobalLayoutListener(listener)
      removeListener = {
        viewTreeObserver.removeOnGlobalLayoutListener(listener)
      }
    }
  }
  return CaptureRoboGifResult(
    result = runCatching {
      try {
        block()
      } catch (e: Exception) {
        throw e
      } finally {
        composeRule.waitForIdle()
        removeListener()
      }
    }
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
}

internal val colors = listOf(
  0x3F9101,
  0x0E4A8E,
  0xBCBF01,
  0xBC0BA2,
  0x61AA0D,
  0x3D017A,
  0xD6A60A,
  0x7710A3,
  0xA502CE,
  0xeb5a00
)

internal enum class Visibility {
  Visible,
  Gone,
  Invisible;
}

internal sealed interface RoboComponent {

  class View(private val view: android.view.View) : RoboComponent {
    override val width: Int
      get() = view.width
    override val height: Int
      get() = view.height
    override val children: List<RoboComponent>
      get() = when (view) {
        is AbstractComposeView -> {
          (view.getChildAt(0) as? ViewRootForTest)
            ?.semanticsOwner
            ?.rootSemanticsNode
            ?.let {
              listOf(Compose(it))
            } ?: listOf()
        }
        is ViewGroup -> {
          (0 until view.childCount)
            .map { View(view.getChildAt(it)) }
        }
        else -> {
          listOf()
        }
      }

    private val id: String
      get() =
        if (0xFFFFFFFF.toInt() == view.id) {
          ""
        } else {
          try {
            view.resources.getResourceName(view.id)
          } catch (e: Exception) {
            ""
          }
        }
    override val text: String
      get() = HumanReadables.describe(view)
    override val visibility: Visibility
      get() = when (view.visibility) {
        android.view.View.VISIBLE -> Visibility.Visible
        android.view.View.GONE -> Visibility.Gone
        else -> Visibility.Invisible
      }

    override fun getGlobalVisibleRect(rect: Rect) {
      view.getGlobalVisibleRect(rect)
    }
  }

  class Compose(private val node: SemanticsNode) : RoboComponent {
    override val width: Int
      get() = node.layoutInfo.width
    override val height: Int
      get() = node.layoutInfo.height
    override val children: List<RoboComponent>
      get() = node.children.map {
        Compose(it)
      }
    override val text: String
      get() = node.printToString()
    override val visibility: Visibility
      get() = Visibility.Visible

    override fun getGlobalVisibleRect(rect: Rect) {
      val boundsInWindow = node.boundsInWindow
      rect.set(boundsInWindow.toAndroidRect())
    }
  }

  fun getGlobalVisibleRect(rect: Rect)

  val rect: Rect
    get() {
      val rect = Rect()
      getGlobalVisibleRect(rect)
      return rect
    }
  val children: List<RoboComponent>
  val text: String
  val visibility: Visibility
  val width: Int
  val height: Int

  fun depth(): Int {
    return (children.maxOfOrNull {
      it.depth()
    } ?: 0) + 1
  }

  fun countOfComponent(): Int {
    return children.sumOf {
      it.countOfComponent()
    } + 1
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

internal fun capture(rootComponent: RoboComponent, saveAction: (RoboCanvas) -> Unit) {
  val start = System.currentTimeMillis()
  val basicSize = 600
  val depthSlide = 30
  var depth = 0

  val deepestDepth = rootComponent.depth()
  val componentCount = rootComponent.countOfComponent()

  val canvas = RoboCanvas(
    rootComponent.width + basicSize + deepestDepth * depthSlide + componentCount * 20,
    rootComponent.height + basicSize + deepestDepth * depthSlide + componentCount * 20
  )
  val paddingRect = Rect(basicSize / 2, basicSize / 2, basicSize / 2, basicSize / 2)

  val paint = Paint().apply {
    color = Color.RED
    style = Paint.Style.FILL
    textSize = 30F
  }
  val textPaint = TextPaint()
  textPaint.isAntiAlias = true
  textPaint.textSize = 16F

  val queue = ArrayDeque<Pair<Int, RoboComponent>>()
  queue.add(0 to rootComponent)

  fun bfs() {
    while(queue.isNotEmpty()) {
      val (depth, component) = queue.removeFirst()
      val rect = Rect()
      component.getGlobalVisibleRect(rect)
      val canvasRect = Rect(
        rect.left + paddingRect.left + depth * depthSlide,
        rect.top + paddingRect.top + depth * depthSlide,
        rect.right + paddingRect.left + depth * depthSlide,
        rect.bottom + paddingRect.top + depth * depthSlide
      )

      val boxColor = colors[depth % colors.size] + (0xfF shl 56)
      val alphaBoxColor = when (component.visibility) {
        Visibility.Visible -> colors[depth % colors.size] + (0xEE shl 56) // alpha EE / FF
        Visibility.Gone -> colors[depth % colors.size] + (0x66 shl 56) // alpha 88 / FF
        Visibility.Invisible -> colors[depth % colors.size] + (0x88 shl 56) // alpha BB / FF
      }

      canvas.drawRect(canvasRect, paint.apply {
        color = alphaBoxColor
      })

      canvas.addPendingDraw {
        val texts =
          component.text.lines().flatMap {
            if (it.length > 30) it.chunked(30) else listOf(it)
          }
        val (rawBoxWidth, rawBoxHeight) = canvas.textCalc(texts)
        val textPadding = 5
        val boxWidth = rawBoxWidth + textPadding * 2
        val boxHeight = rawBoxHeight + textPadding * 2

        val (textPointX, textPointY) = findTextPoint(
          canvas,
          canvasRect.centerX(),
          canvasRect.centerY(),
          boxWidth,
          boxHeight
        )

        val textBoxRect = Rect(
          textPointX,
          textPointY,
          textPointX + boxWidth,
          textPointY + boxHeight
        )
        canvas.drawLine(
          Rect(
            canvasRect.centerX(), canvasRect.centerY(),
            textBoxRect.centerX(), textBoxRect.centerY()
          ), paint.apply {
            color = alphaBoxColor - (0x22 shl 56) // alpha DD / FF
            strokeWidth = 2F
          }
        )
        canvas.drawRect(
          textBoxRect, paint.apply {
            color = boxColor
          })
        canvas.drawText(
          textPointX.toFloat() + textPadding,
          textPointY.toFloat() + textPadding + rawBoxHeight / texts.size,
          texts,
          textPaint.apply {
            color = if (isColorBright(boxColor)) {
              Color.BLACK
            } else {
              Color.WHITE
            }
          }
        )
      }

      component.children.forEach { child ->
        queue.addLast(depth + 1 to child)
      }
    }
  }
  bfs()
  saveAction(canvas)
  val end = System.currentTimeMillis()
//  println("roborazzi takes " + (end - start) + "ms")
}

fun isColorBright(color: Int): Boolean {
  val red = Color.red(color)
  val green = Color.green(color)
  val blue = Color.blue(color)
  val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255
  return luminance > 0.5
}

fun findTextPoint(
  canvas: RoboCanvas,
  centerX: Int,
  centerY: Int,
  width: Int,
  height: Int
): Pair<Int, Int> {
  var searchPlaces = canvas.emptyPoints
    .filter { (x, y) ->
      x + width < canvas.width &&
        y + height < canvas.height
    }
    .sortedBy { (x, y) ->
      val xDiff = abs(x - centerX + width / 2)
      val yDiff = abs(y - centerY + height / 2)
      xDiff * xDiff + yDiff * yDiff
    }
  val binarySearch = listOf(3, 0, 5, 2, 4, 1)
  while (searchPlaces.isNotEmpty()) {
    val (x, y) = searchPlaces.first()
    var failPlace = -1 to -1
    if (binarySearch.all { xDiv ->
        binarySearch.all { yDiv ->
          val checkX = x + xDiv * width / 5
          val checkY = y + yDiv * height / 5
          val result =
            canvas.emptyPoints.contains(checkX - (checkX % 50) to checkY - (checkY % 50))
          if (!result) {
            failPlace = checkX to checkY
          }
          result
        }
      }
    ) {
      return x to y
    }
    searchPlaces = searchPlaces.filter { (x, y) ->
      !((x <= failPlace.first && failPlace.first <= x + width) &&
        (y <= failPlace.second && failPlace.second <= y + height))
    }
  }
  return 0 to 0
}