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

    // Experimental: render at a lower density to make the tests faster and the images smaller.
    // See "Making the tests faster with renderScale" below.
    // renderScale = 1.0 / 3

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

`Options` is how the Gradle extension reaches your tester: the generated test assigns the configured values to `ComposePreviewTester.defaultOptionsFromPlugin`, and the default `options()` returns them. So when you override `options()`, derive the result with `super.options().copy(...)` instead of constructing a new `Options`, otherwise every setting the plugin configured is silently replaced by defaults. The same applies if you implement `test()` or the capture yourself: read the values from `options()` rather than assuming defaults.

Two settings need extra care with a custom tester:

- `includePrivatePreviews` and `annotationFilter` are consumed by `testParameters()`. Because a custom tester usually overrides it, the plugin rejects the combination unless you set `useScanOptionParametersInTester = true` and read `options().scanOptions` yourself.
- `renderScale` (see [Making the tests faster with `renderScale`](#making-the-tests-faster-with-renderscale)) is consumed at capture time, so the class-delegation pattern above keeps working. If you override `test()` yourself, see the `renderScale` property documentation for what to pass to `preview.toRoborazziComposeOptions(renderScale)`. A tester that drops the value fails the generated test with an explanation, so a silently unscaled screenshot is not possible.

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

### Making the tests faster with `renderScale`

If the generated tests are slow or the recorded images are large, `renderScale` is the knob for it.
A value below 1.0 renders every preview at a lower device density, so fewer pixels are rendered:
the tests spend less time rendering and the images take less space. What you pay for it is
fidelity, so it pays off on previews that stay readable at the smaller size.

What the scale does and does not change — density-qualified resources, raw-pixel drawing, the dpi
rounding, when it is applied — is documented on the `renderScale` property itself, which your IDE
shows as you type it.

`renderScale` is not the same as `resizeScale`:

| Property | When it applies | What it changes |
| --- | --- | --- |
| `renderScale` | Before Compose renders | Device density. Logical dp dimensions are preserved, so a smaller surface is rendered. Density-qualified resources (`drawable-hdpi` and so on) may resolve differently. |
| `resizeScale` (`RoborazziOptions.RecordOptions`, `roborazzi.record.resizeScale`) | After the screenshot is captured | Downsamples the captured bitmap. Rendering cost is unchanged. |

Both change the recorded image dimensions at the same output paths, so existing golden images have to be recorded again after you set either of them.

In the Groovy DSL, write the value as a `double` literal: `renderScale = 0.5d`. A bare `0.5` is a `BigDecimal` and fails to convert.

To scale a single preview differently, annotate it with `@RoboComposePreviewOptions(renderScale = ...)`. This is useful when only a few previews are large enough to be worth the loss of fidelity:

```kotlin
@RoboComposePreviewOptions(renderScale = 0.5)
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun TabletPreview() {
}
```

Previews without the annotation keep the scale configured in the Gradle extension.

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

### Robolectric or Desktop — trade-offs

- **Fidelity**: Robolectric renders with the Android framework; desktop renders with the
  host's Skia, so the same preview produces different images — goldens are per-platform.
- **Speed**: desktop tests run roughly 4–6x faster than the Robolectric ones ([benchmark](https://github.com/takahirom/roborazzi/pull/903)).
- **Adoption cost**: requires a Kotlin JVM target — a Kotlin Multiplatform `jvm()`
  target or a plain `org.jetbrains.kotlin.jvm` project. For an Android-only project
  that means a KMP migration first — stick with the Robolectric preview tests there.
  Desktop tests shine for already-multiplatform code and Desktop-only apps.

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

### Device profiles (experimental)

A device profile decides how the desktop runtime sizes and scales previews. It is a
property of a *test run*, not of a preview, so one module can capture the same previews
more than once under different profiles.

```kotlin
roborazzi {
  generateComposePreviewDesktopTests {
    enable = true
    packages = listOf("com.example.previews")
    deviceProfile = DesktopPreviewDeviceProfile.AndroidCompatible
  }
}
```

The presets are:

| Profile | What it does |
|---|---|
| `DesktopPreviewDeviceProfile.Desktop` | The historical desktop behaviour: density 1, a canvas of at least 1024x768, and only `widthDp`/`heightDp` affect the size. |
| `DesktopPreviewDeviceProfile.AndroidCompatible` | Sizes previews the way the Robolectric runtime does, so the same preview can be compared between the two runtimes. |

To vary a single axis, start from a preset and use `copy()`.

`Desktop` is the default, so an existing project keeps the screenshots it already has. It ignores
`@Preview(device = ...)`, and it says so once per test run when a preview asks for a device, so
that a preview naming a Pixel and coming out 1024x768 does not look like a bug.

Under `AndroidCompatible`:

- `@Preview(device = ...)` is parsed - `id:`, `name:` and `spec:` all work, with the same parser
  the Robolectric runtime uses - and it decides both the raster surface and the density.
- A preview that names no device is sized as the profile's `defaultDevice`. This stands in for the
  Robolectric base configuration, so write your `qualifiers` in `@Preview` grammar: the default is
  `spec:width=393dp,height=851dp,dpi=440`, which is `RobolectricDeviceQualifiers.Pixel4a`. Change
  both together, or the two runtimes size device-less previews differently on purpose.
- A `defaultDevice` given in dp makes the same dp -> px -> dp round trip the base configuration
  makes, which is not a no-op: `width=411dp` at 420dpi is 1078px, reads back as 410dp, and renders
  at 1076px. A device id such as `id:pixel_7` names a pixel-size entry instead and is converted
  once. Prefer a `spec:` in dp for `defaultDevice`, since that is the form a qualifier takes.
- `widthDp`/`heightDp` are dp at that density rather than raw pixels, so a 200dp box on a 440dpi
  device is 550px wide on both runtimes.

The surface size and the density the two runtimes use agree exactly, including for devices whose
size is declared in pixels: the density round trip that Robolectric performs is reproduced rather
than approximated. What does not agree is text measurement - Android bends font scale non-linearly from API 34, and
glyph advances differ by a few pixels - so a preview whose size is driven by laid-out text can still
come out a little wider or taller.

#### Capturing the same previews under several profiles

Give each profile its own Kotlin test run. Roborazzi already gives every test run of a
JVM target its own set of tasks and, with `separateOutputDirs`, its own output directory,
so the two sets of screenshots never overwrite each other:

```kotlin
kotlin {
  jvm("desktop") {
    testRuns.create("androidCompat")
  }
}

roborazzi {
  // required as soon as a target has more than one test run recording previews
  separateOutputDirs = true
  generateComposePreviewDesktopTests {
    enable = true
    packages = listOf("com.example.previews")
    deviceProfileByTestRun.put(
      "androidCompat",
      DesktopPreviewDeviceProfile.AndroidCompatible,
    )
  }
}
```

```bash
./gradlew recordRoborazziDesktop              # build/outputs/roborazzi/desktop/
./gradlew recordRoborazziDesktopAndroidCompat # build/outputs/roborazzi/desktopAndroidCompat/
```

`deviceProfileByTestRun` is Kotlin Multiplatform only, because a Kotlin JVM project has a
single `test` task and therefore no run to key a profile by. Use `deviceProfile` there.

A custom tester sees the profile as `options().deviceProfile`. Build your options from
`DesktopComposePreviewTester.defaultOptionsFromPlugin.copy(...)`, as the examples below
do: options constructed from scratch drop whatever the plugin configured, the profile
included.

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
| `@Preview` annotation options (`widthDp`/`heightDp`, `fontScale`, `showBackground`/`backgroundColor`, `locale`, `uiMode` dark bit) | ✅ (see below) | ✅ |
| `@Preview(device = ...)` | ✅ | ✅ with `deviceProfile = AndroidCompatible`; ignored under the default `Desktop` profile |
| `robolectricConfig` (device qualifiers, SDK) | ✅ | Not applicable - the equivalent is the device profile's `defaultDevice` |

On Compose Desktop the `@Preview` annotation options are applied as follows:

- `widthDp`/`heightDp`: the preview is wrapped in a fixed-size box. Under the default `Desktop` profile density is `1`, so 1dp equals 1px; under `AndroidCompatible` they are dp at the device density. When neither is specified the preview still renders wrap-content.
- `fontScale`: applied through `LocalDensity`, together with the density the device profile resolved, because `DeviceConfigurationOverride.FontScale` is unsupported on desktop.
- `showBackground`/`backgroundColor`: draws a background behind the preview, defaulting to white when `showBackground = true` but no color is given.
- `locale`: sets `java.util.Locale.getDefault()` for the capture and restores it afterwards. Accepts `"ja"`, `"ja-rJP"`, and `"ja-JP"` forms.
- `uiMode`: only the night bit is honored (dark mode via `LocalSystemTheme`); other configuration bits are ignored.
- `device`: honored under the `AndroidCompatible` device profile, which turns it into the surface size and the density. The default `Desktop` profile ignores it. A spec the parser cannot read fails the test rather than being skipped, which is stricter than the Robolectric runtime - it ignores an unreadable spec and renders at the default size.

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
