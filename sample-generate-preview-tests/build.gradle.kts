plugins {
  id("com.android.application")
//  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("io.github.takahirom.roborazzi")
}

roborazzi {
  generateRobolectricPreviewTests {
    enable = true
    packages = listOf("com.github.takahirom.preview.tests")
  }
}

repositories {
  mavenCentral()
  google()
  maven { url = uri("https://jitpack.io") }
}

android {
  namespace = "com.github.takahirom.preview.tests"
  compileSdk = 34

  defaultConfig {
    minSdk = 24

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  buildFeatures {
    compose = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
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

  testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.20.0")
  testImplementation("io.github.takahirom.roborazzi:roborazzi:1.20.0")
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  testImplementation("com.github.sergio-sastre.ComposablePreviewScanner:android:0.1.2")
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.espresso.core)
}