@file:Suppress("UsePropertyAccessSyntax")

package com.github.takahirom.roborazzi

import android.graphics.Paint
import android.graphics.Rect
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class RoboCanvas(width: Int, height: Int) {
  private val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
  val width get() = bufferedImage.width
  val height get() = bufferedImage.height
  val croppedWidth get() = croppedImage().width
  val croppedHeight get() = croppedImage().height
  private var rightBottomPoint = 0 to 0
  private fun updateRightBottom(x: Int, y: Int) {
    rightBottomPoint = maxOf(x, rightBottomPoint.first) to maxOf(y, rightBottomPoint.second)
  }

  fun drawRect(r: Rect, paint: Paint) {
    val graphics2D: Graphics2D = bufferedImage.createGraphics()

    graphics2D.color = Color(paint.getColor(), true)
    graphics2D.fillRect(
      r.left, r.top,
      (r.right - r.left), (r.bottom - r.top)
    )
    graphics2D.dispose()
    updateRightBottom(r.right, r.bottom)

    emptyPoints -= ((r.left - r.left % 50)..r.right step 50).flatMap { x ->
      ((r.top - r.top % 50)..r.bottom step 50).map { y -> x to y }
    }.toSet()
  }

  fun drawLine(r: Rect, paint: Paint) {
    val graphics2D: Graphics2D = bufferedImage.createGraphics()
    graphics2D.stroke = BasicStroke(paint.strokeWidth)
    graphics2D.paint = Color(paint.getColor(), true)
    graphics2D.setRenderingHint(
      RenderingHints.KEY_ANTIALIASING,
      RenderingHints.VALUE_ANTIALIAS_ON
    )
    graphics2D.drawLine(
      r.left, r.top,
      r.right, r.bottom
    )
    graphics2D.dispose()
  }

  private val textCache = hashMapOf<String, TextLayout>()

  fun textCalc(texts: List<String>): Pair<Int, Int> {
    val graphics2D: Graphics2D = bufferedImage.createGraphics()
    val frc: FontRenderContext = graphics2D.getFontRenderContext()
    val longestLine = texts.maxBy {
      textCache.getOrPut(it) {
        TextLayout(
          it,
          graphics2D.font,
          frc
        )
      }.bounds.width.toInt()
    }
    val longestLayout =
      textCache.getOrPut(longestLine) { TextLayout(longestLine, graphics2D.font, frc) }
    graphics2D.dispose()
    return longestLayout.bounds.width.toInt() to (texts.sumOf {
      calcHeight(it, graphics2D, frc) + 1
    }).toInt()
  }

  fun drawText(textPointX: Float, textPointY: Float, texts: List<String>, paint: Paint) {
    val graphics2D = bufferedImage.createGraphics()
    graphics2D.color = Color(paint.getColor())

    val frc: FontRenderContext = graphics2D.getFontRenderContext()
    var nextY = textPointY
    for (text in texts) {
      val height = calcHeight(text, graphics2D, frc)
      graphics2D.drawString(
        text,
        textPointX.toInt(),
        nextY.toInt()
      )
      nextY += (height + 1).toInt()
    }
    graphics2D.dispose()
  }

  private fun calcHeight(
    text: String,
    graphics2D: Graphics2D,
    frc: FontRenderContext
  ) = textCache.getOrPut(text) {
    TextLayout(text, graphics2D.font, frc)
  }.bounds.height

  fun getPixel(x: Int, y: Int): Int {
    return bufferedImage.getRGB(x, y)
  }

  fun outputImage(): BufferedImage {
    return croppedImage()
  }

  private fun croppedImage(): BufferedImage {
    drawPendingDraw()
    return bufferedImage.getSubimage(0, 0, rightBottomPoint.first, rightBottomPoint.second)
  }

  var pendingDrawList = mutableListOf<() -> Unit>()

  fun addPendingDraw(pendingDraw: () -> Unit) {
    pendingDrawList.add(pendingDraw)
  }

  fun save(file: File) {
    drawPendingDraw()
    ImageIO.write(
      croppedImage(),
      "png",
      file
    )
  }

  private fun drawPendingDraw() {
    val start = System.currentTimeMillis()
    pendingDrawList.forEach { it() }
    val end = System.currentTimeMillis()
    if (pendingDrawList.isNotEmpty()) {
      println("roborazzi pending drawing takes ${end - start} ms")
      pendingDrawList.clear()
    }
  }

  var emptyPoints = (0..width step 50)
    .flatMap { x -> (0..height step 50).map { y -> x to y } }
    .toMutableSet()
    private set
}