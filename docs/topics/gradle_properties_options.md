# Roborazzi gradle.properties Options

You can configure the following options in your `gradle.properties` file:

### roborazzi.test

This option enables you to configure the behavior of Roborazzi. By default, all settings are set to false.
For additional configuration options, please refer to the 'Apply Roborazzi Gradle Plugin' section.

```
roborazzi.test.record=true
# roborazzi.test.compare=true
# roborazzi.test.verify=true
```

### roborazzi.record

#### roborazzi.record.resizeScale

This option lets you set the resize scale for the image being recorded. The default value is 1.0.

```
roborazzi.record.resizeScale=0.5
```

#### roborazzi.record.filePathStrategy

This setting allows you to specify the file path strategy for the recorded image. The default strategy is `relativePathFromCurrentDirectory`. If you choose `relativePathFromRoborazziContextOutputDirectory`, the file will be saved in the output directory specified by `RoborazziRule.Options.outputDirectoryPath`.

```
roborazzi.record.filePathStrategy=relativePathFromRoborazziContextOutputDirectory
```

#### roborazzi.record.namingStrategy

This option enables you to define the naming strategy for the recorded image. The default strategy is `testPackageAndClassAndMethod`.

- If you choose `testPackageAndClassAndMethod`, the file name will be `com.example.MyTest.testMethod.png`.
- If you choose `escapedTestPackageAndClassAndMethod`, the file name will be `com_example_MyTest.testMethod.png`.
- If you choose `testClassAndMethod`, the file name will be `MyTest.testMethod.png`.

```
roborazzi.record.namingStrategy=testClassAndMethod
```
