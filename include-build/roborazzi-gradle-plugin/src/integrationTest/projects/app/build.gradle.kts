@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("io.github.takahirom.roborazzi")
}

android {
  namespace = "com.github.takahirom.integration_test_project"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "com.github.takahirom.integration_test_project"
    minSdk = 21
    targetSdk = 33
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "com.github.takahirom.integration_test_project.CustomRoborazziSampleTestRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
    unitTests.all {
      testLogging {
        events "skipped", "failed"
        showStandardStreams = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
      }
    }
  }
}

dependencies {
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.rule)
  testImplementation(libs.robolectric)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.compose.material3)
  testImplementation(libs.junit)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.androidx.test.espresso.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}