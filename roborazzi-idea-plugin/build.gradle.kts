plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm")
  id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "io.github.takahirom.roborazzi"
version = "1.10.0"

repositories {
  mavenCentral()
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
repositories {
  mavenCentral()

  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  intellijPlatform {
    intellijIdeaCommunity("2024.2.2", false)
    bundledPlugin("com.intellij.java")
    bundledPlugin("org.jetbrains.kotlin")
    bundledPlugin("com.intellij.gradle")

    instrumentationTools()
  }
}

tasks {
  patchPluginXml {
    sinceBuild.set("232")
  }
}
