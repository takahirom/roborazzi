package com.github.takahirom.roborazzi

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.core.graphics.plus
import androidx.core.view.children
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
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
  perform(ImageCaptureViewAction(file))
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
          view.children.map {
            View(it)
          }.toList()
        }
        else -> {
          listOf()
        }
      }

    private val id: String
      get() = "id:" + try {
        view.resources.getResourceName(view.id)
      } catch (e: Exception) {
        ""
      }
    override val text: String
      get() = buildString {
        append(id)
        append("\nclassName:")
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

private class ImageCaptureViewAction(val file: File) : ViewAction {
  override fun getConstraints(): Matcher<View> {
    return Matchers.any(View::class.java)
  }

  override fun getDescription(): String {
    return String.format(Locale.ROOT, "capture view to image")
  }

  @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
  override fun perform(uiController: UiController, view: View) {
    val basicSize = 300
    val depthSlide = 30
    var depth = 0
    var index = 0

    val rootComponent = RoboComponent.View(view)
    val deepestDepth = rootComponent.depth()
    val componentCount = rootComponent.countOfComponent()

    val canvas = RoboCanvas(
      view.width * 2 + basicSize + deepestDepth * depthSlide + componentCount * 10,
      view.height * 2 + basicSize + deepestDepth * depthSlide + componentCount * 10
    )
    val paddingRect = Rect(view.width / 2, view.height / 2, view.width / 2, view.height / 2)

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
      val canvasRect = rect.plus(Point(paddingRect.left, paddingRect.top)).plus(depth * depthSlide)

      canvas.drawRect(canvasRect, paint.apply {
        color = colors[index % colors.size]
      })

      val boxColor = colors[index % colors.size]
      drawTexts.add {
        val text =
          component.text.lines().flatMap {
            if (it.length > 30) it.chunked(30) else listOf(it)
          }.joinToString("\n")
        val (boxWidth, boxHeight) = canvas.textCalc(text)

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
          textPointX + boxWidth + 5,
          textPointY + boxHeight + 5
        )
        canvas.drawLine(
          Rect(
            canvasRect.centerX(), canvasRect.centerY(),
            textBoxRect.centerX(), textBoxRect.centerY()
          ), paint.apply {
            color = boxColor
          }
        )
        canvas.drawRect(
          textBoxRect, paint.apply {
            color = boxColor
          })
        canvas.drawText(
          textPointX.toFloat(),
          textPointY.toFloat() + boxHeight / text.split("\n").size,
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
    canvas.save(file)
  }

  fun isColorBright(color: Int): Boolean {
    val red = Color.red(color)
    val green = Color.green(color)
    val blue = Color.blue(color)
    val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255
    return luminance > 0.5
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
