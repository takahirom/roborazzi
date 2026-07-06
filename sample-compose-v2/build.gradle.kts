plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("io.github.takahirom.roborazzi")
}

// Execute ./gradlew :sample-compose-v2:recordRoborazziDebug
roborazzi {
  generateComposePreviewRobolectricTests {
    enable = true
    packages = listOf("com.github.takahirom.sample.composev2")
    // Drive the generated tests through the Compose Testing v2 rule.
    testerQualifiedClassName = "com.github.takahirom.sample.composev2.V2CustomPreviewTester"
  }
}

repositories {
  mavenCentral()
  google()
}

android {
  namespace = "com.github.takahirom.sample.composev2"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    // androidx.compose.ui:ui-test-junit4:1.11.0 requires minSdk 23,
    // higher than the repo-wide catalog minSdk (21).
    minSdk = 23
    targetSdk = libs.versions.targetSdk.get().toInt()

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
        it.maxHeapSize = "4096m"
        it.jvmArgs("-noverify")
      }
    }
  }
}

// This module exists to exercise the Compose Testing v2 APIs
// (androidx.compose.ui.test.junit4.v2), which require Compose 1.11.0.
// The rest of the repo is still on the catalog Compose version,
// so the pins are isolated here to keep other modules' screenshots stable.
val composeVersion = "1.11.0"
dependencies {
  implementation("androidx.compose.ui:ui:$composeVersion")
  implementation("androidx.compose.ui:ui-tooling:$composeVersion")
  implementation("androidx.compose.runtime:runtime:$composeVersion")
  implementation("androidx.compose.foundation:foundation:$composeVersion")

  testImplementation(project(":roborazzi"))
  testImplementation(project(":roborazzi-compose"))
  testImplementation(project(":roborazzi-junit-rule"))
  // replaced by dependency substitution
  testImplementation("io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support:0.1.0")
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.composable.preview.scanner)
  testImplementation(libs.androidx.test.ext.junit.ktx)
  testImplementation("androidx.compose.ui:ui-test-junit4:$composeVersion")
  debugImplementation("androidx.compose.ui:ui-test-manifest:$composeVersion")
}
