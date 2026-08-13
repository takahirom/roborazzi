package com.github.takahirom.roborazzi

/**
 * Specify the file path strategy for the recorded image.
 * Default: roborazzi.record.filePathStrategy=relativePathFromCurrentDirectory
 * If set to relativePathFromCurrentDirectory, the image will be saved in the relative path from the current directory.
 * If set to relativePathFromRoborazziContextOutputDirectory, the image will be saved in the relative path from the output directory of the Roborazzi context.
 */
@ExperimentalRoborazziApi
fun roborazziRecordFilePathStrategy(): RoborazziRecordFilePathStrategy {
  return when (
    getSystemProperty(
      "roborazzi.record.filePathStrategy",
      RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory.propertyValue
    )
  ) {
    RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory.propertyValue ->
      RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory

    RoborazziRecordFilePathStrategy.RelativePathFromRoborazziContextOutputDirectory.propertyValue ->
      RoborazziRecordFilePathStrategy.RelativePathFromRoborazziContextOutputDirectory

    else -> RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory
  }
}

/**
 * You can specify the naming strategy of the image to be recorded.
 * The default is roborazzi.record.namingStrategy=testPackageAndClassAndMethod
 * If you specify testPackageAndClassAndMethod, the file name will be com.example.MyTest.testMethod.png
 * If you specify escapedTestPackageAndClassAndMethod, the file name will be com_example_MyTest.testMethod.png
 * If you specify testClassAndMethod, the file name will be MyTest.testMethod.png
 * If you specify testPackageDirAndClassAndMethod, the file will be saved as com.example/MyTest.testMethod.png (package as a single directory)
 * If you specify testNestedPackageDirAndClassAndMethod, the file will be saved as com/example/MyTest.testMethod.png (package as nested directories)
 */
fun roborazziDefaultNamingStrategy(): DefaultFileNameGenerator.DefaultNamingStrategy {
  return DefaultFileNameGenerator.DefaultNamingStrategy
    .fromOptionName(
      optionName = checkNotNull(
        getSystemProperty(
          "roborazzi.record.namingStrategy",
          DefaultFileNameGenerator.DefaultNamingStrategy.TestPackageAndClassAndMethod.optionName
        )
      )
    )
}
