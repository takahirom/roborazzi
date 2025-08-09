@file:Suppress("UsePropertyAccessSyntax")

package com.github.takahirom.roborazzi

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import com.dropbox.differ.ImageComparator
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.io.File

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

  enum class CompositeMode {
    SrcOver,
    Src,
  }

  fun drawImage(image: BufferedImage, compositeMode: CompositeMode = CompositeMode.SrcOver) {
    bufferedImage.graphics { graphics2D ->
      graphics2D.setComposite(
        when (compositeMode) {
          CompositeMode.SrcOver -> AlphaComposite.SrcOver
          CompositeMode.Src -> AlphaComposite.Src
        }
      )
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
    bufferedImage.graphics {
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
    val w = minOf(bufferedImage.width, rightBottomPoint.first)
    val h = minOf(bufferedImage.height, rightBottomPoint.second)
    if (w == bufferedImage.width && h == bufferedImage.height) {
      roborazziDebugLog {
        "AwtRoboCanvas.croppedImage croppedImage is same size as original image, so return original image without cropping."
      }
      return@lazy bufferedImage
    }
    bufferedImage.getSubimage(
      /* x = */ 0,
      /* y = */ 0,
      /* w = */ w,
      /* h = */ h
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

  override fun save(
    path: String,
    resizeScale: Double,
    contextData: Map<String, Any>,
    imageIoFormat: ImageIoFormat,
  ) = measurePerformance("canvas_save") {
    val file = File(path)
    drawPendingDraw()
    val directory = file.parentFile
    try {
      if (!directory.exists()) {
        directory.mkdirs()
      }
    } catch (e: Exception) {
      // ignore
    }
    val scaledBufferedImage = croppedImage.scale(resizeScale)
    (imageIoFormat as JvmImageIoFormat)
      .awtImageWriter.write(
        destFile = file,
        contextData = contextData,
        image = scaledBufferedImage
      )
  }

  override fun differ(
    other: RoboCanvas,
    resizeScale: Double,
    imageComparator: ImageComparator
  ): ImageComparator.ComparisonResult = measurePerformance("image_comparison") {
    other as AwtRoboCanvas
    val otherImage = other.bufferedImage
    imageComparator.compare(
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
    fun load(file: File, bufferedImageType: Int, imageIoFormat: ImageIoFormat): AwtRoboCanvas = measurePerformance("load_golden_file") {
      val loadedImage: BufferedImage = (imageIoFormat as JvmImageIoFormat).awtImageLoader.load(file)
      val awtRoboCanvas = AwtRoboCanvas(
        loadedImage.width,
        height = loadedImage.height,
        filled = true,
        bufferedImageType = bufferedImageType
      )
      // We should use Src here, it changes the transparent color
      // https://github.com/takahirom/roborazzi/issues/292
      awtRoboCanvas.drawImage(loadedImage, CompositeMode.Src)
      awtRoboCanvas
    }

    const val TRANSPARENT_NONE = 0xFF shl 56
    const val TRANSPARENT_BIT = 0xEE shl 56
    const val TRANSPARENT_MEDIUM = 0x88 shl 56
    const val TRANSPARENT_STRONG = 0x66 shl 56

    sealed interface ComparisonCanvasParameters {
      val referenceImage: BufferedImage
      val newImage: BufferedImage
      val diffImage: BufferedImage
      val newCanvasResize: Double
      val bufferedImageType: Int
      val comparisonImageWidth: Int
      val comparisonImageHeight: Int

      data class Grid(
        val goldenCanvas: AwtRoboCanvas,
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
          get() = goldenCanvas.width + newImage.width + diffImage.width + 2 * margin

        override val comparisonImageHeight: Int
          get() = goldenCanvas.height.coerceAtLeast(newImage.height) + 2 * margin
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
          get() = goldenCanvas.width + newCanvas.width + diffImage.width

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
              roborazziDebugLog { "Roborazzi can't find the oneDpPx, so fall back to CompareOptions.Format.Simple comparison format" }
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
              val font = getFont(Font.BOLD, fontSize)
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
      val width = maxOf(originalImage.width, comparedImage.width)
      val height = maxOf(originalImage.height, comparedImage.height)
      val diffImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
      for (x in 0 until width) {
        for (y in 0 until height) {
          if (x >= originalImage.width || y >= originalImage.height
            || x >= comparedImage.width || y >= comparedImage.height
          ) {
            diffImage.setRGB(x, y, -0x10000)
            continue
          }
          val rgbOrig = originalImage.getRGB(x, y)
          val rgbComp = comparedImage.getRGB(x, y)
          if (rgbOrig != rgbComp) {
            diffImage.setRGB(x, y, -0x10000)
          }
        }
      }
      return diffImage
    }
  }
}

private fun BufferedImage.scale(scale: Double): BufferedImage {
  if (scale == 1.0) {
    roborazziDebugLog {
      "AwtRoboCanvas.scale scale is 1.0, so return original image without scaling."
    }
    return this
  }
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

internal val preferredFontName: String by lazy {
  getSystemProperty("roborazzi.theme.typography.font.name", "Courier New")
}

internal fun getFont(style: Int, size: Int): Font {
  return if (hasPreferredFont) {
    Font(preferredFontName, style, size)
  } else {
    Font(Font.MONOSPACED, style, size)
  }
}

/**
 * Checks if the preferred font is available in the system.
 * In headless environments or when font configuration is missing, this will return false
 * and the system will fall back to using Font.MONOSPACED.
 *
 * The preferred font can be configured using the system property "roborazzi.theme.typography.font.name"
 * (defaults to "Courier New").
 */
internal val hasPreferredFont: Boolean by lazy {
  try {
    GraphicsEnvironment.getLocalGraphicsEnvironment()
      .availableFontFamilyNames.any { it.equals(preferredFontName, ignoreCase = true) }
  } catch (e: Throwable) {
    // It seems that font error becomes InternalError in some environments so we catch Throwable here
    // https://github.com/takahirom/roborazzi/issues/656#issuecomment-2734506322
    // In headless environments where font configuration is missing, default to false
    roborazziDebugLog {
      "Font configuration is missing or not available in headless environment. Using Font.MONOSPACED as fallback. Error: ${e.message}"
    }
    false
  }
}

private fun <T> BufferedImage.graphics(block: (Graphics2D) -> T): T {
  val graphics = createGraphics()
  graphics.font = getFont(Font.BOLD, 12)
  val result = block(graphics)
  graphics.dispose()
  return result
}
