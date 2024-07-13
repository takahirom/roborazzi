# Experimental Compose Preview Support

Roborazzi provides support for generating screenshot tests and easy setup for Jetpack Compose Preview.
This support uses [ComposePreviewScanner](https://github.com/sergio-sastre/ComposablePreviewScanner) to scan the Composable Previews in your project.

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

The plugin will not automatically change your settings or add dependencies to prevent conflicts with your existing setup. However, it will provide instructions on what to do next, such as adding dependencies and required code.

After that, you can run the `recordRoborazziDebug` task to generate screenshots using the generated tests, as described in the [setup guide](https://takahirom.github.io/roborazzi/build-setup.html).

### Customizing the Preview screenshot test

You can customize the generated test by adding the following configuration to your `build.gradle` file:

```kotlin
roborazzi {
  generateComposePreviewRobolectricTests {
    enable = true
    // The package names to scan for Composable Previews.
    packages = listOf("com.example")
    // The fully qualified class name of the custom test class that implements [com.github.takahirom.roborazzi.RobolectricPreviewTest].
    customTestQualifiedClassName = "com.example.MyCustomRobolectricPreviewTest"
    // robolectricConfig will be passed to Robolectric's @Config annotation in the generated test class.
    // See https://robolectric.org/configuring/ for more information.
    robolectricConfig = mapOf(
      "sdk" to "[32]",
      "qualifiers" to "RobolectricDeviceQualifiers.Pixel5",
    )
  }
}
```

## Manually adding Compose Preview screenshot tests

Roborazzi provides a helper function for ComposePreviewScanner.
You can add the following dependency to your project to use the helper function:

`testImplementation("io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support:[version]")`

Then you can use the `ComposablePreview<AndroidPreviewInfo>.captureRoboImage()` function to capture the Composable Preview using the settings in Preview annotations.
To obtain the `ComposablePreview` object, please refer to [ComposePreviewScanner](https://github.com/sergio-sastre/ComposablePreviewScanner).

```kotlin
fun ComposablePreview<AndroidPreviewInfo>.captureRoboImage(
  filePath: String,
  roborazziOptions: RoborazziOptions
)
```

### The supported `@Preview` annotation options

Currently, we don't support all the annotation options provided by the Compose Preview.
You can check the supported annotations in the [source code](https://github.com/takahirom/roborazzi/blob/main/roborazzi-compose-preview-scanner-support/src/main/java/com/github/takahirom/roborazzi/RobolectricPreviewInfosApplier.kt).
We are looking forward to your contributions to support more annotation options.
