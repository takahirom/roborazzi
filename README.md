# Roborazzi

**Make JVM Android Integration Test Visible**

## Roborazzi now supports [Robolectric Native Graphics (RNG)](https://github.com/robolectric/robolectric/releases/tag/robolectric-4.10) and enables screenshot testing.📣

## Why Choose Roborazzi?

### Why we need screenshot testing?

Screenshot testing is important for checking how your app looks and works.
It can catch visual issues and also check the overall flow of your app. It's like testing your app
the same way your users would use it, making it easier to catch problems that they would actually
face.
Plus, it's a great way to spot changes - this can be easier and quicker than writing lots of assert
statements. So, with screenshot testing, you're not just checking your app looks right, you're also
making sure it behaves correctly and is user-friendly.

### Why test with JVM instead of testing on Android?

When testing on a device, tests can fail easily due to the device environment, animations, etc. This
can result in false negatives, where tests fail due to issues with the device environment rather
than the application code itself. These failures are often hard to reproduce and troubleshoot,
making them difficult to fix.

### Paparazzi and Roborazzi: A Comparison

Paparazzi is a fantastic tool for visualizing actual displays within the JVM. It leverages LayoutLib, Android Studio's layout drawing tool, for this purpose. However, because Paparazzi and Robolectric both mock the Android framework, they are incompatible.

Roborazzi steps in to fill this gap. It integrates with Robolectric, making it possible to run tests with Hilt and interact with components, such as clicking on elements. Essentially, Roborazzi extends the capabilities of Paparazzi, offering a more dynamic, user-oriented testing experience that captures screenshots with Robolectric.

## Try it out

Available on Maven Central.

### Add Robolectric

This library is dependent on Robolectric. Please see below to add Robolectric.

https://robolectric.org/getting-started/

To take screenshots, please use Robolectric 4.10 alpha 1 or later and please
add `@GraphicsMode(GraphicsMode.Mode.NATIVE)` to your test class.

```kotlin
@GraphicsMode(GraphicsMode.Mode.NATIVE)
```

### Apply Roborazzi Gradle Plugin

Roborazzi is available on maven central.

This plugin simply creates Gradle tasks record, verify, compare and passes the configuration to the
test.

<table>
<tr><td>plugins</td><td>buildscript</td></tr>
<tr><td>

Define plugin in root build.gradle

```groovy
plugins {
  ...
  id "io.github.takahirom.roborazzi" version "[version]" apply false
}
```

Apply plugin in module build.gradle

```groovy
plugins {
  ...
  id 'io.github.takahirom.roborazzi'
}
```

</td><td>

root build.gradle

```groovy
buildscript {
  dependencies {
    ...
    classpath "io.github.takahirom.roborazzi:roborazzi-gradle-plugin:[version]"
  }
}
```

module build.gradle

```groovy
apply plugin: "io.github.takahirom.roborazzi"
```

</td></tr>

</table>


<table>
<tr>
<td> Gradle Command </td> <td> Description </td>
</tr>
<tr>
<td>

```sh
./gradlew recordRoborazziDebug
```

</td><td> 

Record a screenshot

</td>
</tr>
<tr>
<td>

```sh
./gradlew compareRoborazziDebug
```

</td><td>

Review changes made to an image. This action will
compare the current image with the saved one, generating a comparison image labeled
as `[original]_compare.png`. It also produces a JSON file containing the diff information, which can
be found under `build/test-results/roborazzi`.

</td>
</tr>
<tr>
<td>

```sh
./gradlew verifyRoborazziDebug
```

</td><td>

Validate changes made to an image. If there is any difference between the current image and the
saved one, the test will fail.

</td>
</tr>
<tr>
<td>

```sh
./gradlew verifyAndRecordRoborazziDebug
```

</td><td>

This task will first verify the images and, if differences are detected, it will record a new
baseline.

</td>
</tr>

</table>

![image](https://user-images.githubusercontent.com/1386930/226360316-69080436-c273-469b-bc45-55d73bd99975.png)

### Add dependencies

| Description     | Dependencies                                                                         |
|-----------------|--------------------------------------------------------------------------------------|
| Core functions  | `testImplementation("io.github.takahirom.roborazzi:roborazzi:[version]")`            |
| Jetpack Compose | `testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:[version]")`    |
| JUnit rules     | `testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:[version]")` |

## How to use

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
    ActivitySenario.launch(MainActivity::class.java)

    // Capture screen
    onView(ViewMatchers.isRoot())
      // If you don't specify a screenshot file name, Roborazzi will automatically use the method name as the file name for you.
      // The format of the file name will be as follows:
      // build/outputs/roborazzi/com_..._ManualTest_captureRoboImageSample.png
      .captureRoboImage()

    // Capture Jetpack Compose Node
    composeTestRule.onNodeWithTag("MyComposeButton")
      .onParent()
      .captureRoboImage("build/compose.png")

    // Capture small view on window
    onView(withId(R.id.button_first))
      .captureRoboImage("build/button.png")

    // move to next page
    onView(withId(R.id.button_first))
      .perform(click())

    val view: View = composeTestRule.activity.findViewById<View>(R.id.button_second)
    // Capture view on window
    view.captureRoboImage("build/manual_view_on_window.png")

    val textView = TextView(composeTestRule.activity).apply {
      text = "Hello View!"
      setTextColor(android.graphics.Color.RED)
    }
    // Capture view not on window
    textView.captureRoboImage("build/manual_view_without_window.png")

    // Capture Jetpack Compose lambda
    captureRoboImage("build/manual_compose.png") {
      Text("Hello Compose!")
    }

    val bitmap: Bitmap = createBitmap(100, 100, Bitmap.Config.ARGB_8888)
      .apply {
        applyCanvas {
          drawColor(android.graphics.Color.YELLOW)
        }
      }
    // Capture Bitmap
    bitmap.captureRoboImage("build/manual_bitmap.png")
  }
}
```

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

### Generate gif automatically

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

### Automatically generate gif with test rule

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
      onlyFail = true
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

### Compose Support

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
        .testTag("MyComposeButton")
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
      RoborazziRule.CaptureType.Gif
    )
  )

  @Test
  fun composable() {
    composeTestRule.setContent {
      SampleComposableFunction()
    }
    (0 until 3).forEach { _ ->
      composeTestRule
        .onNodeWithTag("MyComposeButton")
        .performClick()
    }
  }
}
```

![com github takahirom roborazzi sample ComposeTest_composable](https://user-images.githubusercontent.com/1386930/226366224-b9950b60-26a2-489e-bc03-08bfb86c533a.gif)

### RoborazziRule options

You can use some RoborazziRule options

```kotlin
class RoborazziRule private constructor(
  ...
) : TestWatcher() {
  /**
   * If you add this annotation to the test, the test will be ignored by roborazzi
   */
  annotation class Ignore

  data class Options(
    val captureType: CaptureType = CaptureType.Gif,
    /**
     * capture only when the test fail
     */
    val onlyFail: Boolean = false,
    /**
     * output directory path
     */
    val outputDirectoryPath: String = DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH,
    val roborazziOptions: RoborazziOptions = RoborazziOptions(),
  )

  enum class CaptureType {
    /**
     * Generate last images for each test
     */
    LastImage,

    /**
     * Generate images for each layout change such as TestClass_method_0.png for each test.
     */
    AllImage,

    /**
     * Generate gif images for each test
     */
    Gif
  }
```

### Roborazzi options

```
data class RoborazziOptions(
  val captureType: CaptureType = if (isNativeGraphicsEnabled()) CaptureType.Screenshot() else CaptureType.Dump(),
  val verifyOptions: VerifyOptions = VerifyOptions(),
  val recordOptions: RecordOptions = RecordOptions(),
) {
  sealed interface CaptureType {
    class Screenshot : CaptureType

    data class Dump(
      val takeScreenShot: Boolean = isNativeGraphicsEnabled(),
      val basicSize: Int = 600,
      val depthSlideSize: Int = 30,
      val query: ((RoboComponent) -> Boolean)? = null,
    ) : CaptureType
  }

  data class VerifyOptions(
    /**
     * This value determines the threshold of pixel change at which the diff image is output or not.
     * The value should be between 0 and 1
     */
    val resultValidator: (result: ImageComparator.ComparisonResult) -> Boolean
  ) {
    constructor(
      changeThreshold: Float = 0.01F,
    ) : this(ThresholdValidator(changeThreshold))
  }

  data class RecordOptions(
    val resizeScale: Double = 1.0
  )
```

### Dump mode

If you are having trouble debugging your test, try Dump mode as follows.

![image](https://user-images.githubusercontent.com/1386930/226364158-a07a0fb0-d8e7-46b7-a495-8dd217faaadb.png)

### LICENSE

```
Copyright 2023 takahirom
Copyright 2019 Square, Inc.
Copyright The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
