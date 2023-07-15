pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "integration-test-project"
include(":app")

val roborazziRootPath = java.lang.System.getenv("ROBORAZZI_ROOT_PATH") ?: "../../../../.."
includeBuild(roborazziRootPath) {
  dependencySubstitution {
    substitute(module("io.github.takahirom.roborazzi:roborazzi")).using(project(":roborazzi"))
  }
}

val roborazziIncludeBuildRootPath =
  java.lang.System.getenv("ROBORAZZI_INCLUDE_BUILD_ROOT_PATH") ?: "../../../.."
includeBuild(roborazziIncludeBuildRootPath) {
  dependencySubstitution {
    substitute(module("io.github.takahirom.roborazzi:roborazzi-gradle-plugin")).using(project(":roborazzi-gradle-plugin"))
  }
}
 