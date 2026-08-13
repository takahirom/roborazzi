package com.github.takahirom.roborazzi

internal actual fun roborazziIsSubdirectoryNamingStrategy(): Boolean {
  return when (roborazziDefaultNamingStrategy()) {
    DefaultFileNameGenerator.DefaultNamingStrategy.TestPackageDirAndClassAndMethod,
    DefaultFileNameGenerator.DefaultNamingStrategy.TestNestedPackageDirAndClassAndMethod -> true

    else -> false
  }
}

@OptIn(ExperimentalRoborazziApi::class)
internal actual fun roborazziContextOutputDirectory(): String {
  return provideRoborazziContext().outputDirectory
}
