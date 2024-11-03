import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  id("org.jetbrains.compose")
  id("io.github.takahirom.roborazzi")
}

group = "com.github.takahirom.roborazzi.compose.desktop.jvm.sample"
version = "1.0-SNAPSHOT"
tasks.test {
  useJUnitPlatform()
}

dependencies {
  // Note, if you develop a library, you should use compose.desktop.common.
  // compose.desktop.currentOs should be used in launcher-sourceSet
  // (in a separate module for demo project and in testMain).
  // With compose.desktop.common you will also lose @Preview functionality
  implementation(compose.desktop.currentOs)
  testImplementation(project(":roborazzi-compose-desktop"))

  testImplementation(kotlin("test"))
}

compose.desktop {
  application {
    mainClass = "MainKt"

    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
      packageName = "app-compose-desktop"
      packageVersion = "1.0.0"
    }
  }
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    incremental = false
  }
}