plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("org.jetbrains.compose")
  id("org.jetbrains.kotlin.plugin.compose")
  id("io.github.takahirom.roborazzi")
}

group = "com.github.takahirom.roborazzi.android.multiplatform.sample"
version = "1.0-SNAPSHOT"

kotlin {
  androidLibrary {
    namespace = "com.github.takahirom.roborazzi.sample.android.multiplatform"
    compileSdk = libs.versions.compileSdk.get().toInt()
    minSdk = libs.versions.minSdk.get().toInt()

    // Enable host-side (unit) tests with Android resources
    withHostTest {
      isIncludeAndroidResources = true
    }

    compilations.configureEach {
      compileTaskProvider.configure {
        compilerOptions {
          jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.javaTarget.get()))
        }
      }
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material)
      implementation(compose.ui)
    }

    androidMain.dependencies {
      implementation(libs.androidx.activity.compose)
    }

    getByName("androidHostTest").dependencies {
      implementation(project(":roborazzi"))
      implementation(project(":roborazzi-compose"))
      implementation(libs.robolectric)
      implementation(libs.androidx.compose.ui.test.junit4)
      implementation(libs.androidx.compose.ui.test.manifest)
      implementation(libs.junit)
    }
  }
}
