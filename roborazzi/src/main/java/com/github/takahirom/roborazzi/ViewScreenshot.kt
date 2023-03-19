package com.github.takahirom.roborazzi

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.Window
import androidx.concurrent.futures.ResolvableFuture

fun View.fetchImage(): Bitmap? {
  if (this.width <= 0 || this.height <= 0) return null
  val bitmapFuture = ResolvableFuture.create<Bitmap>()
  generateBitmap(bitmapFuture)
  val bitmap = bitmapFuture.get()
  return bitmap
}

// From AOSP: https://cs.android.com/androidx/android-test/+/master:core/java/androidx/test/core/view/WindowCapture.kt;drc=25e2f2b042b283eea3b7ced82fb3c6504b6cca63
private fun View.generateBitmap(bitmapFuture: ResolvableFuture<Bitmap>) {
  if (bitmapFuture.isCancelled) {
    return
  }
  val destBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  when {
    Build.VERSION.SDK_INT < 26 -> generateBitmapFromDraw(destBitmap, bitmapFuture)
    this is SurfaceView -> generateBitmapFromSurfaceViewPixelCopy(destBitmap, bitmapFuture)
    else -> {
      val window = getActivity()?.window
      if (window != null) {
        generateBitmapFromPixelCopy(window, destBitmap, bitmapFuture)
      } else {
        Log.i(
          "View.captureToImage",
          "Could not find window for view. Falling back to View#draw instead of PixelCopy"
        )
        generateBitmapFromDraw(destBitmap, bitmapFuture)
      }
    }
  }
}

@SuppressWarnings("NewApi")
private fun SurfaceView.generateBitmapFromSurfaceViewPixelCopy(
  destBitmap: Bitmap,
  bitmapFuture: ResolvableFuture<Bitmap>
) {
  val onCopyFinished =
    PixelCopy.OnPixelCopyFinishedListener { result ->
      if (result == PixelCopy.SUCCESS) {
        bitmapFuture.set(destBitmap)
      } else {
        bitmapFuture.setException(RuntimeException(String.format("PixelCopy failed: %d", result)))
      }
    }
  PixelCopy.request(this, null, destBitmap, onCopyFinished, handler)
}

internal fun View.generateBitmapFromDraw(
  destBitmap: Bitmap,
  bitmapFuture: ResolvableFuture<Bitmap>
) {
  destBitmap.density = resources.displayMetrics.densityDpi
  computeScroll()
  val canvas = Canvas(destBitmap)
  canvas.translate((-scrollX).toFloat(), (-scrollY).toFloat())
  draw(canvas)
  bitmapFuture.set(destBitmap)
}

private fun View.getActivity(): Activity? {
  fun Context.getActivity(): Activity? {
    return when (this) {
      is Activity -> this
      is ContextWrapper -> this.baseContext.getActivity()
      else -> null
    }
  }
  return context.getActivity()
}

private fun View.generateBitmapFromPixelCopy(
  window: Window,
  destBitmap: Bitmap,
  bitmapFuture: ResolvableFuture<Bitmap>
) {
  val locationInWindow = intArrayOf(0, 0)
  getLocationInWindow(locationInWindow)
  val x = locationInWindow[0]
  val y = locationInWindow[1]
  val boundsInWindow = Rect(x, y, x + width, y + height)

  return window.generateBitmapFromPixelCopy(boundsInWindow, destBitmap, bitmapFuture)
}

@SuppressWarnings("NewApi")
internal fun Window.generateBitmapFromPixelCopy(
  boundsInWindow: Rect? = null,
  destBitmap: Bitmap,
  bitmapFuture: ResolvableFuture<Bitmap>
) {
  val onCopyFinished =
    PixelCopy.OnPixelCopyFinishedListener { result ->
      if (result == PixelCopy.SUCCESS) {
        bitmapFuture.set(destBitmap)
      } else {
        bitmapFuture.setException(RuntimeException(String.format("PixelCopy failed: %d", result)))
      }
    }
  PixelCopy.request(
    this,
    boundsInWindow,
    destBitmap,
    onCopyFinished,
    Handler(Looper.getMainLooper())
  )
}
