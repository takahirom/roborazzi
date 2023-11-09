@file:Suppress("UsePropertyAccessSyntax")

package com.github.takahirom.roborazzi

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import com.dropbox.differ.ImageComparator
import java.awt.*
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO


class AwtRoboCanvas(width: Int, height: Int, filled: Boolean, bufferedImageType: Int): RoboCanvas {
  internal val bufferedImage = BufferedImage(width, height, bufferedImageType)
  override val width: Int get() = bufferedImage.width
  override val height: Int get() = bufferedImage.height
  override val croppedWidth: Int get() = croppedImage.width
  override val croppedHeight: Int get() = croppedImage.height
  private var rightBottomPoint = if (filled) width to height else 0 to 0
  private fun updateRightBottom(x: Int, y: Int) {
    rightBottomPoint = maxOf(x, rightBottomPoint.first) to maxOf(y, rightBottomPoint.second)
  }

  var emptyPoints: MutableSet<Pair<Int, Int>> = (0..width step 50)
    .flatMap { x -> (0..height step 50).map { y -> x to y } }
    .toMutableSet()
    private set

  fun drawImage(r: Rect, bitmap: Bitmap) {
    bufferedImage.graphics { graphics2D ->
      graphics2D.drawImage(
        BitmapToBufferedImageConverter.convert(bitmap),
        r.left,
        r.top,
        r.width(),
        r.height(),
        null
      )
    }
    updateRightBottom(r.right, r.bottom)
    consumeEmptyPoints(r)
  }

  fun drawImage(image: BufferedImage) {
    bufferedImage.graphics { graphics2D ->
      graphics2D.drawImage(
        image,
        0,
        0,
        null
      )
    }
    updateRightBottom(image.width, image.height)
//    consumeEmptyPoints(r)
  }

  fun drawRectOutline(r: Rect, paint: Paint) {
    bufferedImage.graphics { graphics2D ->
      graphics2D.color = Color(paint.getColor(), true)
      val stroke = BasicStroke(minOf(r.width().toFloat(), r.height().toFloat()) / 20)
      graphics2D.setStroke(stroke)
      graphics2D.drawRect(
        r.left, r.top,
        (r.right - r.left), (r.bottom - r.top)
      )
      updateRightBottom(r.right, r.bottom)

      consumeEmptyPoints(r)
    }
  }

  fun drawRect(r: Rect, paint: Paint) {
    bufferedImage.graphics { graphics2D ->
      graphics2D.color = Color(paint.getColor(), true)
      graphics2D.fillRect(
        r.left, r.top,
        (r.right - r.left), (r.bottom - r.top)
      )
      graphics2D.dispose()
      updateRightBottom(r.right, r.bottom)

      consumeEmptyPoints(r)
    }
  }

  private fun consumeEmptyPoints(r: Rect) {
    emptyPoints -= ((r.left - r.left % 50)..r.right step 50).flatMap { x ->
      ((r.top - r.top % 50)..r.bottom step 50).map { y -> x to y }
    }.toSet()
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

  internal fun outputImage(resizeImage: Double): BufferedImage {
    return croppedImage.scale(resizeImage)
  }

  private val croppedImage by lazy {
    drawPendingDraw()
    bufferedImage.getSubimage(
      /* x = */ 0,
      /* y = */ 0,
      /* w = */ minOf(bufferedImage.width, rightBottomPoint.first),
      /* h = */ minOf(bufferedImage.height, rightBottomPoint.second)
    )
  }

  var baseDrawList = mutableListOf<() -> Unit>()

  fun addBaseDraw(baseDraw: () -> Unit) {
    baseDrawList.add(baseDraw)
  }

  var pendingDrawList = mutableListOf<() -> Unit>()
  fun addPendingDraw(pendingDraw: () -> Unit) {
    pendingDrawList.add(pendingDraw)
  }

  override fun save(file: File, resizeScale: Double) {
    drawPendingDraw()
    val directory = file.parentFile
    try {
      if (!directory.exists()) {
        directory.mkdirs()
      }
    } catch (e: Exception) {
      // ignore
    }
    ImageIO.write(
      croppedImage.scale(resizeScale),
      "png",
      file
    )
  }

  override fun differ(other: RoboCanvas, resizeScale: Double, imageComparator: ImageComparator): ImageComparator.ComparisonResult {
    other as AwtRoboCanvas
    val otherImage = other.bufferedImage
    return imageComparator.compare(
      DifferBufferedImage(croppedImage.scale(resizeScale)),
      DifferBufferedImage(otherImage)
    )
  }

  internal fun drawPendingDraw() {
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

  override fun release() {
    bufferedImage.flush()
    croppedImage.flush()
    textCache.clear()
  }

  companion object {
    fun load(file: File, bufferedImageType: Int): AwtRoboCanvas {
      val loadedImage: BufferedImage = ImageIO.read(file)
      val awtRoboCanvas = AwtRoboCanvas(
        loadedImage.width,
        height = loadedImage.height,
        filled = true,
        bufferedImageType = bufferedImageType
      )
      awtRoboCanvas.drawImage(loadedImage)
      return awtRoboCanvas
    }

    const val TRANSPARENT_NONE = 0xFF shl 56
    const val TRANSPARENT_BIT = 0xEE shl 56
    const val TRANSPARENT_MEDIUM = 0x88 shl 56
    const val TRANSPARENT_STRONG = 0x66 shl 56

    fun generateCompareCanvas(
      goldenCanvas: AwtRoboCanvas,
      newCanvas: AwtRoboCanvas,
      newCanvasResize: Double,
      bufferedImageType: Int
    ): AwtRoboCanvas {
      newCanvas.drawPendingDraw()
      val image1 = goldenCanvas.bufferedImage
      val image2 = newCanvas.bufferedImage.scale(newCanvasResize)
      val diff = generateDiffImage(image1, image2)
      val width = image1.width + diff.width + image2.width
      val height = image1.height.coerceAtLeast(diff.height).coerceAtLeast(image2.height)

      val combined = BufferedImage(width, height, bufferedImageType)

      val g = combined.createGraphics()
      g.drawImage(image1, 0, 0, null)
      g.drawImage(diff, image1.width, 0, null)
      g.drawImage(image2, image1.width + diff.width, 0, null)
      g.dispose()
      return AwtRoboCanvas(
        width = width,
        height = height,
        filled = true,
        bufferedImageType = bufferedImageType
      ).apply {
        drawImage(combined)
      }
    }

    private fun generateDiffImage(
      originalImage: BufferedImage,
      comparedImage: BufferedImage
    ): BufferedImage {
      val width = minOf(originalImage.width, comparedImage.width)
      val height = minOf(originalImage.height, comparedImage.height)
      val diffImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
      for (x in 0 until width) {
        for (y in 0 until height) {
          val rgbOrig = originalImage.getRGB(x, y)
          val rgbComp = comparedImage.getRGB(x, y)
          if (rgbOrig != rgbComp) {
            diffImage.setRGB(x, y, -0x10000)
          } else {
            0x0
          }
        }
      }
      return diffImage
    }
  }
}

private fun BufferedImage.scale(scale: Double): BufferedImage {
  val before: BufferedImage = this
  val w = before.width * scale
  val h = before.height * scale
  val after = BufferedImage(w.toInt(), h.toInt(), before.type)
  val at = AffineTransform()
  at.scale(scale, scale)
  val scaleOp = AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR)
  scaleOp.filter(before, after)
  return after
}

private fun <T> BufferedImage.graphics(block: (Graphics2D) -> T): T {
  val graphics = createGraphics()
  graphics.font = Font("Courier New", Font.BOLD, 12)
  val result = block(graphics)
  graphics.dispose()
  return result
}
