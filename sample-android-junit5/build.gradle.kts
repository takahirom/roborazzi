plugins {
  id("com.android.library")
  kotlin("android")
  id("io.github.takahirom.roborazzi")
  id("de.mannodermaus.android-junit5")
  id("tech.apter.junit5.jupiter.robolectric-extension-gradle-plugin")
}

val jvmVersion = JavaVersion.VERSION_17

android {
  namespace = "com.github.takahirom.roborazzi.sample"
  compileSdk = 34

  defaultConfig {
    minSdk = 21
    targetSdk = 34

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = jvmVersion
    targetCompatibility = jvmVersion
  }

  kotlinOptions {
    jvmTarget = jvmVersion.toString()
  }

  buildFeatures {
    viewBinding = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(jvmVersion.toString()))
  }
}

junitPlatform {
  configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
}

dependencies {
  testImplementation(project(":roborazzi"))
  testImplementation(project(":roborazzi-junit5"))

  implementation(kotlin("stdlib"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.google.android.material)

  testImplementation(libs.robolectric)
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.androidx.test.espresso.core)
  testRuntimeOnly(libs.junit.jupiter.engine)

  androidTestImplementation(libs.junit.jupiter.api)
  androidTestImplementation(libs.androidx.test.runner)
}
