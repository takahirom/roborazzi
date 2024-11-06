# Roborazzi gradle.properties Options and Recommendations

You can configure the following options in your `gradle.properties` file.
You can also use `-P` to set the options in the command line. For example, `./gradlew test -Proborazzi.test.record=true`.

## roborazzi.test

This option enables you to configure the behavior of Roborazzi. By default, all settings are set to false.
For additional configuration options, please refer to the 'Apply Roborazzi Gradle Plugin' section.

```
roborazzi.test.record=true
# roborazzi.test.compare=true
# roborazzi.test.verify=true
```

## roborazzi.record

### roborazzi.record.resizeScale

This option lets you set the resize scale for the image being recorded. The default value is 1.0.

```
roborazzi.record.resizeScale=0.5
```

### roborazzi.record.filePathStrategy

This setting allows you to specify the file path strategy for the recorded image. The default strategy is `relativePathFromCurrentDirectory`. If you choose `relativePathFromRoborazziContextOutputDirectory`, the file will be saved in the output directory specified by `RoborazziRule.Options.outputDirectoryPath`.

```
roborazzi.record.filePathStrategy=relativePathFromRoborazziContextOutputDirectory
```

### roborazzi.record.namingStrategy

This option enables you to define the naming strategy for the recorded image. The default strategy is `testPackageAndClassAndMethod`.

- If you choose `testPackageAndClassAndMethod`, the file name will be `com.example.MyTest.testMethod.png`.
- If you choose `escapedTestPackageAndClassAndMethod`, the file name will be `com_example_MyTest.testMethod.png`.
- If you choose `testClassAndMethod`, the file name will be `MyTest.testMethod.png`.

```
roborazzi.record.namingStrategy=testClassAndMethod
```

## roborazzi.cleanupOldScreenshots

This option allows you to clean up old screenshots. By default, this option is set to false.
The reason why Roborazzi does not delete old screenshots by default is that Roborazzi doesn't know if you are running filtered tests or not. If you are running filtered tests, Roborazzi will delete the screenshots that are not related to the current test run.

```
roborazzi.cleanupOldScreenshots=true
```

## Robolectric Options

### robolectric.pixelCopyRenderMode

I recommend setting `it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"` in your `build.gradle` file to enhance the accuracy of your screenshots. For more details, please refer to the [issue](https://github.com/takahirom/roborazzi/issues/296).

```
android {
    testOptions {
    ...
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
            }
```