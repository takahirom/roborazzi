package com.github.takahirom.roborazzi

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
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

internal fun captureDump(
  rootComponent: RoboComponent,
  dumpOptions: Dump,
  recordOptions: RoborazziOptions.RecordOptions,
  onCanvas: (AwtRoboCanvas) -> Unit
) {
  val start = System.currentTimeMillis()
  val basicSize = dumpOptions.basicSize
  val depthSlide = dumpOptions.depthSlideSize

  val deepestDepth = rootComponent.depth()
  val componentCount = rootComponent.countOfComponent()

  val canvas = AwtRoboCanvas(
    width = rootComponent.rect.right + basicSize + deepestDepth * depthSlide + componentCount * 20,
    height = rootComponent.rect.bottom + basicSize + deepestDepth * depthSlide + componentCount * 20,
    filled = false,
    bufferedImageType = recordOptions.pixelBitConfig.toBufferedImageType(),
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
      val queryResult = QueryResult.of(component, dumpOptions.query)
      fun Int.overrideByQuery(queryResult: QueryResult): Int = when (queryResult) {
        QueryResult.Disabled -> this
        is QueryResult.Enabled -> if (queryResult.matched) {
          AwtRoboCanvas.TRANSPARENT_BIT
        } else {
          AwtRoboCanvas.TRANSPARENT_STRONG
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
          Visibility.Visible -> AwtRoboCanvas.TRANSPARENT_BIT.overrideByQuery(queryResult) // alpha EE / FF
          Visibility.Invisible -> AwtRoboCanvas.TRANSPARENT_MEDIUM.overrideByQuery(queryResult)  // alpha BB / FF
          Visibility.Gone -> AwtRoboCanvas.TRANSPARENT_STRONG.overrideByQuery(queryResult) // alpha 88 / FF
        }

        val alphaBoxColor = boxColor + boxAlpha

        val rootImage = component.image
        if (rootImage != null) {
          canvas.drawImage(canvasRect, rootImage)
          canvas.drawRectOutline(canvasRect, paint.apply {
            color = alphaBoxColor
          })
        } else {
          canvas.drawRect(canvasRect, paint.apply {
            color = alphaBoxColor
          })
        }

        canvas.addPendingDraw {
          val componentRawText = dumpOptions.explanation(component) ?: return@addPendingDraw
          val texts = componentRawText.formattedTextList()

          val isAllBlank = texts.isEmpty() || texts.all { it.isBlank() }
          if (isAllBlank) {
            return@addPendingDraw
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
              color = alphaBoxColor
            })
          canvas.drawText(
            textPointX.toFloat() + textPadding,
            textPointY.toFloat() + textPadding + rawBoxHeight / texts.size,
            texts,
            textPaint.apply {
              color = if (isColorBright(alphaBoxColor)) {
                Color.BLACK - AwtRoboCanvas.TRANSPARENT_NONE + boxAlpha
              } else {
                Color.WHITE - AwtRoboCanvas.TRANSPARENT_NONE + boxAlpha
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
  canvas: AwtRoboCanvas,
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
  // ↓
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