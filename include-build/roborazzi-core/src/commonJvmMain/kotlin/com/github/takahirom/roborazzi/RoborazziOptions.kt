package com.github.takahirom.roborazzi

/**
 * Specify the naming strategy for the recorded image.
 * Default: roborazzi.record.namingStrategy=testPackageAndClassAndMethod
 * If set to testPackageAndClassAndMethod, the file name will be com.example.MyTest.testMethod.png
 * If set to escapedTestPackageAndClassAndMethod, the file name will be com_example_MyTest.testMethod.png
 * If set to testClassAndMethod, the file name will be MyTest.testMethod.png
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
