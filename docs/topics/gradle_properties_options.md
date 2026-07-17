# Roborazzi gradle.properties Options and Recommendations

You can configure the following options in your `gradle.properties` file.
You can also use `-P` to set the options in the command line. For example, `./gradlew test -Proborazzi.test.record=true`.

## roborazzi.test

This option enables you to configure the behavior of Roborazzi. By default, all settings are set to false.
For the tasks these properties correspond to, see [Build setup](https://takahirom.github.io/roborazzi/build-setup.html).

```properties
roborazzi.test.record=true
# roborazzi.test.compare=true
# roborazzi.test.verify=true
```

## roborazzi.record

### roborazzi.record.resizeScale

This option lets you set the resize scale for the image being recorded. The default value is 1.0.

```properties
roborazzi.record.resizeScale=0.5
```

### roborazzi.record.filePathStrategy

This setting allows you to specify the file path strategy for the recorded image. The default strategy is `relativePathFromCurrentDirectory`. If you choose `relativePathFromRoborazziContextOutputDirectory`, the file will be saved in the output directory specified by `roborazzi.outputDir`.

```properties
roborazzi.record.filePathStrategy=relativePathFromRoborazziContextOutputDirectory
```

### roborazzi.record.namingStrategy

This option enables you to define the naming strategy for the recorded image. The default strategy is `testPackageAndClassAndMethod`.

- If you choose `testPackageAndClassAndMethod`, the file name will be `com.example.MyTest.testMethod.png`.
- If you choose `escapedTestPackageAndClassAndMethod`, the file name will be `com_example_MyTest.testMethod.png`.
- If you choose `testClassAndMethod`, the file name will be `MyTest.testMethod.png`.

```properties
roborazzi.record.namingStrategy=testClassAndMethod
```

## roborazzi.cleanupOldScreenshots

This option allows you to clean up old screenshots. By default, this option is set to false.
The reason why Roborazzi does not delete old screenshots by default is that Roborazzi doesn't know if you are running filtered tests or not. If you are running filtered tests, Roborazzi will delete the screenshots that are not related to the current test run.

```properties
roborazzi.cleanupOldScreenshots=true
```

## roborazzi.dumpUiTree

This option enables the [UI tree dump (JSON)](https://takahirom.github.io/roborazzi/ui-tree-dump.html).
When set to `true`, each captured screenshot gets a machine-readable
`.uitree.json` sidecar written next to the image it describes
(`MyTest.uitree.json` on record, `MyTest_actual.uitree.json` on compare/verify).
By default this option is set to false. The sidecar is informational only: it
never participates in image diffing and never fails verification.

```properties
roborazzi.dumpUiTree=true
```

## Robolectric Options

### robolectric.pixelCopyRenderMode

I recommend setting `it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"` in your `build.gradle` file to enhance the accuracy of your screenshots. For more details, please refer to the [issue](https://github.com/takahirom/roborazzi/issues/296).

```kotlin
android {
    testOptions {
    ...
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
            }
        }
    }
}
```
