plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("io.github.takahirom.roborazzi")
}

kotlin {
  androidLibrary {
    namespace = "com.github.takahirom.roborazzi.sample.kmp"
    compileSdk = libs.versions.compileSdk.get().toInt()
    minSdk = libs.versions.minSdk.get().toInt()

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
    getByName("androidHostTest").dependencies {
      implementation(libs.roborazzi)
      implementation(libs.robolectric)
      implementation(libs.junit)
    }
  }
}
