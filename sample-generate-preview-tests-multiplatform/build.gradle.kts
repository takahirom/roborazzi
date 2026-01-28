import com.github.takahirom.roborazzi.ExperimentalRoborazziApi

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  // AGP 9.0: Use com.android.kotlin.multiplatform.library instead of com.android.application
  id("com.android.kotlin.multiplatform.library")
  id("io.github.takahirom.roborazzi")
  id("org.jetbrains.compose")
  id("org.jetbrains.kotlin.plugin.compose")
}

@OptIn(ExperimentalRoborazziApi::class)
roborazzi {
  generateComposePreviewRobolectricTests {
    enable = true
    packages = listOf("com.github.takahirom.preview.tests")
    testerQualifiedClassName = "com.github.takahirom.preview.tests.MultiplatformPreviewTester"
  }
}

repositories {
  mavenCentral()
  google()
}

kotlin {
  androidLibrary {
    namespace = "com.github.takahirom.preview.tests"
    compileSdk = libs.versions.compileSdk.get().toInt()
    minSdk = 24

    withHostTest {
      isIncludeAndroidResources = true
    }
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(compose.components.uiToolingPreview)
      }
    }
    val androidMain by getting {
      dependencies {
        implementation(compose.material3)
        implementation(compose.ui)
        implementation(compose.uiTooling)
        implementation(compose.runtime)
      }
    }

    val androidHostTest by getting {
      dependencies {
        // replaced by dependency substitution
        implementation("io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support:0.1.0")
        implementation(project(":roborazzi-compose"))
        implementation(project(":roborazzi-annotations"))
        implementation(libs.junit)
        implementation(libs.robolectric)
        implementation(libs.composable.preview.scanner.common)
        implementation(libs.composable.preview.scanner)
        implementation(libs.androidx.compose.ui.test.junit4)
      }
    }
    val androidDebug by creating {
      dependencies {
        implementation(libs.androidx.compose.ui.test.manifest)
      }
    }
  }
}
