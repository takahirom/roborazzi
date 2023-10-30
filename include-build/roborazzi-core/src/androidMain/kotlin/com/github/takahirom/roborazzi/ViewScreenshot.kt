package com.github.takahirom.roborazzi

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.Window
import androidx.concurrent.futures.ResolvableFuture
import androidx.core.graphics.withClip
import org.robolectric.versioning.AndroidVersions
import kotlin.math.min

fun View.fetchImage(recordOptions: RoborazziOptions.RecordOptions): Bitmap? {
  if (this.width <= 0 || this.height <= 0) return null
  val bitmapFuture = ResolvableFuture.create<Bitmap>()
  generateBitmap(bitmapFuture, recordOptions.pixelBitConfig)
  val bitmap = bitmapFuture.get()

  return if (recordOptions.applyDeviceCrop) {
    bitmap?.applyDeviceCrop(resources.configuration, recordOptions.pixelBitConfig)
  } else {
    bitmap
  }
}

internal fun Bitmap.applyDeviceCrop(
  configuration: Configuration,
  pixelBitConfig: RoborazziOptions.PixelBitConfig
): Bitmap {
  val isRoundCrop =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && configuration.isScreenRound

  return if (isRoundCrop) {
    cropRound(pixelBitConfig)
  } else {
    this
  }
}

internal fun Bitmap.cropRound(pixelBitConfig: RoborazziOptions.PixelBitConfig): Bitmap {
  val newBitmap = Bitmap.createBitmap(this.width, this.height, pixelBitConfig.toBitmapConfig())

  val canvas = Canvas(newBitmap)
  val paint = Paint().apply {
    isAntiAlias = true
  }
  canvas.drawColor(Color.TRANSPARENT)

  val width = canvas.width.toFloat()
  val height = canvas.height.toFloat()
  val path = Path().apply {
    addCircle(
      width / 2,
      height / 2,
      min(width / 2, height / 2),
      Path.Direction.CCW
    )
  }

  canvas.withClip(path) {
    drawBitmap(this@cropRound, 0f, 0f, paint)
  }

  return newBitmap
}

// From AOSP: https://cs.android.com/androidx/android-test/+/master:core/java/androidx/test/core/view/WindowCapture.kt;drc=25e2f2b042b283eea3b7ced82fb3c6504b6cca63
private fun View.generateBitmap(
  bitmapFuture: ResolvableFuture<Bitmap>,
  pixelBitConfig: RoborazziOptions.PixelBitConfig
) {
  if (bitmapFuture.isCancelled) {
    return
  }
  val destBitmap = Bitmap.createBitmap(width, height, pixelBitConfig.toBitmapConfig())
  if (AndroidVersions.U.RELEASED && Build.VERSION.SDK_INT <= 33) {
    // It seems that Robolectric 4.11 does not support PixelCopy when using API 33 or lower.
    debugLog {
      "PixelCopy is not supported for API levels below 34. Falling back to View#draw instead of PixelCopy. " +
        "Consider using API level 34 or higher, e.g., @Config(sdk = [34])."
    }
    generateBitmapFromDraw(destBitmap, bitmapFuture)
    return
  }
  when {
    Build.VERSION.SDK_INT < 26 -> {
      debugLog {
        "**Warning from Roborazzi**: Robolectric may not function properly under API 26, " +
          "specifically it may fail to capture accurate screenshots. " +
          "Please add @Config(sdk = [26]) or higher to your test class to ensure proper operation. " +
          "For more details, please refer to https://github.com/takahirom/roborazzi/issues/114 ."
      }
      generateBitmapFromDraw(destBitmap, bitmapFuture)
    }

    this is SurfaceView -> generateBitmapFromSurfaceViewPixelCopy(destBitmap, bitmapFuture)
    else -> {
      val window = getActivity()?.window
      if (window != null) {
        if (Build.VERSION.SDK_INT < 28) {
          // See: https://github.com/robolectric/robolectric/blob/robolectric-4.10.3/shadows/framework/src/main/java/org/robolectric/shadows/ShadowPixelCopy.java#L32
          debugLog {
            "PixelCopy is not supported for API levels below 28. Falling back to View#draw instead of PixelCopy. " +
              "Consider using API level 28 or higher, e.g., @Config(sdk = [28])."
          }
          generateBitmapFromDraw(destBitmap, bitmapFuture)
        } else {
          generateBitmapFromPixelCopy(window, destBitmap, bitmapFuture)
        }
      } else {
        debugLog {
          "View.captureToImage Could not find window for view. Falling back to View#draw instead of PixelCopy"
        }
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
