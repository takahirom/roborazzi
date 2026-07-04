package com.github.takahirom.roborazzi.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.runtime.LaunchedEffect
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoboAnimationOptions
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboAnimation
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import me.saket.touchrobot.rememberTouchRobot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Prototype: drive a Compose gesture with saket/touch-robot and record it in real time with
 * [captureRoboAnimation]. The gesture runs in a LaunchedEffect while the recording loop pumps
 * virtual time frame by frame.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.NexusOne)
class ComposeTouchRobotAnimationTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun captureTouchRobotSwipe() {
    // captureRoboAnimation records only in record mode and no-ops otherwise, so the gesture that
    // drives the box never progresses in compare/verify mode. Skip the test then instead of
    // asserting on an un-driven gesture (which fails) or leaving it suspended (which hangs).
    assumeTrue(provideRoborazziContext().options.taskType.isRecording())
    // Track how far the draggable box actually moved so we can assert the gesture progressed;
    // otherwise a stalled gesture would still produce a GIF (of identical frames) and pass.
    var latestOffsetX = 0f
    var latestOffsetY = 0f
    composeTestRule.setContent {
      DraggableBoxContent(onOffsetChanged = { x, y ->
        latestOffsetX = x
        latestOffsetY = y
      })
    }
    composeTestRule.onNodeWithTag("root")
      .captureRoboAnimation(
        composeRule = composeTestRule,
        filePath = "${roborazziSystemPropertyOutputDirectory()}/touch_robot_swipe.gif",
        animationOptions = RoboAnimationOptions(fps = 10),
      ) {
        // Pump virtual time so the LaunchedEffect gesture progresses while frames are captured.
        delay(1000)
      }
    // The swipe drags roughly 200dp diagonally, so both axes must have moved substantially.
    assertTrue(
      "Expected the swipe to drag the box substantially, but offset was " +
        "($latestOffsetX, $latestOffsetY)",
      abs(latestOffsetX) > 100f && abs(latestOffsetY) > 100f
    )
  }
}

@Composable
private fun DraggableBoxContent(onOffsetChanged: (Float, Float) -> Unit = { _, _ -> }) {
  val touchRobot = rememberTouchRobot(showTaps = true)
  val density = LocalDensity.current
  var offsetX by remember { mutableStateOf(0f) }
  var offsetY by remember { mutableStateOf(0f) }

  LaunchedEffect(Unit) {
    // Swipe from near the top-left of the box down and to the right. The suspend gesture makes
    // progress because captureRoboAnimation idles the Robolectric main Looper in lockstep with
    // the Compose main clock while recording frames.
    val startPx = with(density) { 40.dp.toPx() }.roundToInt()
    val endPx = with(density) { 240.dp.toPx() }.roundToInt()
    touchRobot.onRoot().performGesture {
      swipe(
        start = IntOffset(startPx, startPx),
        stop = IntOffset(endPx, endPx),
        duration = 500.milliseconds,
      )
    }
  }

  Box(
    Modifier
      .testTag("root")
      .size(300.dp)
      .background(Color.LightGray)
      .pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
          change.consume()
          offsetX += dragAmount.x
          offsetY += dragAmount.y
          onOffsetChanged(offsetX, offsetY)
        }
      }
  ) {
    Box(
      Modifier
        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
        .size(60.dp)
        .background(Color.Blue)
    )
  }
}
