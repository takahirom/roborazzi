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


class AwtRoboCanvas(width: Int, height: Int, filled: Boolean, bufferedImageType: Int) : RoboCanvas {
  private val bufferedImage = BufferedImage(width, height, bufferedImageType)
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

  private var baseDrawList = mutableListOf<() -> Unit>()

  fun addBaseDraw(baseDraw: () -> Unit) {
    baseDrawList.add(baseDraw)
  }

  private var pendingDrawList = mutableListOf<() -> Unit>()
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

  override fun differ(
    other: RoboCanvas,
    resizeScale: Double,
    imageComparator: ImageComparator
  ): ImageComparator.ComparisonResult {
    other as AwtRoboCanvas
    val otherImage = other.bufferedImage
    return imageComparator.compare(
      DifferBufferedImage(croppedImage.scale(resizeScale)),
      DifferBufferedImage(otherImage)
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

    sealed interface ComparisonCanvasParameters {
      abstract val referenceImage: BufferedImage
      abstract val newImage: BufferedImage
      abstract val diffImage: BufferedImage
      abstract val newCanvasResize: Double
      abstract val bufferedImageType: Int
      abstract val comparisonImageWidth: Int
      abstract val comparisonImageHeight: Int

      data class Grid(
        val goldenCanvas: AwtRoboCanvas,
        val newCanvas: AwtRoboCanvas,
        override val newCanvasResize: Double,
        override val bufferedImageType: Int,
        override val referenceImage: BufferedImage,
        override val newImage: BufferedImage,
        override val diffImage: BufferedImage,
        val oneDpPx: Float,
        val bigLineSpaceDp: Int? = 16,
        val smallLineSpaceDp: Int? = 4,
        val hasLabel: Boolean = true
      ) : ComparisonCanvasParameters {

        val margin = (16 * oneDpPx).toInt()
        override val comparisonImageWidth: Int
          get() = goldenCanvas.width + newCanvas.width + diffImage.width + 2 * margin

        override val comparisonImageHeight: Int
          get() = goldenCanvas.height.coerceAtLeast(newCanvas.height) + 2 * margin
      }

      data class Simple(
        val goldenCanvas: AwtRoboCanvas,
        val newCanvas: AwtRoboCanvas,
        override val newCanvasResize: Double,
        override val bufferedImageType: Int,
        override val referenceImage: BufferedImage,
        override val newImage: BufferedImage,
        override val diffImage: BufferedImage,
      ) : ComparisonCanvasParameters {
        override val comparisonImageWidth: Int
          get() = goldenCanvas.width + newCanvas.width

        override val comparisonImageHeight: Int
          get() = goldenCanvas.height.coerceAtLeast(newCanvas.height)
      }

      companion object {
        fun create(
          goldenCanvas: AwtRoboCanvas,
          newCanvas: AwtRoboCanvas,
          newCanvasResize: Double,
          bufferedImageType: Int,
          oneDpPx: Float?,
          comparisonComparisonStyle: RoborazziOptions.CompareOptions.ComparisonStyle,
        ): ComparisonCanvasParameters {
          newCanvas.drawPendingDraw()

          val referenceImage = goldenCanvas.bufferedImage
          val newImage =
            newCanvas.bufferedImage.scale(newCanvasResize)
          val diffImage = generateDiffImage(referenceImage, newImage)
          return if (comparisonComparisonStyle is RoborazziOptions.CompareOptions.ComparisonStyle.Grid && oneDpPx != null) {
            Grid(
              goldenCanvas = goldenCanvas,
              newCanvas = newCanvas,
              newCanvasResize = newCanvasResize,
              bufferedImageType = bufferedImageType,
              referenceImage = referenceImage,
              newImage = newImage,
              diffImage = diffImage,
              oneDpPx = oneDpPx,
              bigLineSpaceDp = comparisonComparisonStyle.bigLineSpaceDp,
              smallLineSpaceDp = comparisonComparisonStyle.smallLineSpaceDp,
              hasLabel = comparisonComparisonStyle.hasLabel
            )
          } else {
            if (comparisonComparisonStyle is RoborazziOptions.CompareOptions.ComparisonStyle.Grid) {
              debugLog { "Roborazzi can't find the oneDpPx, so fall back to CompareOptions.Format.Simple comparison format" }
            }
            Simple(
              goldenCanvas = goldenCanvas,
              newCanvas = newCanvas,
              newCanvasResize = newCanvasResize,
              bufferedImageType = bufferedImageType,
              referenceImage = referenceImage,
              newImage = newImage,
              diffImage = diffImage,
            )
          }
        }
      }
    }

    fun generateCompareCanvas(
      comparisonCanvasParameters: ComparisonCanvasParameters
    ): AwtRoboCanvas {
      val referenceImage = comparisonCanvasParameters.referenceImage
      val newImage = comparisonCanvasParameters.newImage
      val diffImage = comparisonCanvasParameters.diffImage
      val comparisonImageWidth = comparisonCanvasParameters.comparisonImageWidth
      val comparisonImageHeight =
        comparisonCanvasParameters.comparisonImageHeight

      val comparisonImage =
        BufferedImage(
          comparisonImageWidth,
          comparisonImageHeight,
          comparisonCanvasParameters.bufferedImageType
        )

      val comparisonImageGraphics = comparisonImage.createGraphics()
      when (comparisonCanvasParameters) {
        is ComparisonCanvasParameters.Grid -> {
          val margin = comparisonCanvasParameters.margin
          // Grid lines with 16 and 4 px spacing
          val oneDpPx = comparisonCanvasParameters.oneDpPx
          comparisonImageGraphics.stroke = BasicStroke(1F)
          comparisonImageGraphics.color = Color(0x33777777, true)
          comparisonCanvasParameters.smallLineSpaceDp?.let { smallSpaceDp ->
            for (y in 0 until comparisonImageHeight step (smallSpaceDp * oneDpPx).toInt()) {
              comparisonImageGraphics.drawLine(0, y, comparisonImageWidth, y)
            }
            for (x in 0 until (margin + referenceImage.width) step (smallSpaceDp * oneDpPx).toInt()) {
              comparisonImageGraphics.drawLine(x, 0, x, comparisonImageHeight)
            }
            for (x in (margin + referenceImage.width) until (margin + referenceImage.width + newImage.width) step (smallSpaceDp * oneDpPx).toInt()) {
              comparisonImageGraphics.drawLine(x, 0, x, comparisonImageHeight)
            }
            for (x in (margin + referenceImage.width + newImage.width) until comparisonImageWidth step (smallSpaceDp * oneDpPx).toInt()) {
              comparisonImageGraphics.drawLine(x, 0, x, comparisonImageHeight)
            }
          }

          comparisonImageGraphics.color = Color(0x99777777.toInt(), true)
          comparisonCanvasParameters.bigLineSpaceDp?.let { bigSpaceDp ->
            for (y in 0 until comparisonImageHeight step (bigSpaceDp * oneDpPx).toInt()) {
              comparisonImageGraphics.drawLine(0, y, comparisonImageWidth, y)
            }

            for (x in 0 until (margin + referenceImage.width) step (bigSpaceDp * oneDpPx).toInt()) {
              comparisonImageGraphics.drawLine(x, 0, x, comparisonImageHeight)
            }
            for (x in (margin + referenceImage.width) until (margin + referenceImage.width + newImage.width) step (bigSpaceDp * oneDpPx).toInt()) {
              comparisonImageGraphics.drawLine(x, 0, x, comparisonImageHeight)
            }
            for (x in (margin + referenceImage.width + newImage.width) until comparisonImageWidth step (bigSpaceDp * oneDpPx).toInt()) {
              comparisonImageGraphics.drawLine(x, 0, x, comparisonImageHeight)
            }
          }
          if (comparisonCanvasParameters.hasLabel) {
            // draw rect for texts
            fun drawStringWithBackgroundRect(text: String, x: Int, y: Int, fontSize: Int) {
              // fill with 4dp margin
              val textMargin = (4 * oneDpPx).toInt()
              // Set size to 12dp
              val font = Font("Courier New", Font.BOLD, fontSize)
              val textLayout = TextLayout(text, font, comparisonImageGraphics.fontRenderContext)
              val bounds = textLayout.bounds
              val rect = Rectangle(
                x,
                y - bounds.height.toInt(),
                bounds.width.toInt(),
                bounds.height.toInt()
              )
              comparisonImageGraphics.color = Color(0x55999999, true)
              comparisonImageGraphics.fillRect(
                rect.x,
                rect.y - textMargin * 2,
                rect.width + textMargin * 2,
                rect.height + textMargin * 2
              )
              comparisonImageGraphics.color = Color.BLACK
              textLayout.draw(
                comparisonImageGraphics,
                x.toFloat() + textMargin,
                y.toFloat() - textMargin
              )
            }

            val fontSize = (12 * oneDpPx).toInt()
            drawStringWithBackgroundRect("Reference", margin, margin, fontSize)
            drawStringWithBackgroundRect("Diff", referenceImage.width + margin, margin, fontSize)
            drawStringWithBackgroundRect(
              "New",
              referenceImage.width + diffImage.width + margin,
              margin,
              fontSize
            )
          }
          comparisonImageGraphics.drawImage(referenceImage, margin, margin, null)
          comparisonImageGraphics.drawImage(diffImage, referenceImage.width + margin, margin, null)
          comparisonImageGraphics.drawImage(
            newImage,
            referenceImage.width + diffImage.width + margin,
            margin,
            null
          )

        }

        is ComparisonCanvasParameters.Simple -> {
          comparisonImageGraphics.drawImage(referenceImage, 0, 0, null)
          comparisonImageGraphics.drawImage(diffImage, referenceImage.width, 0, null)
          comparisonImageGraphics.drawImage(
            newImage,
            referenceImage.width + diffImage.width,
            0,
            null
          )
        }
      }
      comparisonImageGraphics.dispose()
      return AwtRoboCanvas(
        width = comparisonImageWidth,
        height = comparisonImageHeight,
        filled = true,
        bufferedImageType = comparisonCanvasParameters.bufferedImageType
      ).apply {
        drawImage(comparisonImage)
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
