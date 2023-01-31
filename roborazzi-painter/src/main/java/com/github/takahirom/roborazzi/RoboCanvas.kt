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
  var rightBottomPoint = 0 to 0
  fun updateRightBottom(x: Int, y: Int) {
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

  fun textCalc(text: String): Pair<Int, Int> {
    val texts = text.split("\n")
    val graphics2D: Graphics2D = bufferedImage.createGraphics()
    val frc: FontRenderContext = graphics2D.getFontRenderContext()
    val longestLine = texts.maxBy { TextLayout(it, graphics2D.font, frc).bounds.width.toInt() }
    val highestLine = texts.maxBy { TextLayout(it, graphics2D.font, frc).bounds.height.toInt() }
    val longestLayout = TextLayout(longestLine, graphics2D.font, frc)
    val highteestLayout = TextLayout(highestLine, graphics2D.font, frc)
    graphics2D.dispose()
    return longestLayout.bounds.width.toInt() to (highteestLayout.getPixelBounds(
      frc,
      0F,
      0F
    ).height * texts.size + 1).toInt()
  }

  fun drawText(textPointX: Float, textPointY: Float, text: String, paint: Paint) {
    val graphics2D = bufferedImage.createGraphics()
    graphics2D.color = Color(paint.getColor())

    val frc: FontRenderContext = graphics2D.getFontRenderContext()
    val layout = TextLayout(text, graphics2D.font, frc)
    val outputs: List<String> = text.split("\n")
    for (i in outputs.indices) {
      graphics2D.drawString(
        outputs[i],
        textPointX.toInt(),
        (textPointY + i * layout.bounds.height + 0.5).toInt()
      )
    }
    graphics2D.dispose()
  }

  fun getPixel(x: Int, y: Int): Int {
    return bufferedImage.getRGB(x, y)
  }

  fun save(file: File) {
    ImageIO.write(
      bufferedImage.getSubimage(0, 0, rightBottomPoint.first, rightBottomPoint.second),
      "png",
      file
    )
  }
}