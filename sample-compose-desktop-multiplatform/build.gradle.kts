import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("multiplatform")
  id("org.jetbrains.compose")
  id("io.github.takahirom.roborazzi")
}

group = "com.github.takahirom.roborazzi.compose.desktop.kmp.sample"
version = "1.0-SNAPSHOT"

kotlin {
  jvm("desktop")
  sourceSets {
    val desktopMain by getting {
      dependencies {
        // Note, if you develop a library, you should use compose.desktop.common.
        // compose.desktop.currentOs should be used in launcher-sourceSet
        // (in a separate module for demo project and in testMain).
        // With compose.desktop.common you will also lose @Preview functionality
        implementation(compose.desktop.currentOs)
      }
    }

    val desktopTest by getting {
      dependencies {
        implementation(project(":roborazzi-compose-desktop"))
        implementation(kotlin("test"))
      }
    }
  }
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
    freeCompilerArgs += "-Xcontext-receivers"
  }
}