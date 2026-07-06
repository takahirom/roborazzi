@file:OptIn(ExperimentalRoborazziApi::class)

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi

plugins {
  id("com.android.application")
//  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("io.github.takahirom.roborazzi")
}

// Execute ./gradlew :sample-generate-preview-tests:recordRoborazziDebug --rerun-tasks
roborazzi {
  generateComposePreviewRobolectricTests {
    enable = true
    packages = listOf("com.github.takahirom.preview.tests")
    // Use the Compose Testing v2 rule for the generated tests.
    // The annotation filter (RoboPreviewExclude) is applied inside V2CustomPreviewTester
    // because the plugin does not allow setting annotationFilter alongside a custom tester.
    testerQualifiedClassName = "com.github.takahirom.preview.tests.V2CustomPreviewTester"
  }
}

repositories {
  mavenCentral()
  google()
}

android {
  namespace = "com.github.takahirom.preview.tests"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    minSdk = 24

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
// For large preview
        it.maxHeapSize = "4096m"
        it.jvmArgs("-noverify")
      }
    }
  }
}

// Pinned to 1.11.0 (stable) to exercise the Compose Testing v2 rule
// (androidx.compose.ui.test.junit4.v2). The rest of the repo is still on 1.7.x.
val composeVersion = "1.11.0"
dependencies {
  implementation(project(":roborazzi-annotations"))
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.ui:ui:$composeVersion")
  implementation("androidx.compose.ui:ui-tooling:$composeVersion")
  implementation("androidx.compose.runtime:runtime:$composeVersion")
  implementation("androidx.compose.foundation:foundation:$composeVersion")

// replaced by dependency substitution
  testImplementation("io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support:0.1.0")
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.composable.preview.scanner)
  testImplementation("androidx.compose.ui:ui-test-junit4:$composeVersion")
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.espresso.core)
}