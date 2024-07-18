plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm")
  id("org.jetbrains.intellij") version "1.17.2"
}

group = "io.github.takahirom.roborazzi"
version = "1.3.0"

repositories {
  mavenCentral()
}

dependencies {
  // Replaced by dependency substitution
  implementation("io.github.takahirom.roborazzi:roborazzi-core:0.0.1")
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
  updateSinceUntilBuild = false

  version.set("2023.2.5")
  type.set("IC") // Target IDE Platform

  plugins.set(listOf(
    "java",
    "Kotlin",
    "com.intellij.gradle",
  ))
}

tasks {
  patchPluginXml {
    sinceBuild.set("232")
  }
}
