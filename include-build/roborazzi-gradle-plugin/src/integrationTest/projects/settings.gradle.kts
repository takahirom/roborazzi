val roborazziRootPath = java.lang.System.getenv("ROBORAZZI_ROOT_PATH") ?: "../../../../.."
pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}
dependencyResolutionManagement {
  versionCatalogs {
    create("libs") {
      from(files("$roborazziRootPath/gradle/libs.versions.toml"))
    }
  }
  repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "integration-test-project"
include(":app")
include(":sample-generate-preview-tests")

includeBuild(roborazziRootPath) {
  dependencySubstitution {
    substitute(module("io.github.takahirom.roborazzi:roborazzi")).using(project(":roborazzi"))
    substitute(module("io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support")).using(project(":roborazzi-compose-preview-scanner-support"))
    substitute(module("io.github.takahirom.roborazzi:roborazzi-junit-rule")).using(project(":roborazzi-junit-rule"))
  }
}

val roborazziIncludeBuildRootPath =
  java.lang.System.getenv("ROBORAZZI_INCLUDE_BUILD_ROOT_PATH") ?: "../../../.."
includeBuild(roborazziIncludeBuildRootPath) {
  dependencySubstitution {
    substitute(module("io.github.takahirom.roborazzi:roborazzi-gradle-plugin")).using(project(":roborazzi-gradle-plugin"))
  }
}
