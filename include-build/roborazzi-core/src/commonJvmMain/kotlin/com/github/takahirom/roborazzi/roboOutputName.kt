package com.github.takahirom.roborazzi

/**
 * You can utilize this function to generate the file name of the image to be recorded.
 * You can change naming strategy by setting roborazzi.record.namingStrategy.
 */
@ExperimentalRoborazziApi
fun roboOutputName(): String {
  val roborazziContext = provideRoborazziContext()
  val description = roborazziContext.description
  if (description != null) {
    return DefaultFileNameGenerator.generateOutputNameWithDescription(description)
  }
  return DefaultFileNameGenerator.generateOutputNameWithStackTrace()
}