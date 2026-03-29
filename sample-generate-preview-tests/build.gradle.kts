plugins {
  id("com.android.application")
//  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("io.github.takahirom.roborazzi")
}

roborazzi {
  generateComposePreviewRobolectricTests {
    enable = true
    packages = listOf("com.github.takahirom.preview.tests")
    testerQualifiedClassName = "com.github.takahirom.preview.tests.V2CustomPreviewTester"
  }
}

repositories {
  mavenCentral()
  google()
}

android {
  namespace = "com.github.takahirom.preview.tests"
  compileSdk = 35

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
  // Disable allWarningsAsErrors for compose test v2 (v1 APIs are deprecated in 1.11.0+)
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
      allWarningsAsErrors.set(false)
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

val composeVersion = "1.11.0-beta02"
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