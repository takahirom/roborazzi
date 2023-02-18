@file:Suppress("UsePropertyAccessSyntax")

package com.github.takahirom.roborazzi

import android.graphics.Paint
import android.graphics.Rect
import java.awt.*
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class RoboCanvas(width: Int, height: Int) {
  private val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_USHORT_565_RGB)
  val width: Int get() = bufferedImage.width
  val height: Int get() = bufferedImage.height
  val croppedWidth: Int get() = croppedImage.width
  val croppedHeight: Int get() = croppedImage.height
  private var rightBottomPoint = 0 to 0
  private fun updateRightBottom(x: Int, y: Int) {
    rightBottomPoint = maxOf(x, rightBottomPoint.first) to maxOf(y, rightBottomPoint.second)
  }

  var emptyPoints = (0..width step 50)
    .flatMap { x -> (0..height step 50).map { y -> x to y } }
    .toMutableSet()
    private set

  fun drawRect(r: Rect, paint: Paint) {
    bufferedImage.graphics { graphics2D ->
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
  }

  fun drawLine(r: Rect, paint: Paint) {
    bufferedImage.graphics { graphics2D ->
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
    }
  }

  private val textCache = hashMapOf<String, TextLayout>()

  fun textCalc(texts: List<String>): Pair<Int, Int> {
    return bufferedImage.graphics { graphics2D ->
      val frc: FontRenderContext = graphics2D.getFontRenderContext()
      val longestLineWidth = texts.map {
        calcTextLayout(
          it,
          graphics2D,
          frc
        ).getPixelBounds(frc, 0F, 0F).width
      }.maxBy {
        it
      }
      longestLineWidth to (texts.sumOf {
        calcTextLayout(it, graphics2D, frc).bounds.height + 1
      }).toInt()
    }
  }

  fun drawText(textPointX: Float, textPointY: Float, texts: List<String>, paint: Paint) {
    bufferedImage.graphics { graphics: Graphics2D ->
      val graphics2D = bufferedImage.createGraphics()
      graphics2D.color = Color(paint.getColor(), true)

      val frc: FontRenderContext = graphics2D.getFontRenderContext()

      var nextY = textPointY
      for (text in texts) {
        val layout = calcTextLayout(text, graphics2D, frc)
        val height = layout.bounds.height
        layout.draw(
          graphics2D,
          textPointX,
          nextY
        )
        nextY += (height + 1).toInt()
      }
    }
  }

  private fun calcTextLayout(
    text: String,
    graphics2D: Graphics2D,
    frc: FontRenderContext
  ) = textCache.getOrPut(text) {
    TextLayout(text, graphics2D.font, frc)
  }

  fun getPixel(x: Int, y: Int): Int {
    return bufferedImage.getRGB(x, y)
  }

  fun outputImage(): BufferedImage {
    return croppedImage
  }

  private val croppedImage by lazy {
    drawPendingDraw()
    bufferedImage.getSubimage(0, 0, rightBottomPoint.first, rightBottomPoint.second)
  }

  var baseDrawList = mutableListOf<() -> Unit>()

  fun addBaseDraw(baseDraw: () -> Unit) {
    baseDrawList.add(baseDraw)
  }

  var pendingDrawList = mutableListOf<() -> Unit>()
  fun addPendingDraw(pendingDraw: () -> Unit) {
    pendingDrawList.add(pendingDraw)
  }

  fun save(file: File) {
    drawPendingDraw()
    ImageIO.write(
      croppedImage,
      "png",
      file
    )
  }

  private fun drawPendingDraw() {
//    val start = System.currentTimeMillis()
    baseDrawList.forEach { it() }
    if (baseDrawList.isNotEmpty()) {
      baseDrawList.clear()
    }
    pendingDrawList.forEach { it() }
//    val end = System.currentTimeMillis()
    if (pendingDrawList.isNotEmpty()) {
//      println("roborazzi pending drawing takes ${end - start} ms")
      pendingDrawList.clear()
    }
  }

  fun release() {
    bufferedImage.flush()
    croppedImage.flush()
    textCache.clear()
  }

  companion object {
    const val TRANSPARENT_NONE = 0xFF shl 56
    const val TRANSPARENT_BIT = 0xEE shl 56
    const val TRANSPARENT_MEDIUM = 0x88 shl 56
    const val TRANSPARENT_STRONG = 0x66 shl 56
  }
}

private fun <T> BufferedImage.graphics(block: (Graphics2D) -> T): T {
  val graphics = createGraphics()
  graphics.font = Font("Courier New", Font.BOLD, 12)
  val result = block(graphics)
  graphics.dispose()
  return result
}
