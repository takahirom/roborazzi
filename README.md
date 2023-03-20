# Roborazzi

**Make JVM Android Integration Test Visible**

## Roborazzi now supports [Robolectric Native Graphics (RNG)](https://github.com/robolectric/robolectric/releases/tag/robolectric-4.10-alpha-1) and enables screenshot testing.📣

To save the image, run `recordRoborazziDebug` task.

```
 ./gradlew recordRoborazziDebug
```

To view the changes in the image, run `verifyRoborazziDebug` task. This way, the changes between the image and the one you are saving now will be saved as [original]_compare.png.

```
 ./gradlew verifyRoborazziDebug
```

![image](https://user-images.githubusercontent.com/1386930/226360316-69080436-c273-469b-bc45-55d73bd99975.png)

## Why test with JVM instead of testing on Android?

Because when testing on a device, it is easy for the test to fail due to the device environment, animations, etc. 
This affects the reliability of the test and ultimately, if the test fails, it will not be fixed.

## Why not Paparazzi?

Paparazzi is a great tool to see the actual display in the JVM.  
Paparazzi relies on LayoutLib, Android Studio's layout drawing tool, which is incompatible with Robolectric. 
This is because they both mock the Android framework.  
Without Robolectric, you can't write tests that actually click on components and run them with Hilt tests.



## Try it out

Available on Maven Central.

### Add Robolectric

This library is dependent on Robolectric. Please see below to add Robolectric.

https://robolectric.org/getting-started/

To take screenshots, please use Robolectric 4.10 alpha 1 or later and please add `@GraphicsMode(GraphicsMode.Mode.NATIVE)` to your test class.

```kotlin
@GraphicsMode(GraphicsMode.Mode.NATIVE)
```

[Learn more about why we use Robolectric instead of device tests.](#why-test-with-jvm-instead-of-testing-on-android).

### Apply Roborazzi Gradle Plugin

Roborazzi is available on maven central.

root build.gradle

```
plugins {
...
  id "io.github.takahirom.roborazzi" version "[write the latest vesrion]" apply false
}
```

module build.gradle

```
plugins {
...
  id 'io.github.takahirom.roborazzi'
}
```

or

root build.gradle

```kotlin
buildscript {
  dependencies {
  ...
    classpath 'io.github.takahirom.roborazzi:roborazzi-gradle-plugin:[write the latest vesrion]'
  }
}
```

module build.gradle

```
apply plugin: 'io.github.takahirom.roborazzi'
```

### Add dependencies


```
// Core functions
testImplementation("io.github.takahirom.roborazzi:roborazzi:[write the latest vesrion]")
// JUnit rules
testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:[write the latest vesrion]")
```

## How to use

### Take a screenshot manually

You can take a screenshot by calling captureRoboImage().

app/src/test/java/../ManualTest.kt

```kotlin
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
...

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

    // screen level image
    onView(ViewMatchers.isRoot())
      .captureRoboImage("build/first_screen.png")

    // compose image
    composeTestRule.onNodeWithTag("MyComposeButton")
      .onParent()
      .captureRoboImage("build/compose.png")

    // small component image
    onView(withId(R.id.button_first))
      .captureRoboImage("build/button.png")

    // move to next page
    onView(withId(R.id.button_first))
      .perform(click())

    onView(ViewMatchers.isRoot())
      .captureRoboImage("build/second_screen.png")
  }
```


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
  fun captureRoboGifSampleFail() {
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
  val outputDirectoryPath: String = DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH
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

### Dump mode

If you are having trouble debugging your test, try Dump mode as follows.

![image](https://user-images.githubusercontent.com/1386930/226364158-a07a0fb0-d8e7-46b7-a495-8dd217faaadb.png)

