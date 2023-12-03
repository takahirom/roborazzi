# FAQ

### Q: How can I run only screenshot tests using Roborazzi?

**A:** To run only screenshot tests, you can configure your project with the following:

```groovy
android {
    testOptions {
        unitTests {
            all {
                // -Pscreenshot to filter screenshot tests
                it.useJUnit {
                    if (project.hasProperty("screenshot")) {
                        includeCategories("io.github.takahirom.roborazzi.testing.category.ScreenshotTests")
                    }
                }
            }
        }
    }
}
```

Include the `-Pscreenshot` property, and only the screenshot tests will be run.

Note: This feature is not provided in the Roborazzi library itself, to keep it simple and utilize JUnit's built-in features for test filtering.

You can also annotate your tests like this:

```kotlin
/**
 * You can filter ScreenshotTests using -Pscreenshot parameter
 */
interface ScreenshotTests

@Test
@Category(ScreenshotTests::class)
fun checkLaunchShot() {
  onRoot().captureRoboImage(roborazziOptions = roborazziOptions)
}
```

This allows you to create a category of screenshot tests and filter them using the `-Pscreenshot` property, thus making it easier to run only those specific tests.

### Q: How can I debug screenshot tests in Android Studio?
### Q: How can I execute screenshot tests using Android Studio's Run button?

A: To execute screenshot tests using Android Studio's Run button, configure your project as follows:

`gradle.properties`

```groovy
roborazzi.test.record=true
```

After that, you can execute screenshot tests using either Android Studio's Run or Debug button as you normally would.

### Q: My screenshot tests are not capturing images. What could be the issue?

**A:** If your screenshot tests are not capturing images, there may be several patterns that are causing this issue. Please follow these troubleshooting steps:

- **Enable Debugging**: Set `ROBORAZZI_DEBUG = true` to see logs.
- **Check Plugin**: Ensure that the plugin is properly applied.
- **Run Task**: Verify that the `recordRoborazziDebug` task is running.
- **Call Method**: Confirm that `captureRoboImage()` is being called.

By following these steps, you should be able to identify and resolve the issue causing the screenshot tests to not capture images.

### Q: I'm seeing an optimization warning related to Java lambdas in Gradle. What can I do?

**A:** This warning may occur with Gradle 7.5. Upgrade to Gradle 7.6.2 to resolve this issue. Change the distribution URL in `gradle-wrapper.properties`:

```diff
-distributionUrl=https\://services.gradle.org/distributions/gradle-7.5-bin.zip
+distributionUrl=https\://services.gradle.org/distributions/gradle-7.6.2-bin.zip
```

### Q: Can I run Roborazzi with Bazel?

**A:** As of now, there is no direct support for running Roborazzi with Bazel. However, it is possible to do so. Please refer to the following comment for more details:
[Roborazzi Bazel Support Comment](https://github.com/takahirom/roborazzi/issues/63#issuecomment-1531990825)

### Q: My tests are being skipped or, conversely, are being run when they should be skipped. How can I handle caching to address this?

**A:** The behavior you are experiencing may be related to caching issues. Although it's
experimental, you can set the `outputDir` parameter in your `build.gradle` file to handle caching
and improve the stability of your tests. This parameter allows you to specify the output directory
for your screenshots, which can help in managing the cache. Here is how you can set it up:
If you use the default output directory(module/build/outputs/roborazzi), specifying the `outputDir`
parameter is not necessary. For more reference, you can check
out [the issue](https://github.com/takahirom/roborazzi/issues/193#issuecomment-1782073746).

```gradle
roborazzi {
    outputDir = "src/your/screenshot/folder"
}
```