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

<details>
<summary>build.gradle version</summary>
<br>

```groovy
roborazzi {
    generateComposePreviewRobolectricTests.enable.set(true)
    // The package names to scan for Composable Previews.
    generateComposePreviewRobolectricTests.packages.set(["com.example"])
    // robolectricConfig will be passed to Robolectric's @Config annotation in the generated test class.
    // See https://robolectric.org/configuring/ for more information.
    generateComposePreviewRobolectricTests.robolectricConfig.set([
        "sdk": "[32]",
        "qualifiers": "RobolectricDeviceQualifiers.Pixel5",
    ])
    // If true, the private previews will be included in the test.
    generateComposePreviewRobolectricTests.includePrivatePreviews.set(true)
    // The fully qualified class name of the custom test class that implements [com.github.takahirom.roborazzi.ComposePreviewTester].
    generateComposePreviewRobolectricTests.testerQualifiedClassName.set("com.example.MyCustomComposePreviewTester")
}
```

</details>


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
You can check the supported annotations in the [source code](https://github.com/takahirom/roborazzi/blob/main/roborazzi-compose-preview-scanner-support/src/main/java/com/github/takahirom/roborazzi/RobolectricPreviewInfosApplier.kt).
We are looking forward to your contributions to support more annotation options.
