/**
 * This file is overwritten by the IntegrationTest.
 */

plugins {
  id("com.android.application")
//  id("com.android.library")
  // AGP 9.0: org.jetbrains.kotlin.android is no longer needed (built-in Kotlin)
  id("io.github.takahirom.roborazzi")
}

roborazzi {
  generateComposePreviewRobolectricTests {
    enable = true
    packages = listOf("com.github.takahirom.preview.tests")
  }
}

// Replace AGP's default Compose Compiler with Kotlin's integrated version
configurations.all {
  resolutionStrategy.dependencySubstitution {
    substitute(module("androidx.compose.compiler:compiler"))
      .using(module("org.jetbrains.kotlin:kotlin-compose-compiler-plugin-embeddable:2.2.10"))
      .because("Compose Compiler is now shipped as part of Kotlin distribution")
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
      }
    }
  }
}

dependencies {
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.compose.runtime)

  // replaced by dependency substitution
  testImplementation("io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support:0.1.0")
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.composable.preview.scanner)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.espresso.core)
}