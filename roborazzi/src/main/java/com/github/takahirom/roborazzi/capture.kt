package com.github.takahirom.roborazzi

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.espresso.util.HumanReadables
import kotlin.math.abs

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

enum class Visibility {
  Visible,
  Gone,
  Invisible;
}

val hasCompose = try{
  Class.forName("androidx.compose.ui.platform.AbstractComposeView")
  true
} catch (e:Exception) {
  false
}

sealed interface RoboComponent {

  class View(view: android.view.View) : RoboComponent {
    override val width: Int = view.width
    override val height: Int = view.height
    override val rect: Rect = run {
      val rect = Rect()
      view.getGlobalVisibleRect(rect)
      rect
    }
    override val children: List<RoboComponent> = when {
      hasCompose && view is androidx.compose.ui.platform.AbstractComposeView -> {
        (view.getChildAt(0) as? ViewRootForTest)
          ?.semanticsOwner
          ?.rootSemanticsNode
          ?.let {
            listOf(Compose(it))
          } ?: listOf()
      }

      view is ViewGroup -> {
        (0 until view.childCount)
          .map { View(view.getChildAt(it)) }
      }

      else -> {
        listOf()
      }
    }

    val id: Int = view.id

    val idResourceName: String? =
      if (0xFFFFFFFF.toInt() == view.id) {
        null
      } else {
        try {
          view.resources.getResourceName(view.id)
        } catch (e: Exception) {
          null
        }
      }
    override val text: String = HumanReadables.describe(view)
    override val visibility: Visibility = when (view.visibility) {
      android.view.View.VISIBLE -> Visibility.Visible
      android.view.View.GONE -> Visibility.Gone
      else -> Visibility.Invisible
    }
  }

  class Compose(node: SemanticsNode) : RoboComponent {
    override val width: Int = node.layoutInfo.width
    override val height: Int = node.layoutInfo.height
    override val children: List<RoboComponent> = node.children.map {
      Compose(it)
    }
    override val text: String = node.printToString()
    override val visibility: Visibility = Visibility.Visible
    val testTag: String? = node.config.getOrNull(SemanticsProperties.TestTag)

    override val rect: Rect = run {
      val rect = Rect()
      val boundsInWindow = node.boundsInWindow
      rect.set(boundsInWindow.toAndroidRect())
      rect
    }
  }

  val rect: Rect
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

fun withViewId(@IdRes id: Int): (RoboComponent) -> Boolean {
  return { roboComponent ->
    when (roboComponent) {
      is RoboComponent.Compose -> false
      is RoboComponent.View -> roboComponent.id == id
    }
  }
}

fun withComposeTestTag(testTag: String): (RoboComponent) -> Boolean {
  return { roboComponent ->
    when (roboComponent) {
      is RoboComponent.Compose -> testTag == roboComponent.testTag
      is RoboComponent.View -> false
    }
  }
}

class CaptureOptions(
  val basicSize: Int = 600,
  val depthSlideSize: Int = 30,
  val query: ((RoboComponent) -> Boolean)? = null,
)

internal sealed interface QueryResult {
  object Disabled : QueryResult
  data class Enabled(val matched: Boolean) : QueryResult

  companion object {
    fun of(component: RoboComponent, query: ((RoboComponent) -> Boolean)?): QueryResult {
      if (query == null) return Disabled
      return Enabled(query(component))
    }
  }
}

internal fun capture(
  rootComponent: RoboComponent,
  captureOptions: CaptureOptions,
  onCanvas: (RoboCanvas) -> Unit
) {
  val start = System.currentTimeMillis()
  val basicSize = captureOptions.basicSize
  val depthSlide = captureOptions.depthSlideSize

  val deepestDepth = rootComponent.depth()
  val componentCount = rootComponent.countOfComponent()

  val canvas = RoboCanvas(
    rootComponent.rect.right + basicSize + deepestDepth * depthSlide + componentCount * 20,
    rootComponent.rect.bottom + basicSize + deepestDepth * depthSlide + componentCount * 20
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

  val depthAndComponentQueue = ArrayDeque<Pair<Int, RoboComponent>>()
  depthAndComponentQueue.add(0 to rootComponent)

  fun bfs() {
    while (depthAndComponentQueue.isNotEmpty()) {
      val (depth, component) = depthAndComponentQueue.removeFirst()
      val queryResult = QueryResult.of(component, captureOptions.query)
      fun Int.overrideByQuery(queryResult: QueryResult): Int = when (queryResult) {
        QueryResult.Disabled -> this
        is QueryResult.Enabled -> if (queryResult.matched) {
          RoboCanvas.TRANSPARENT_BIT
        } else {
          RoboCanvas.TRANSPARENT_STRONG
        }
      }
      canvas.addBaseDraw {
        val rect = component.rect
        val canvasRect = Rect(
          rect.left + paddingRect.left + depth * depthSlide,
          rect.top + paddingRect.top + depth * depthSlide,
          rect.right + paddingRect.left + depth * depthSlide,
          rect.bottom + paddingRect.top + depth * depthSlide
        )
        val boxColor = colors[depth % colors.size]
        val boxAlpha = when (component.visibility) {
          Visibility.Visible -> RoboCanvas.TRANSPARENT_BIT.overrideByQuery(queryResult) // alpha EE / FF
          Visibility.Invisible -> RoboCanvas.TRANSPARENT_MEDIUM.overrideByQuery(queryResult)  // alpha BB / FF
          Visibility.Gone -> RoboCanvas.TRANSPARENT_STRONG.overrideByQuery(queryResult) // alpha 88 / FF
        }

        val alphaBoxColor = boxColor + boxAlpha

        canvas.drawRect(canvasRect, paint.apply {
          color = alphaBoxColor
        })

        canvas.addPendingDraw {
          val componentRawText = component.text
          val texts = componentRawText.formattedTextList()
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
              color = alphaBoxColor
            })
          canvas.drawText(
            textPointX.toFloat() + textPadding,
            textPointY.toFloat() + textPadding + rawBoxHeight / texts.size,
            texts,
            textPaint.apply {
              color = if (isColorBright(alphaBoxColor)) {
                Color.BLACK - RoboCanvas.TRANSPARENT_NONE + boxAlpha
              } else {
                Color.WHITE - RoboCanvas.TRANSPARENT_NONE + boxAlpha
              }
            }
          )
        }
      }

      component.children.forEach { child ->
        depthAndComponentQueue.addLast(depth + 1 to child)
      }
    }
  }
  bfs()
  onCanvas(canvas)
  val end = System.currentTimeMillis()
//  println("roborazzi takes " + (end - start) + "ms")
}

fun isColorBright(color: Int): Boolean {
  val alpha = Color.alpha(color)
  val red = Color.red(color)
  val green = Color.green(color)
  val blue = Color.blue(color)
  val luminance = ((0.299 * red + 0.587 * green + 0.114 * blue) / 255) * alpha / 255
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


fun String.formattedTextList() = lines().flatMap { line ->
  // abc=d, e=f, g
  // ???
  // abc=d,
  // e=f, g
  var index = 0
  val lineLength = 30
  buildList {
    while (index < line.length) {
      val takenString = line.drop(index).take(lineLength)
      val lastIndex = takenString.lastIndexOf(",")
      if (takenString.length == 30 && lastIndex in 20..28) {
        this.add(takenString.substring(0, lastIndex + 2))
        index += lastIndex + 2
      } else {
        this.add(takenString)
        index += lineLength
      }
    }
  }
}