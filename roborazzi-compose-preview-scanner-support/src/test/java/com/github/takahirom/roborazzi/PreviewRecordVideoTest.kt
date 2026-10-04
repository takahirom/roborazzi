package com.github.takahirom.roborazzi

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
import java.io.File

@Composable
private fun FadingBox() {
  var target by remember { mutableStateOf(0f) }
  LaunchedEffect(Unit) { target = 1f }
  val alpha by animateFloatAsState(target)
  Column {
    // Static, so the very first frame already has something that is not the background.
    Box(Modifier.fillMaxWidth().height(50.dp).background(Color.Blue))
    Box(Modifier.fillMaxWidth().height(100.dp).alpha(alpha).background(Color.Red))
  }
}

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewRecordVideoTest {
  @get:Rule
  val composeTestRule = createEmptyComposeRule()

  @get:Rule
  val tmp = TemporaryFolder()

  private val preview = object : ComposablePreview<AndroidPreviewInfo> {
    override val previewInfo = AndroidPreviewInfo(
      device = "", fontScale = 1f, uiMode = Configuration.UI_MODE_NIGHT_NO,
      widthDp = -1, heightDp = -1,
    )
    override val previewIndex: Int? = null
    override val previewIndexDisplayName: String? = null
    override val otherAnnotationsInfo = null
    override val declaringClass = PreviewRecordVideoTest::class.java.name
    override val methodName = "FadingBox"
    override val methodParametersType = ""

    @Composable override fun invoke() = FadingBox()
  }

  @Test
  fun recordsMultipleFramesAndRestoresQualifiers() {
    val before = RuntimeEnvironment.getQualifiers()
    val file = File(tmp.root, "preview.gif")
    val composeOptions = RoborazziComposeOptions()
    preview.recordRoboVideo(
      filePath = file.path,
      roborazziOptions = provideRoborazziContext().options.copy(
        taskType = RoborazziTaskType.Record
      ),
      roborazziComposeOptions = composeOptions,
      composeRule = composeTestRule,
    )
    assertTrue("video should be written", file.exists())
    // Each GIF frame starts with a Graphic Control Extension block (0x21 0xF9 0x04).
    val bytes = file.readBytes()
    val frames = (0 until bytes.size - 2).count {
      bytes[it] == 0x21.toByte() && bytes[it + 1] == 0xF9.toByte() && bytes[it + 2] == 0x04.toByte()
    }
    assertTrue("expected an animation, got $frames frame(s)", frames > 1)
    assertEquals(before, RuntimeEnvironment.getQualifiers())
  }

  @Test
  fun firstFrameContainsThePreview() {
    val file = File(tmp.root, "first.gif")
    preview.recordRoboVideo(
      filePath = file.path,
      videoOptions = RoboVideoOptions(settleTimeoutMillis = 0),
      roborazziOptions = provideRoborazziContext().options.copy(
        taskType = RoborazziTaskType.Record
      ),
      roborazziComposeOptions = RoborazziComposeOptions(),
      composeRule = composeTestRule,
    )
    // BitmapFactory decodes the first frame of a GIF.
    val first = BitmapFactory.decodeFile(file.path)
    assertNotEquals(
      "first frame should already show the preview, not the empty background",
      RoboVideoOptions().backgroundColor,
      first.getPixel(5, 5),
    )
  }
}
