package com.github.takahirom.roborazzi

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.junit.Assert.*
import java.io.File
import java.util.Locale
import kotlin.math.abs

fun ViewInteraction.roboCapture(filePath: String) {
  roboCapture(File(filePath))
}

fun ViewInteraction.roboCapture(file: File) {
  perform(ImageCaptureViewAction { canvas ->
    canvas.save(file)
  })
}

fun ViewInteraction.roboAutoCapture(filePath: String, block: () -> Unit) {
  var removeListener = {}

  val canvases = mutableListOf<RoboCanvas>()

  val listener = ViewTreeObserver.OnGlobalLayoutListener {
    this@roboAutoCapture.perform(
      ImageCaptureViewAction { canvas ->
        canvases.add(canvas)
      }
    )
  }
  val viewTreeListenerAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> {
      return Matchers.any(View::class.java)
    }

    override fun getDescription(): String {
      return String.format(Locale.ROOT, "capture view to image")
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
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
    perform(viewTreeListenerAction)
  } catch (e: Exception) {
    val application = instrumentation.targetContext.applicationContext as Application
    application.registerActivityLifecycleCallbacks(activityCallbacks)
  }
  block()
  removeListener()
  val application = instrumentation.targetContext.applicationContext as Application
  application.unregisterActivityLifecycleCallbacks(activityCallbacks)
  val e = AnimatedGifEncoder()
  e.setRepeat(0)
  e.start(filePath)
  e.setDelay(1000)   // 1 frame per sec
  e.setSize(
    canvases.maxOf { it.croppedWidth },
    canvases.maxOf { it.croppedHeight }
  )
  canvases.forEach { canvas ->
    e.addFrame(canvas)
  }
  e.finish()
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
      get() = buildString {
        if (id.isNotBlank()) {
          appendLine("id:$id")
        }
        append("className:")
        append(view.javaClass.name)
        append("\nrect:")
        append(rect)
        append("\nvisibility:")
        append(
          when (view.visibility) {
            android.view.View.VISIBLE -> "VISIBLE"
            android.view.View.GONE -> "GONE"
            else -> "GONE"
          }
        )
        if (view is TextView) {
          append("\ntext:")
          append(
            view.text
          )
        }
      }
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
    override val children: List<RoboComponent>
      get() = node.children.map {
        Compose(it)
      }
    override val text: String
      get() = buildString {
        append("ComposeNode\nrect:")
        append(rect)
        append("\n")
        append(node.config)
        append("\n")
        append(node.layoutInfo)
      }
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

private class ImageCaptureViewAction(val action: (RoboCanvas) -> Unit) : ViewAction {
  override fun getConstraints(): Matcher<View> {
    return Matchers.any(View::class.java)
  }

  override fun getDescription(): String {
    return String.format(Locale.ROOT, "capture view to image")
  }

  @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
  override fun perform(uiController: UiController, view: View) {
    val basicSize = 500
    val depthSlide = 30
    var depth = 0
    var index = 0

    val rootComponent = RoboComponent.View(view)
    val deepestDepth = rootComponent.depth()
    val componentCount = rootComponent.countOfComponent()

    val canvas = RoboCanvas(
      view.width + basicSize + deepestDepth * depthSlide + componentCount * 20,
      view.height + basicSize + deepestDepth * depthSlide + componentCount * 20
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
    val drawTexts = mutableListOf<() -> Unit>()

    fun dfs(component: RoboComponent) {
      index++
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

      drawTexts.add {
        val text =
          component.text.lines().flatMap {
            if (it.length > 30) it.chunked(30) else listOf(it)
          }.joinToString("\n")
        val (rawBoxWidth, rawBoxHeight) = canvas.textCalc(text)
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
          textPointY.toFloat() + textPadding + rawBoxHeight / text.split("\n").size,
          text,
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
        depth++
        dfs(child)
        depth--
      }
    }
    dfs(rootComponent)
    drawTexts.forEach { it() }
    action(canvas)
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

    val searchPlaces = (-10000..10000 step 50)
      .flatMap { x -> (-10000..10000 step 50).map { y -> x to y } }
      .filter { (x, y) ->
        0 < x + centerX && x + centerX + width < canvas.width &&
          0 < y + centerY && y + centerY + height < canvas.height
      }
      .sortedBy { (x, y) -> abs(x + width / 2) + abs(y + height / 2) }
      .map { (x, y) -> (x + centerX) to (y + centerY) }
    for ((x, y) in searchPlaces) {
      if (
        (0..5).all { xDiv ->
          (0..5).all { yDiv ->
            canvas.getPixel(x + xDiv * width / 5, y + yDiv * height / 5) == 0
          }
        }
      ) {
        return x to y
      }
    }
    return 0 to 0
  }
}
