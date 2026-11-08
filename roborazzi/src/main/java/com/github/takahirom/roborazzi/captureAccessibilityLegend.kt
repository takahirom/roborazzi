package com.github.takahirom.roborazzi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.platform.ViewRootForTest
import kotlin.math.max
import kotlin.math.roundToInt

internal fun captureAccessibilityLegend(
  rootComponent: RoboComponent,
  recordOptions: RoborazziOptions.RecordOptions,
  onCanvas: (AwtRoboCanvas) -> Unit,
) {
  val image = rootComponent.image ?: throw IllegalStateException(
    "Unable to find the image of the target root component. Does the rendering element exist?"
  )
  val entries = collectAccessibilityLegendEntries(rootComponent)
  val legend = renderAccessibilityLegend(
    screenshot = image,
    entries = entries,
    density = rootComponent.density(),
  )
  onCanvas(
    AwtRoboCanvas(
      width = legend.width,
      height = legend.height,
      filled = true,
      bufferedImageType = recordOptions.pixelBitConfig.toBufferedImageType(),
    ).apply {
      drawImage(Rect(0, 0, legend.width, legend.height), legend)
    }
  )
}

private fun RoboComponent.density(): Float {
  val view = when (this) {
    is RoboComponent.View -> androidView
    is RoboComponent.Compose -> (semanticsNode.root as? ViewRootForTest)?.view
    is RoboComponent.Screen -> windows.firstOrNull()?.first
  }
  return view?.resources?.displayMetrics?.density ?: 1f
}

private const val PROBLEM_COLOR = 0xFFE00000.toInt()
private const val CONTAINER_COLOR = 0xFF5F6368.toInt()
private const val DETAILS_COLOR = 0xFF5F6368.toInt()
private val PALETTE = intArrayOf(
  0xFF1E88E5.toInt(),
  0xFF43A047.toInt(),
  0xFF8E24AA.toInt(),
  0xFFFB8C00.toInt(),
  0xFF00897B.toInt(),
  0xFF6D4C41.toInt(),
  0xFF3949AB.toInt(),
  0xFFC0CA33.toInt(),
  0xFFD81B60.toInt(),
  0xFF546E7A.toInt(),
  0xFF00ACC1.toInt(),
  0xFF7CB342.toInt(),
  0xFF5E35B1.toInt(),
  0xFFF4511E.toInt(),
  0xFF039BE5.toInt(),
  0xFFAD1457.toInt(),
)

/**
 * The screenshot with outlined elements, their numbers in a column on the left so they never
 * cover the UI, and the legend on the right.
 */
private fun renderAccessibilityLegend(
  screenshot: Bitmap,
  entries: List<AccessibilityLegendEntry>,
  density: Float,
): Bitmap {
  val elements = entries.filter { it.container == null }
  val containers = entries.filter { it.container != null }
  val pad = (12 * density).roundToInt()
  val swatch = (16 * density).roundToInt()
  val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = 11 * density
    color = Color.WHITE
    isFakeBoldText = true
  }
  val badgeHeight = (badgePaint.textSize * 1.4f).roundToInt()
  val badgeWidth = badgePaint.measureText("${max(elements.size, 1)}").roundToInt() + pad / 2
  val gutter = badgeWidth + pad
  val legendLeft = gutter + screenshot.width
  val legendWidth = screenshot.width
  val numberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = 14 * density
    color = Color.DKGRAY
  }
  val numberColumn = numberPaint.measureText("${elements.size}.").roundToInt() + pad / 2
  val textLeft = legendLeft + pad + swatch + pad + numberColumn
  val textWidth = max(legendLeft + legendWidth - pad - textLeft, pad)

  val colors = elementColors(elements)
  val mainLayouts = elements.map { entry ->
    staticLayout(
      text = entry.line,
      paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14 * density
        color = when {
          entry.problem -> PROBLEM_COLOR
          entry.order == null || entry.offscreen -> Color.GRAY
          else -> Color.BLACK
        }
        isFakeBoldText = entry.problem
      },
      width = textWidth,
    )
  }
  val detailLayouts = elements.map { entry ->
    entry.details.takeIf { it.isNotEmpty() }?.let {
      staticLayout(
        text = it.joinToString("\n"),
        paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          textSize = 12.5f * density
          color = if (entry.problem) PROBLEM_COLOR else DETAILS_COLOR
        },
        width = textWidth,
      )
    }
  }
  val paneLayouts = containers.map {
    staticLayout(
      text = it.container.orEmpty(),
      paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13 * density
        color = CONTAINER_COLOR
      },
      width = textWidth,
    )
  }
  fun entryHeight(index: Int): Int {
    val details = detailLayouts[index]?.let { it.height + pad / 4 } ?: 0
    return max(mainLayouts[index].height + details, swatch)
  }
  val legendHeight = pad + elements.indices.sumOf { entryHeight(it) + pad } +
    (if (paneLayouts.isEmpty()) 0 else pad + paneLayouts.sumOf { max(it.height, swatch) + pad })
  val height = max(screenshot.height, legendHeight)

  val result = Bitmap.createBitmap(legendLeft + legendWidth, height, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(result)
  canvas.drawColor(Color.WHITE)
  canvas.drawBitmap(screenshot, gutter.toFloat(), 0f, null)

  val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = CONTAINER_COLOR
    style = Paint.Style.STROKE
    strokeWidth = 2 * density
    pathEffect = DashPathEffect(floatArrayOf(8 * density, 5 * density), 0f)
  }
  val visible = elements.indices.filter { !elements[it].offscreen }
  val frames = visible.associateWith { Rect(elements[it].bounds).apply { offset(gutter, 0) } }
  canvas.save()
  canvas.clipRect(gutter, 0, gutter + screenshot.width, screenshot.height)
  containers.filter { !it.offscreen }.forEach {
    canvas.drawRect(Rect(it.bounds).apply { offset(gutter, 0) }, dashed)
  }
  visible.forEach { index ->
    val frame = frames.getValue(index)
    val fill = Paint().apply {
      color = colors[index]
      alpha = 0x30
    }
    val stroke = Paint().apply {
      color = colors[index]
      style = Paint.Style.STROKE
      strokeWidth = 2 * density
    }
    canvas.drawRect(frame, fill)
    canvas.drawRect(frame, stroke)
  }
  canvas.restore()

  var nextFreeTop = 0
  visible.forEach { index ->
    val frame = frames.getValue(index)
    val top = max(frame.top.coerceAtLeast(0), nextFreeTop)
    if (top + badgeHeight > height) return@forEach
    nextFreeTop = top + badgeHeight + (2 * density).roundToInt()
    val left = gutter - pad / 2 - badgeWidth
    val badge = Rect(left, top, left + badgeWidth, top + badgeHeight)
    canvas.drawRect(badge, Paint().apply { color = colors[index] })
    val number = "${index + 1}"
    canvas.drawText(
      number,
      left + (badgeWidth - badgePaint.measureText(number)) / 2f,
      top + badgeHeight / 2f - (badgePaint.ascent() + badgePaint.descent()) / 2f,
      badgePaint,
    )
    // A leader line only when no other element lies between the number and its frame.
    val centerY = top + badgeHeight / 2f
    val blocked = frames.any { (other, otherFrame) ->
      other != index && otherFrame.left < frame.left &&
        centerY >= otherFrame.top && centerY <= otherFrame.bottom
    }
    if (centerY >= frame.top && centerY <= frame.bottom && !blocked) {
      val leader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors[index]
        strokeWidth = 1.5f * density
      }
      canvas.drawLine((left + badgeWidth).toFloat(), centerY, frame.left.toFloat(), centerY, leader)
    }
  }

  var y = pad
  elements.indices.forEach { index ->
    val x = legendLeft + pad
    canvas.drawRect(Rect(x, y, x + swatch, y + swatch), Paint().apply { color = colors[index] })
    val baseline = y + mainLayouts[index].getLineBaseline(0)
    canvas.drawText("${index + 1}.", (x + swatch + pad).toFloat(), baseline.toFloat(), numberPaint)
    canvas.save()
    canvas.translate(textLeft.toFloat(), y.toFloat())
    mainLayouts[index].draw(canvas)
    detailLayouts[index]?.let { details ->
      canvas.translate(0f, (mainLayouts[index].height + pad / 4).toFloat())
      details.draw(canvas)
    }
    canvas.restore()
    y += entryHeight(index) + pad
  }
  if (paneLayouts.isNotEmpty()) {
    y += pad
    paneLayouts.forEach { layout ->
      val x = legendLeft + pad
      canvas.drawRect(Rect(x, y, x + swatch, y + swatch), dashed)
      canvas.save()
      canvas.translate(textLeft.toFloat(), y.toFloat())
      layout.draw(canvas)
      canvas.restore()
      y += max(layout.height, swatch) + pad
    }
  }
  return result
}

/**
 * Colors come from a hash of the element's text, so inserting an element doesn't recolor the
 * others; an element whose color is already taken gets the next free one.
 */
private fun elementColors(elements: List<AccessibilityLegendEntry>): List<Int> {
  val usedSlots = mutableSetOf<Int>()
  return elements.map { entry ->
    when {
      entry.problem -> PROBLEM_COLOR
      entry.order == null || entry.offscreen -> Color.LTGRAY
      else -> {
        var slot = Math.floorMod(entry.line.substringBefore(" · ").hashCode(), PALETTE.size)
        repeat(PALETTE.size) { if (slot in usedSlots) slot = (slot + 1) % PALETTE.size }
        usedSlots += slot
        PALETTE[slot]
      }
    }
  }
}

private fun staticLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
  StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()
