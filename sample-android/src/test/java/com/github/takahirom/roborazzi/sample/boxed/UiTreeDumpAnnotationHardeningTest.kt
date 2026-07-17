package com.github.takahirom.roborazzi.sample.boxed

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.UiTreeDumpOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyCompareOutputDirectory
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Regression coverage for the UI tree dump annotated-image hardening:
 *  - a FAILING verify still writes the annotated image for the fresh `_actual`
 *    (the assertion is thrown from the report step AFTER `_actual` is written);
 *  - an UNCHANGED verify annotates the golden, never a stale `_actual` left by an
 *    earlier run; and
 *  - the annotated image is always a real PNG, even when `recordOptions` uses a
 *    non-PNG image format such as lossless WebP.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel4, sdk = [35])
class UiTreeDumpAnnotationHardeningTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val pngMagic = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
  )

  private fun startsWithPngMagic(file: File): Boolean {
    val head = ByteArray(pngMagic.size)
    file.inputStream().use { it.read(head) }
    return head.contentEquals(pngMagic)
  }

  @Test
  fun failingVerifyStillWritesAnnotatedImageForActual() {
    boxedEnvironment {
      val prefix =
        "${roborazziSystemPropertyOutputDirectory()}/${this::class.qualifiedName}.failingVerify"
      val goldenImage = File("$prefix.png")
      val actualImage = File(
        "${roborazziSystemPropertyCompareOutputDirectory()}/${this::class.qualifiedName}.failingVerify_actual.png"
      )
      val actualAnnotated = File(
        "${roborazziSystemPropertyCompareOutputDirectory()}/${this::class.qualifiedName}.failingVerify_actual.annotated.png"
      )
      listOf(goldenImage, actualImage, actualAnnotated).forEach { it.delete() }

      val options = { taskType: RoborazziTaskType ->
        RoborazziOptions(taskType = taskType, uiTreeDumpOptions = UiTreeDumpOptions())
      }

      var label by mutableStateOf("Login")
      composeTestRule.setContent {
        Text(text = label, modifier = Modifier.testTag("btn").size(120.dp))
      }

      // Record a golden showing "Login".
      setupRoborazziSystemProperty(record = true)
      onView(isRoot()).captureRoboImage(
        file = goldenImage,
        roborazziOptions = options(RoborazziTaskType.Record),
      )
      assertTrue("golden should exist", goldenImage.exists())

      // Change the content so the verify fails, then verify: it must throw, and the
      // freshly written `_actual` must still get its `.annotated.png`.
      label = "Logout now!"
      composeTestRule.waitForIdle()
      setupRoborazziSystemProperty(verify = true)

      var threw = false
      try {
        onView(isRoot()).captureRoboImage(
          file = goldenImage,
          roborazziOptions = options(RoborazziTaskType.Verify),
        )
      } catch (e: AssertionError) {
        threw = true
      }
      assertTrue("a changed verify must throw AssertionError", threw)
      assertTrue("changed verify must write the _actual image", actualImage.exists())
      assertTrue(
        "a failing verify must still write the _actual annotated image",
        actualAnnotated.exists(),
      )

      listOf(goldenImage, actualImage, actualAnnotated).forEach { it.delete() }
    }
  }

  @Test
  fun unchangedVerifyAnnotatesGoldenNotStaleActual() {
    boxedEnvironment {
      val prefix =
        "${roborazziSystemPropertyOutputDirectory()}/${this::class.qualifiedName}.unchangedVerify"
      val goldenImage = File("$prefix.png")
      val actualImage = File(
        "${roborazziSystemPropertyCompareOutputDirectory()}/${this::class.qualifiedName}.unchangedVerify_actual.png"
      )
      val actualAnnotated = File(
        "${roborazziSystemPropertyCompareOutputDirectory()}/${this::class.qualifiedName}.unchangedVerify_actual.annotated.png"
      )
      listOf(goldenImage, actualImage, actualAnnotated).forEach { it.delete() }

      val options = { taskType: RoborazziTaskType ->
        RoborazziOptions(taskType = taskType, uiTreeDumpOptions = UiTreeDumpOptions())
      }

      composeTestRule.setContent {
        Text(text = "Login", modifier = Modifier.testTag("btn").size(120.dp))
      }
      setupRoborazziSystemProperty(record = true)
      onView(isRoot()).captureRoboImage(
        file = goldenImage,
        roborazziOptions = options(RoborazziTaskType.Record),
      )
      val goldenWidth = BitmapFactory.decodeFile(goldenImage.absolutePath).width
      assertTrue("golden should be wider than the stale marker", goldenWidth > 10)

      // Pre-seed a STALE _actual image from a hypothetical earlier changed run: a
      // tiny 10x10 PNG whose size is unmistakably different from the golden.
      actualImage.parentFile?.mkdirs()
      val stale = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
      FileOutputStream(actualImage).use { stale.compress(Bitmap.CompressFormat.PNG, 100, it) }

      // Verify the unchanged screenshot: no new _actual is written this run, so the
      // annotated image must be drawn on the golden, NOT the stale 10x10 _actual.
      setupRoborazziSystemProperty(verify = true)
      onView(isRoot()).captureRoboImage(
        file = goldenImage,
        roborazziOptions = options(RoborazziTaskType.Verify),
      )

      assertTrue("annotated image should be written", actualAnnotated.exists())
      val annotatedWidth = BitmapFactory.decodeFile(actualAnnotated.absolutePath).width
      assertNotEquals(
        "annotated image must not be based on the stale 10x10 _actual",
        10,
        annotatedWidth,
      )
      assertEquals(
        "annotated image must be based on the golden image",
        goldenWidth,
        annotatedWidth,
      )

      listOf(goldenImage, actualImage, actualAnnotated).forEach { it.delete() }
    }
  }

  @Test
  fun annotatedImageIsPngEvenWithLosslessWebpFormat() {
    boxedEnvironment {
      val prefix =
        "${roborazziSystemPropertyOutputDirectory()}/${this::class.qualifiedName}.webpAnnotated"
      val goldenImage = File("$prefix.png")
      val goldenAnnotated = File("$prefix.annotated.png")
      listOf(goldenImage, goldenAnnotated).forEach { it.delete() }

      composeTestRule.setContent {
        Text(text = "Login", modifier = Modifier.testTag("btn").size(120.dp))
      }
      setupRoborazziSystemProperty(record = true)
      onView(isRoot()).captureRoboImage(
        file = goldenImage,
        roborazziOptions = RoborazziOptions(
          taskType = RoborazziTaskType.Record,
          recordOptions = RoborazziOptions.RecordOptions(
            imageIoFormat = LosslessWebPImageIoFormat(),
          ),
          uiTreeDumpOptions = UiTreeDumpOptions(),
        ),
      )

      assertTrue("annotated image should be written", goldenAnnotated.exists())
      if (!startsWithPngMagic(goldenAnnotated)) {
        fail("annotated image must be a real PNG regardless of recordOptions.imageIoFormat")
      }

      listOf(goldenImage, goldenAnnotated).forEach { it.delete() }
    }
  }
}
