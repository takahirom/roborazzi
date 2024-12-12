# How to use

### Take a screenshot manually

You can take a screenshot by calling captureRoboImage().

app/src/test/java/../ManualTest.kt

```kotlin
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

// All you need to do is use the captureRoboImage function in the test!
import com.github.takahirom.roborazzi.captureRoboImage


// Tips: You can use Robolectric while using AndroidJUnit4
@RunWith(AndroidJUnit4::class)
// Enable Robolectric Native Graphics (RNG) 
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ManualTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun captureRoboImageSample() {
    // Tips: You can use Robolectric with Espresso API
    // launch
    ActivityScenario.launch(MainActivity::class.java)

    // Capture screen
    onView(ViewMatchers.isRoot())
      // If you don't specify a screenshot file name, Roborazzi will automatically use the method name as the file name for you.
      // The format of the file name will be as follows:
      // build/outputs/roborazzi/com_..._ManualTest_captureRoboImageSample.png
      .captureRoboImage()

    // Capture Jetpack Compose Node
    composeTestRule.onNodeWithTag("AddBoxButton")
      .onParent()
      .captureRoboImage("build/compose.png")
  }
}
```

Roborazzi supports the following APIs.

<table>
<tr><td>Capture</td><td>Code</td></tr>
<tr><td>
✅ Jetpack Compose's onNode()
</td><td>

```kotlin
composeTestRule.onNodeWithTag("AddBoxButton")
  .captureRoboImage()
```

</td></tr>
<tr><td>
✅ Espresso's onView()
</td><td>

```kotlin
onView(ViewMatchers.isRoot())
  .captureRoboImage()
```

```kotlin
onView(withId(R.id.button_first))
  .captureRoboImage()
```

</td></tr>
<tr><td>
✅ View
</td><td>

```kotlin
val view: View = composeTestRule.activity.findViewById<View>(R.id.button_second)
view.captureRoboImage()
```

</td></tr>

<tr><td>
✅ Jetpack Compose lambda

</td><td>

```kotlin
captureRoboImage() {
  Text("Hello Compose!")
}
```

</td></tr>

<tr><td>
Experimental🧪<br/> 
✅ Captures the entire screen, including dialogs
</td><td>

```kotlin
captureScreenRoboImage()
```

</td></tr>

<tr><td>
✅ Bitmap

</td><td>

```kotlin
val bitmap: Bitmap = createBitmap(100, 100, Bitmap.Config.ARGB_8888)
  .apply {
    applyCanvas {
      drawColor(android.graphics.Color.YELLOW)
    }
  }
bitmap.captureRoboImage()
```

</td></tr>

</table>

### Device configuration

You can configure the device by using the `@Config` annotation and `RobolectricDeviceQualifiers`.

<table>
<tr><td>Configuration</td><td>Code</td></tr>
<tr><td>
✅ Predefined device configuration
</td><td>

You can change the device configuration by adding `@Config` to the class or method.

```kotlin
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class RoborazziTest {
```

```kotlin
@Test
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
fun test() {
```

</td></tr>
<tr><td>
✅ Night mode
</td><td>

```kotlin
@Config(qualifiers = "+night")
```

</td></tr>
<tr><td>
✅ Locale
</td><td>

```kotlin
@Config(qualifiers = "+ja")
```

</td></tr>
<tr><td>
✅ Screen size
</td><td>

```kotlin
@Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet)
```

</td></tr>

</table>

### Integrate to your GitHub Actions

It is easy to integrate Roborazzi to your GitHub Actions.

#### Add a job to store screenshots

```yaml
name: store screenshots

on:
  push

env:
  GRADLE_OPTS: "-Dorg.gradle.jvmargs=-Xmx6g -Dorg.gradle.daemon=false -Dkotlin.incremental=false"

jobs:
  test:
    runs-on: macos-latest

    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3.9.0
        with:
          distribution: 'zulu'
          java-version: 19

      - name: Gradle cache
        uses: gradle/gradle-build-action@v2

      - name: test
        run: |
          # Create screenshots
          ./gradlew app:recordRoborazziDebug --stacktrace

      # Upload screenshots to GitHub Actions Artifacts
      - uses: actions/upload-artifact@v3
        with:
          name: screenshots
          path: app/build/outputs/roborazzi
          retention-days: 30
```

#### Add a job to verify screenshots

```yaml
name: verify test

on:
  push

env:
  GRADLE_OPTS: "-Dorg.gradle.jvmargs=-Xmx6g -Dorg.gradle.daemon=false -Dkotlin.incremental=false"

jobs:
  test:
    runs-on: macos-latest

    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3.9.0
        with:
          distribution: 'zulu'
          java-version: 19

      - name: Gradle cache
        uses: gradle/gradle-build-action@v2

      # Download screenshots from main branch
      - uses: dawidd6/action-download-artifact@v2
        with:
          name: screenshots
          path: app/build/outputs/roborazzi
          workflow: test.yaml
          branch: main

      - name: verify test
        id: verify-test
        run: |
          # If there is a difference between the screenshots, the test will fail.
          ./gradlew app:verifyRoborazziDebug --stacktrace

      - uses: actions/upload-artifact@v3
        if: ${{ always() }}
        with:
          name: screenshot-diff
          path: app/build/outputs/roborazzi
          retention-days: 30

      - uses: actions/upload-artifact@v3
        if: ${{ always() }}
        with:
          name: screenshot-diff-reports
          path: app/build/reports
          retention-days: 30

      - uses: actions/upload-artifact@v3
        if: ${{ always() }}
        with:
          name: screenshot-diff-test-results
          path: app/build/test-results
          retention-days: 30

```

#### Advanced workflow Sample: Compare Snapshot Results on Pull Requests

For those who are looking for a more advanced example, we have prepared a sample repository that
demonstrates how to use Roborazzi to compare snapshot results on GitHub pull requests. This sample
showcases the integration of Roborazzi with GitHub Actions workflows, making it easy to visualize
and review the differences between snapshots directly in the pull request comments.

Check out
the [roborazzi-compare-on-github-comment-sample](https://github.com/takahirom/roborazzi-compare-on-github-comment-sample)
repository to see this powerful feature in action and learn how to implement it in your own
projects.

Example of the comment

<img src="https://user-images.githubusercontent.com/1386930/236480693-80483cde-53fe-4c04-ba1f-2352e14b5f15.png" width="600" />

## RoborazziRule (Optional)

RoborazziRule is a JUnit rule for Roborazzi.
RoborazziRule is **optional**. You can use `captureRoboImage()` without this rule.

RoborazziRule has two features.

1. Provide context such as `RoborazziOptions` and `outputDirectoryPath` etc for `captureRoboImage()`.
2. Capture screenshots for each test when specifying RoborazziRule.options.captureType.

For example, The following code generates an output file
named `**custom_outputDirectoryPath**/**custom_outputFileProvider**-com.github.takahirom.roborazzi.sample.RuleTestWithPath.captureRoboImage.png` :

```kotlin
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RuleTestWithPath {
  @get:Rule
  val roborazziRule = RoborazziRule(
    options = Options(
      outputDirectoryPath = "$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/custom_outputDirectoryPath",
      outputFileProvider = { description, outputDirectory, fileExtension ->
        File(
          outputDirectory,
          "custom_outputFileProvider-${description.testClass.name}.${description.methodName}.$fileExtension"
        )
      }
    ),
  )

  @Test
  fun captureRoboImage() {
    launch(MainActivity::class.java)
    // The file will be saved using the rule's outputDirectoryPath and outputFileProvider
    onView(isRoot()).captureRoboImage()
  }
}
```

### Generate gif image

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

<img width="350" src="https://user-images.githubusercontent.com/1386930/226362212-35d34c9e-6df1-4671-8949-10fad7ad98c9.gif" />

### Generate gif with test rule

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

### Generate Jetpack Compose gif with test rule

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

### RoborazziRule options

You can use some RoborazziRule options

```kotlin
/**
 * This rule is a JUnit rule for roborazzi.
 * This rule is optional. You can use [captureRoboImage] without this rule.
 *
 * This rule have two features.
 * 1. Provide context such as `RoborazziOptions` and `outputDirectoryPath` etc for [captureRoboImage].
 * 2. Capture screenshots for each test when specifying RoborazziRule.options.captureType.
 */
class RoborazziRule private constructor(
  private val captureRoot: CaptureRoot,
  private val options: Options = Options()
) : TestWatcher() {
  /**
   * If you add this annotation to the test, the test will be ignored by
   * roborazzi's CaptureType.LastImage, CaptureType.AllImage and CaptureType.Gif.
   */
  annotation class Ignore

  data class Options(
    val captureType: CaptureType = CaptureType.None,
    /**
     * output directory path
     */
    val outputDirectoryPath: String = provideRoborazziContext().outputDirectory,

    val outputFileProvider: FileProvider = provideRoborazziContext().fileProvider
      ?: defaultFileProvider,
    val roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  )

  sealed interface CaptureType {
    /**
     * Do not generate images. Just provide the image path to [captureRoboImage].
     */
    object None : CaptureType

    /**
     * Generate last images for each test
     */
    data class LastImage(
      /**
       * capture only when the test fail
       */
      val onlyFail: Boolean = false,
    ) : CaptureType

    /**
     * Generate images for Each layout change like TestClass_method_0.png for each test
     */
    data class AllImage(
      /**
       * capture only when the test fail
       */
      val onlyFail: Boolean = false,
    ) : CaptureType

    /**
     * Generate gif images for each test
     */
    data class Gif(
      /**
       * capture only when the test fail
       */
      val onlyFail: Boolean = false,
    ) : CaptureType
  }
```

#### Image comparator custom settings
When comparing images, you may encounter differences due to minor changes related to antialiasing. You can use the options below to avoid this.
```kotlin
@get:Rule
val roborazziRule = RoborazziRule(
    options = RoborazziRule.Options(
        roborazziOptions = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(
                changeThreshold = 0.01, // For 1% accepted difference
                imageComparator = SimpleImageComparator(
                    maxDistance = 0.007F, // 0.001F is default value from Differ
                    vShift = 2, // Increasing the shift can help resolve antialiasing issues
                    hShift = 2 // Increasing the shift can help resolve antialiasing issues
                )
            )
        )
    )
)
```

### Experimental WebP support and other image formats

You can set `roborazzi.record.image.extension` to `webp` in your `gradle.properties` file to generate WebP images.

```kotlin
roborazzi.record.image.extension=webp
```

WebP is a lossy image format by default, which can make managing image differences challenging. To address this, we provide a lossless WebP image comparison feature.
To enable WebP support, add `testImplementation("io.github.darkxanter:webp-imageio:0.3.3")` to your `build.gradle.kts` file.

```kotlin
onView(ViewMatchers.withId(R.id.textview_first))
  .captureRoboImage(
    roborazziOptions = RoborazziOptions(
      recordOptions = RoborazziOptions.RecordOptions(
        imageIoFormat = LosslessWebPImageIoFormat(),
      ),
    )
  )
```

You can also use other image formats by implementing your own `AwtImageWriter` and `AwtImageLoader`.

```kotlin
data class JvmImageIoFormat(
  val awtImageWriter: AwtImageWriter,
  val awtImageLoader: AwtImageLoader
) : ImageIoFormat

```

### Dump mode

If you are having trouble debugging your test, try Dump mode as follows.

![image](https://user-images.githubusercontent.com/1386930/226364158-a07a0fb0-d8e7-46b7-a495-8dd217faaadb.png)

### Accessibility Check

Roborazzi Accessibility Checks is a library that integrates accessibility checks into Roborazzi.
Please refer to [Accessibility Check](https://github.com/takahirom/roborazzi/blob/main/roborazzi-accessibility-check/README.md)

### Roborazzi options

Please check out [RoborazziOptions](https://github.com/takahirom/roborazzi/blob/main/include-build/roborazzi-core/src/commonJvmMain/kotlin/com/github/takahirom/roborazzi/RoborazziOptions.kt) for available Roborazzi options.
