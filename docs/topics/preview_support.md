# Experimental Compose Preview Support

Roborazzi provides support for generating screenshot tests and easy setup for Jetpack Compose Preview.
This support uses [ComposablePreviewScanner](https://github.com/sergio-sastre/ComposablePreviewScanner) to scan the Composable Previews in your project.

## Generate Compose Preview screenshot tests

You first need to add the Roborazzi plugin to your project. Please refer to the [setup guide](https://takahirom.github.io/roborazzi/build-setup.html) for more information.
Then you can enable the Compose Preview screenshot test generation feature by adding the following configuration to your `build.gradle.kts` file:

```kotlin
roborazzi {
  generateComposePreviewRobolectricTests {
    enable = true
  }
}
```

The plugin will not automatically change your settings or add dependencies to prevent conflicts with your existing setup. However, it will provide instructions on what to do next, such as adding dependencies and required code. You can also check the [sample project](https://github.com/takahirom/roborazzi/tree/main/sample-generate-preview-tests) for a complete example.

After that, you can run the `recordRoborazziDebug` task to generate screenshots using the generated tests, as described in the [setup guide](https://takahirom.github.io/roborazzi/build-setup.html).

### Customizing the Preview screenshot test

You can customize the generated test by adding the following configuration to your `build.gradle.kts` file:

```kotlin
roborazzi {
  @OptIn(ExperimentalRoborazziApi::class)
  generateComposePreviewRobolectricTests {
    enable = true
    // The package names to scan for Composable Previews.
    packages = listOf("com.example")
    // robolectricConfig will be passed to Robolectric's @Config annotation in the generated test class.
    // See https://robolectric.org/configuring/ for more information.
    robolectricConfig = mapOf(
      "sdk" to "[32]",
      "qualifiers" to "RobolectricDeviceQualifiers.Pixel5",
    )
    // If true, the private previews will be included in the test.
    includePrivatePreviews = true
    // The fully qualified class name of the custom test class that implements [com.github.takahirom.roborazzi.ComposePreviewTester].
    testerQualifiedClassName = "com.example.MyCustomComposePreviewTester"
    // The number of test classes to generate. Set this to match maxParallelForks for parallel test execution.
    generatedTestClassCount = 4
    // Filter previews by annotation. See "Filtering previews by annotation" below.
    annotationFilter = AnnotationFilter.Filter.RoboPreviewInclude
  }
}
```

#### Advanced: Custom ComposePreviewTester Implementation

You can create a custom `ComposePreviewTester` to control the screenshot capture behavior, such as setting a custom image comparison threshold.

Note that `AndroidComposePreviewTester` is a final class, so you can't subclass it. Instead, use Kotlin class delegation and pass a custom `Capturer` to its constructor. Also, your tester class must have a parameterless constructor because the plugin instantiates it via reflection:

```kotlin
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.*
import com.github.takahirom.roborazzi.ComposePreviewTester.TestParameter.JUnit4TestParameter.AndroidPreviewJUnit4TestParameter

@OptIn(ExperimentalRoborazziApi::class)
class MyCustomComposePreviewTester :
  ComposePreviewTester<AndroidPreviewJUnit4TestParameter> by AndroidComposePreviewTester(
    capturer = { parameter ->
      val customOptions = parameter.roborazziOptions.copy(
        compareOptions = parameter.roborazziOptions.compareOptions.copy(
          // Set custom comparison threshold (0.0 = exact match, 1.0 = ignore differences)
          imageComparator = SimpleImageComparator(maxDistance = 0.01f)
        )
      )
      AndroidComposePreviewTester.DefaultCapturer().capture(
        parameter.copy(roborazziOptions = customOptions)
      )
    }
  )
```

If you need to customize more than the capture behavior, such as the scan options or the test lifecycle, you can override `options()` or `test()` in the delegating class.

Then reference your custom tester in the Gradle configuration:

```kotlin
roborazzi {
  @OptIn(ExperimentalRoborazziApi::class)
  generateComposePreviewRobolectricTests {
    enable = true
    testerQualifiedClassName = "com.example.MyCustomComposePreviewTester"
  }
}
```

> [!NOTE] 
> If you are using Groovy DSL instead of Kotlin DSL, you need to use the set method for each assignment:
> ```kotlin
> generateComposePreviewRobolectricTests.enable.set(true)
> generateComposePreviewRobolectricTests.packages.set(["com.example"])
> ```

### Filtering previews by annotation

`annotationFilter` controls which previews are captured (requires the `roborazzi-annotations` dependency).
By default it is `AnnotationFilter.Filter.RoboPreviewExclude`, so previews annotated with
`@RoboPreviewExclude` are skipped. Set it to `RoboPreviewInclude` to capture **only** previews
annotated with `@RoboPreviewInclude`:

```kotlin
roborazzi {
  @OptIn(ExperimentalRoborazziApi::class)
  generateComposePreviewRobolectricTests {
    enable = true
    packages = listOf("com.example")
    annotationFilter = AnnotationFilter.Filter.RoboPreviewInclude
  }
}
```

To filter by your own annotations, pass their fully qualified class names
(use the JVM binary name with `$` for nested classes, e.g. `com.example.Outer$Inner`):

```kotlin
// Set either one, not both
annotationFilter = AnnotationFilter.Exclude("com.example.MyExcludeAnnotation")
annotationFilter = AnnotationFilter.Include("com.example.MyIncludeAnnotation")
```

### Compose Multiplatform previews

The Compose Preview support also works with Compose Multiplatform common previews (`@Preview` in `commonMain`). You can scan them with the `CommonComposablePreviewScanner` from the ComposablePreviewScanner `common` artifact in a custom tester; the generated tests run as Android unit tests with Robolectric. See the [multiplatform sample project](https://github.com/takahirom/roborazzi/tree/main/sample-generate-preview-tests-multiplatform) for a complete setup.

## Experimental Compose Desktop Preview Support

Roborazzi can also generate preview screenshot tests for the Compose Desktop (JVM)
target, without Robolectric. Previews are scanned with ComposablePreviewScanner's
`android` artifact, which is a pure-JVM jar: it finds the multiplatform
`androidx.compose.ui.tooling.preview.Preview` annotation on the classpath, so previews
declared in `commonMain` are captured too.

Enable it in your `build.gradle.kts`:

```kotlin
roborazzi {
  @OptIn(ExperimentalRoborazziApi::class)
  generateComposePreviewDesktopTests {
    enable = true
    packages = listOf("com.example")
    // Required only when the project has multiple Kotlin JVM targets:
    // targetName = "desktop"
  }
}
```

Add the dependencies to the JVM target's test source set. If your previews use the
Roborazzi marker annotations (`@RoboPreviewExclude`, `@RoboComposePreviewOptions`, ...),
also add `roborazzi-annotations` to the source set that declares the previews
(usually `commonMain` — test dependencies do not flow into main source sets):

```kotlin
kotlin {
  sourceSets {
    val commonMain by getting {
      dependencies {
        // Only needed when previews use the Roborazzi marker annotations
        implementation("io.github.takahirom.roborazzi:roborazzi-annotations:[version]")
      }
    }
    val desktopTest by getting {
      dependencies {
        implementation("io.github.takahirom.roborazzi:roborazzi-compose-desktop-preview-scanner-support:[version]")
        implementation("io.github.sergio-sastre.ComposablePreviewScanner:android:[version]")
        implementation("junit:junit:4.13.2")
      }
    }
  }
}
```

Plain JVM projects (`org.jetbrains.kotlin.jvm`) are also supported: add the same
dependencies with `testImplementation(...)` and use the `Jvm` task names
(`recordRoborazziJvm`, `compareRoborazziJvm`, `verifyRoborazziJvm`).

Then run the desktop Roborazzi tasks:

```shell
./gradlew recordRoborazziDesktop
./gradlew compareRoborazziDesktop
./gradlew verifyRoborazziDesktop
```

Note: ComposablePreviewScanner is published with JVM 17 metadata, so the desktop
target needs to target JVM 17 (or relax the test classpath's `TargetJvmVersion`
attribute). See the [desktop multiplatform sample](https://github.com/takahirom/roborazzi/tree/main/sample-compose-desktop-multiplatform)
for a complete setup, including manual usage of the tester API without the generator.

### Screenshot naming and mixed modules

Desktop preview screenshots use the same file names as the Robolectric preview tests
(fully qualified class name + method name + preview parameter suffix), so the same
preview produces the same file name on both platforms. If one module enables **both**
`generateComposePreviewRobolectricTests` and `generateComposePreviewDesktopTests`, the
two sets of screenshots would overwrite each other in the shared output directory, so
Roborazzi fails with a configuration error unless
[`separateOutputDirs`](https://takahirom.github.io/roborazzi/build-setup.html#separate-output-directories-per-varianttarget-experimental)
is enabled, which gives each task its own subdirectory.

### Customizing the desktop tester

`DefaultDesktopComposePreviewTester` accepts a `Capturer` whose receiver is the raw
`ComposeUiTest` scope, so anything possible inside `runDesktopComposeUiTest` — clock
control, interactions, wrapping the content in a theme — stays possible:

```kotlin
@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
class MyDesktopPreviewTester : DesktopComposePreviewTester by DefaultDesktopComposePreviewTester(
  capturer = DefaultDesktopComposePreviewTester.Capturer { parameter ->
    setContent { MyTheme { parameter.preview() } }
    // Keep @RoboComposePreviewOptions manualClockOptions working: without this,
    // time-suffixed captures would all show the initial state.
    advanceMainClockFor(parameter)
    onRoot().captureRoboImage(parameter.filePath, parameter.roborazziOptions)
  }
)
```

Reference your tester in the Gradle configuration with
`testerQualifiedClassName = "com.example.MyDesktopPreviewTester"` (the class needs a
parameterless constructor). If you need to change scanning or file naming as well,
implement `DesktopComposePreviewTester` by delegating to the default tester and
override the corresponding method (`testParameters()` / `test()`).

To wrap each generated test in a JUnit `TestRule` (a `TestWatcher`, retry rule, etc.),
override `options()` and provide a `testRuleFactory`:

```kotlin
@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
class MyDesktopPreviewTester : DesktopComposePreviewTester by DefaultDesktopComposePreviewTester() {
  override fun options(): DesktopComposePreviewTester.Options =
    DesktopComposePreviewTester.defaultOptionsFromPlugin.copy(
      testLifecycleOptions = DesktopComposePreviewTester.Options.JUnit4TestLifecycleOptions(
        testRuleFactory = { MyWatcherRule() }
      )
    )
}
```

Unlike the Robolectric tester there is no compose rule factory: Compose Desktop's test
harness is function-scoped (`runDesktopComposeUiTest`), not rule-based.

### Feature parity with the Android preview support

| Feature | Android (Robolectric) | Compose Desktop |
|---|---|---|
| Generated preview tests | ✅ | ✅ |
| `packages`, `includePrivatePreviews`, `testerQualifiedClassName`, `generatedTestClassCount` | ✅ | ✅ |
| `annotationFilter` (`@RoboPreviewInclude` / `@RoboPreviewExclude`) | ✅ | ✅ |
| `@PreviewParameter` (`PreviewParameterProvider`, one capture per value) | ✅ | ✅ |
| `@RoboComposePreviewOptions` (`manualClockOptions`, one test per variation) | ✅ | ✅ |
| Custom JUnit `TestRule` around generated tests (`testRuleFactory`) | ✅ | ✅ |
| Compose rule factory (`composeRuleFactory`) | ✅ | Not applicable (function-scoped harness) |
| `@Preview` annotation options (`widthDp`, `fontScale`, `uiMode`, ...) | ✅ (see below) | Not yet — previews render wrap-content |
| `robolectricConfig` (device qualifiers, SDK) | ✅ | Not applicable |

## Annotation-based Capture Control

To enable fine-grained control over screenshot timing in Compose Previews, add the annotations dependency:

```gradle
testImplementation("io.github.takahirom.roborazzi:roborazzi-annotations:[version]")
```

Use `@RoboComposePreviewOptions` to configure time-based captures:

```kotlin
@RoboComposePreviewOptions(
  manualClockOptions = [ManualClockOptions(advanceTimeMillis = 516L)]
)
@Preview
@Composable
fun DelayedPreview() {
  var visible by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    delay(500)
    visible = true
  }
  if (visible) {
    Text("Content appears after 500ms")
  }
}
```

This annotation enables capturing screenshots at specific time intervals, particularly useful for testing animated components or delayed state changes.

## PreviewWrapper support

Previews annotated with [`@PreviewWrapper`](https://developer.android.com/reference/kotlin/androidx/compose/ui/tooling/preview/PreviewWrapper) (Compose UI 1.11+) are automatically wrapped by ComposablePreviewScanner 0.9.0 or later, so the wrapper's content, such as a theme or background, appears in the screenshots without any extra setup:

```kotlin
class MyWrapperProvider : PreviewWrapperProvider {
  @Composable
  override fun Wrap(content: @Composable () -> Unit) {
    MyTheme { content() }
  }
}

@PreviewWrapper(MyWrapperProvider::class)
@Preview
@Composable
fun WrappedPreview() { ... }
```

## Manually adding Compose Preview screenshot tests

Roborazzi provides a helper function for ComposablePreviewScanner.
You can add the following dependency to your project to use the helper function:

`testImplementation("io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support:[version]")`

Then you can use the `ComposablePreview<AndroidPreviewInfo>.captureRoboImage()` function to capture the Composable Preview using the settings in Preview annotations.
To obtain the `ComposablePreview` object, please refer to [ComposablePreviewScanner](https://github.com/sergio-sastre/ComposablePreviewScanner).

```kotlin
fun ComposablePreview<AndroidPreviewInfo>.captureRoboImage(
  filePath: String,
  roborazziOptions: RoborazziOptions
)
```

### The supported `@Preview` annotation options

Currently, we don't support all the annotation options provided by the Compose Preview.
You can check the supported annotations in the [source code](https://github.com/takahirom/roborazzi/blob/0810ceb7133e6ec38472046cb741242a5ef6ab9e/roborazzi-compose-preview-scanner-support/src/main/java/com/github/takahirom/roborazzi/RoborazziPreviewScannerSupport.kt#L27).
We are looking forward to your contributions to support more annotation options.
