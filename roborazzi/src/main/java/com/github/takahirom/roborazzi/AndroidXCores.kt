package com.github.takahirom.roborazzi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.view.View
import androidx.core.view.ViewCompat

// From AOSP
public fun View.drawToBitmap(config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
  if (!ViewCompat.isLaidOut(this)) {
    throw IllegalStateException("View needs to be laid out before calling drawToBitmap()")
  }
  return Bitmap.createBitmap(width, height, config).applyCanvas {
    translate(-scrollX.toFloat(), -scrollY.toFloat())
    draw(this)
  }
}

public inline fun Bitmap.applyCanvas(block: Canvas.() -> Unit): Bitmap {
  val c = Canvas(this)
  c.block()
  return this
}

public inline fun Canvas.withClip(
  clipPath: Path,
  block: Canvas.() -> Unit
) {
  val checkpoint = save()
  clipPath(clipPath)
  try {
    block()
  } finally {
    restoreToCount(checkpoint)
  }
}