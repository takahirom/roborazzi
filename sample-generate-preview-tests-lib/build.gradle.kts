plugins {
  kotlin("multiplatform")
  id("com.android.library")
  id("org.jetbrains.compose")
  id("io.github.takahirom.roborazzi")
}

kotlin {
  iosSimulatorArm64()
  androidTarget()

  sourceSets {
    val androidMain by getting {
      dependencies {
        implementation(compose.material3)
        implementation(compose.ui)
        implementation(compose.uiTooling)
        implementation(compose.runtime)
      }
    }

    val androidUnitTest by getting {
      dependencies {
        implementation("io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support:0.1.0")
        implementation(libs.junit)
        implementation(libs.robolectric)
        implementation(libs.composable.preview.scanner)
      }
    }

    val androidInstrumentedTest by getting {
      dependencies {
        implementation(libs.androidx.test.ext.junit)
        implementation(libs.androidx.test.espresso.core)
      }
    }
  }
}

android {
  namespace = "com.github.takahirom.preview.tests.lib"
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

roborazzi {
  generateComposePreviewRobolectricTests {
    enable = true
    packages = listOf("com.github.takahirom.preview.tests")
  }
}

repositories {
  mavenCentral()
  google()
  maven { url = uri("https://jitpack.io") }
}