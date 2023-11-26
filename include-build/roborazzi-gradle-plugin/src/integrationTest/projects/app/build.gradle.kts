@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("io.github.takahirom.roborazzi")
}

android {
  namespace = "com.github.takahirom.integration_test_project"
  compileSdk = 33

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
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
  }
  buildFeatures {
//    compose = true
  }
//  composeOptions {
//    kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
//  }
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
//  packaging {
//    resources {
//      excludes += "/META-INF/{AL2.0,LGPL2.1}"
//    }
//  }
}

dependencies {
  testImplementation("io.github.takahirom.roborazzi:roborazzi:1.0.0")
  testImplementation("io.github.takahirom.roborazzi:roborazzi-rule:1.0.0")
  testImplementation("org.robolectric:robolectric:4.10.3")

  implementation(libs.core.ktx)
  implementation(libs.lifecycle.runtime.ktx)
//  implementation(libs.activity.compose)
//  implementation(platform(libs.compose.bom))
//  implementation(libs.ui)
//  implementation(libs.ui.graphics)
//  implementation(libs.ui.tooling.preview)
  implementation(libs.material3)
  testImplementation(libs.junit)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.espresso.core)
//  testImplementation(platform(libs.compose.bom))
  testImplementation(libs.ui.test.junit4)
  debugImplementation(libs.ui.tooling)
  debugImplementation(libs.ui.test.manifest)
}