package com.github.takahirom.roborazzi

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.view.PixelCopy
import android.view.View
import android.view.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/** Captures own their temporary pixels; caller-supplied Bitmaps remain usable afterward. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(manifest = Config.NONE, sdk = [35])
class BitmapOwnershipTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val options = RoborazziOptions(taskType = RoborazziTaskType.Record)

  @Test
  fun `screenshot releases its root bitmap before handing off copied pixels`() {
    val color = Color.argb(127, 255, 0, 0)
    val component = RoboComponent.View(solidView(color), options)
    val bitmap = requireNotNull(component.image)
    lateinit var retainedCanvas: AwtRoboCanvas

    capture(component, options) { canvas ->
      assertTrue(bitmap.isRecycled)
      retainedCanvas = canvas
    }

    try {
      assertEquals(color, retainedCanvas.getPixel(3, 4))
    } finally {
      retainedCanvas.release()
    }
  }

  @Test
  fun `dump retains bitmap pixels until deferred canvas drawing finishes`() {
    val dumpOptions = options.copy(captureType = RoborazziOptions.CaptureType.Dump(takeScreenShot = true))
    val component = RoboComponent.View(solidView(), dumpOptions)
    val bitmap = requireNotNull(component.image)
    lateinit var retainedCanvas: AwtRoboCanvas

    capture(component, dumpOptions) { retainedCanvas = it }

    try {
      assertFalse(bitmap.isRecycled)
      assertTrue(retainedCanvas.croppedWidth > 0)
      assertFalse(bitmap.isRecycled)
    } finally {
      retainedCanvas.release()
      bitmap.recycle()
    }
  }

  @Test
  fun `bitmap capture preserves caller ownership and pixels`() {
    val bitmap = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.RED)
    try {
      bitmap.captureRoboImage(temporaryFolder.newFile("capture.png"), options)

      assertFalse(bitmap.isRecycled)
      assertEquals(Color.RED, bitmap.getPixel(3, 4))
      bitmap.eraseColor(Color.BLUE)
      assertEquals(Color.BLUE, bitmap.getPixel(3, 4))
    } finally {
      bitmap.recycle()
    }
  }

  @Test
  @Config(shadows = [CapturingPixelCopy::class])
  fun `screen releases window temporaries and retains its composited image`() {
    CapturingPixelCopy.captured.clear()
    Robolectric.buildActivity(Activity::class.java).setup().visible().use { controller ->
      controller.get().window.decorView.layout(0, 0, 16, 12)
      val screen = RoboComponent.Screen(fetchRobolectricWindowRoots(), options)
      val image = requireNotNull(screen.image)
      try {
        assertTrue(CapturingPixelCopy.captured.isNotEmpty())
        assertTrue(CapturingPixelCopy.captured.all { it.isRecycled })
        assertFalse(image.isRecycled)
        assertEquals(Color.RED, image.getPixel(3, 4))
      } finally {
        image.recycle()
        CapturingPixelCopy.captured.clear()
      }
    }
  }

  @Test
  @Config(shadows = [CapturingPixelCopy::class])
  fun `screen releases a window bitmap when compositing rejects it`() {
    CapturingPixelCopy.captured.clear()
    CapturingPixelCopy.unpremultiplied = true
    try {
      Robolectric.buildActivity(Activity::class.java).setup().visible().use { controller ->
        controller.get().window.decorView.layout(0, 0, 16, 12)
        try {
          RoboComponent.Screen(fetchRobolectricWindowRoots(), options)
          fail("Drawing an unpremultiplied ARGB bitmap must fail")
        } catch (failure: RuntimeException) {
          assertTrue(failure.message.orEmpty().contains("non-premultiplied"))
        }
        assertTrue(CapturingPixelCopy.captured.isNotEmpty())
        assertTrue(CapturingPixelCopy.captured.all { it.isRecycled })
      }
    } finally {
      CapturingPixelCopy.unpremultiplied = false
      CapturingPixelCopy.captured.clear()
    }
  }

  private fun solidView(color: Int = Color.RED): View {
    return object : View(RuntimeEnvironment.getApplication()) {
      override fun draw(canvas: Canvas) {
        canvas.drawColor(color)
      }
    }.apply { layout(0, 0, 16, 12) }
  }

  /** Exposes only the Bitmaps passed to PixelCopy so the ownership boundary is observable. */
  @Implements(PixelCopy::class, minSdk = 26)
  class CapturingPixelCopy {
    companion object {
      val captured = mutableListOf<Bitmap>()
      var unpremultiplied = false

      @JvmStatic
      @Implementation
      fun request(
        window: Window,
        source: android.graphics.Rect?,
        destination: Bitmap,
        listener: PixelCopy.OnPixelCopyFinishedListener,
        handler: Handler,
      ) {
        captured += destination
        destination.eraseColor(Color.RED)
        if (unpremultiplied) {
          destination.eraseColor(0x80FF0000.toInt())
          destination.setPremultiplied(false)
        }
        listener.onPixelCopyFinished(PixelCopy.SUCCESS)
      }
    }
  }
}
