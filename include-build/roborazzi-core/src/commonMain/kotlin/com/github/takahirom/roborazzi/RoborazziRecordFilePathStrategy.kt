package com.github.takahirom.roborazzi

@ExperimentalRoborazziApi
sealed interface RoborazziRecordFilePathStrategy {
  val propertyValue: String

  object RelativePathFromCurrentDirectory : RoborazziRecordFilePathStrategy {
    override val propertyValue: String
      get() = "relativePathFromCurrentDirectory"
  }

  object RelativePathFromRoborazziContextOutputDirectory : RoborazziRecordFilePathStrategy {
    override val propertyValue: String
      get() = "relativePathFromRoborazziContextOutputDirectory"
  }
}
