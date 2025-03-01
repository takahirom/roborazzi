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
  }
}
```

> [!NOTE] 
> If you are using build.gradle instead of build.gradle.kts, you need to use the set method for each assignment, like
> ```kotlin
> generateComposePreviewRobolectricTests.packages.set(["com.example"])
> ```

## Annotation-based Capture Control

To enable fine-grained control over screenshot timing in Compose Previews, add the new annotations dependency:

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
