# Capture GIFs and videos

Roborazzi can record test interactions as animated images: `captureRoboGif()`
captures visually distinct states as a GIF, and `recordRoboVideo()` records
animations frame by frame over virtual time.

## Generate gif image

```kotlin
@Test
fun captureRoboGifSample() {
  onView(ViewMatchers.isRoot())
    .captureRoboGif("build/test.gif") {
      // launch
      ActivityScenario.launch(MainActivity::class.java)
      // move to next page
      onView(withId(R.id.button_first))
        .perform(click())
      // back
      pressBack()
      // move to next page
      onView(withId(R.id.button_first))
        .perform(click())
    }
}
```

<img alt="GIF of the test interactions captured by captureRoboGif" width="350" src="https://user-images.githubusercontent.com/1386930/226362212-35d34c9e-6df1-4671-8949-10fad7ad98c9.gif" />

## Generate gif with test rule

> **Note**  
> You **don't need to use RoborazziRule** if you're using captureRoboImage().

With the JUnit test rule, you do not need to name the gif image,
and if you prefer, you can output the gif image **only if the test fails**.

This test will output this file.

`build/outputs/roborazzi/com.github.takahirom.roborazzi.sample.RuleTestWithOnlyFail_captureRoboGifSampleFail.gif`

```kotlin
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RuleTestWithOnlyFail {
  @get:Rule
  val roborazziRule = RoborazziRule(
    captureRoot = onView(isRoot()),
    options = Options(
      onlyFail = true,
      captureType = RoborazziRule.CaptureType.Gif,
    )
  )

  @Test
  fun captureRoboLastImageSampleFail() {
    // launch
    ActivityScenario.launch(MainActivity::class.java)
    // move to next page
    onView(withId(R.id.button_first))
      .perform(click())
    // should fail because the button does not exist
    // Due to failure, the gif image will be saved in the outputs folder.
    onView(withId(R.id.button_first))
      .perform(click())
  }
}
```

## Generate Jetpack Compose gif with test rule

Test target

```kotlin
@Composable
fun SampleComposableFunction() {
  var count by remember { mutableStateOf(0) }
  Column(
    Modifier
      .size(300.dp)
  ) {
    Box(
      Modifier
        .testTag("AddBoxButton")
        .size(50.dp)
        .clickable {
          count++
        }
    )
    (0..count).forEach {
      Box(
        Modifier
          .size(30.dp)
      )
    }
  }
}
```

Test (Just add RoborazziRule)

```kotlin
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @get:Rule
  val roborazziRule = RoborazziRule(
    composeRule = composeTestRule,
    captureRoot = composeTestRule.onRoot(),
    options = RoborazziRule.Options(
      RoborazziRule.CaptureType.Gif()
    )
  )

  @Test
  fun composable() {
    composeTestRule.setContent {
      SampleComposableFunction()
    }
    (0 until 3).forEach { _ ->
      composeTestRule
        .onNodeWithTag("AddBoxButton")
        .performClick()
    }
  }
}
```

![com github takahirom roborazzi sample ComposeTest_composable](https://user-images.githubusercontent.com/1386930/226366224-b9950b60-26a2-489e-bc03-08bfb86c533a.gif)

## Record a video of the UI (experimental)

`recordRoboVideo()` records how the UI evolves over virtual time -- animations, transitions,
gestures and any other time-driven change -- as an animated image that plays back in real time.
Unlike `captureRoboGif()`, which only records visually distinct states with a fixed 1-second
delay, it pauses the Compose main clock and drives it frame by frame, so intermediate animation
frames are captured with faithful timing. Interactions in the block run with the clock paused;
call `delay()` in the scope to advance virtual time while frames are recorded. After the block
returns, recording continues until the UI settles, so a block that only performs a click still
records the whole animation the click starts.

```kotlin
@OptIn(ExperimentalRoborazziApi::class)
@Test
fun recordVideo() {
  composeTestRule.setContent {
    AnimatedBoxContent()
  }
  composeTestRule.onNodeWithTag("root")
    .recordRoboVideo(
      composeRule = composeTestRule,
      filePath = "build/outputs/roborazzi/video.gif",
      videoOptions = RoboVideoOptions(fps = 10),
    ) {
      composeTestRule.onNodeWithTag("toggle").performClick()
      delay(300)
      composeTestRule.onNodeWithTag("toggle").performClick()
      delay(300)
    }
}
```

- `RoboVideoOptions`: `fps` (default `10`), `settleTimeoutMillis` (additional virtual time to
  keep recording while the UI is still changing after the block ends; default `3000`), and
  `backgroundColor` (ARGB fill for the fixed recording viewport when frames have different
  sizes; default white).
- **Output format is chosen by the file extension:** `.gif` produces a GIF (256 colors; the
  default), `.png` a lossless, full-color APNG (Animated PNG) -- prefer `.png` when color
  fidelity matters. Only these animated-image formats are supported for now; the "video" name
  was chosen deliberately so real video formats (e.g. mp4) can be added later without renaming
  the API.
- GIF stores frame delays in centiseconds (10ms resolution), so prefer an `fps` whose frame step
  (`1000 / fps`) is a multiple of 10ms (e.g. 10, 20, 25, 50) for exact GIF timing. APNG encodes
  the step exactly.
- The API is experimental (`@ExperimentalRoborazziApi`) and currently only supports recording:
  when the Roborazzi task runs in compare/verify mode it is a complete no-op -- the block is not
  executed and no image is recorded or verified.

`recordScreenRoboVideo()` records all window roots instead of a single node, mirroring how
`captureScreenRoboImage()` relates to `captureRoboImage()`. Prefer it when you want a stable,
device-sized viewport for every frame, or to capture window overlays such as dialogs added
mid-recording or touch/tap indicators, which live on separate window roots and are invisible to a
node-scoped recording. Usage is the same, just without node scoping:

```kotlin
recordScreenRoboVideo(
  composeRule = composeTestRule,
  filePath = "build/outputs/roborazzi/video_screen.gif",
  videoOptions = RoboVideoOptions(fps = 10),
) {
  composeTestRule.onNodeWithTag("toggle").performClick()
  delay(300)
}
```

Because the recorder idles the Robolectric main Looper in lockstep with the Compose main clock,
suspend-based gesture drivers make progress while frames are recorded. For example, you can drive
a swipe with [saket/touch-robot](https://github.com/saket/touch-robot) from a `LaunchedEffect`,
use `recordScreenRoboVideo()` so the touch indicator overlay is captured, and just pump virtual
time in the block:

```kotlin
composeTestRule.setContent {
  // A composable whose LaunchedEffect drives a touch-robot swipe on the root.
  DraggableBoxContent()
}
recordScreenRoboVideo(
  composeRule = composeTestRule,
  filePath = "build/outputs/roborazzi/swipe.gif",
) {
  // Pump virtual time so the LaunchedEffect gesture progresses while frames are captured.
  delay(1000)
}
```
