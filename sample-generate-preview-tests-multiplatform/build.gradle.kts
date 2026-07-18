import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.application")
//  id("com.android.library")
  id("io.github.takahirom.roborazzi")
  id("org.jetbrains.compose")
  id("org.jetbrains.kotlin.plugin.compose")
}

// Benchmark: force the latest Robolectric beta.
configurations.all {
  resolutionStrategy.eachDependency {
    val independentlyVersioned = setOf("android-all", "android-all-instrumented", "nativeruntime-dist-compat")
    if (requested.group == "org.robolectric" && requested.name !in independentlyVersioned) {
      useVersion("4.17-beta-2")
      because("Benchmark against the latest Robolectric beta")
    }
  }
}

@OptIn(ExperimentalRoborazziApi::class)
roborazzi {
  // Both preview generators are enabled in this module, so separate the outputs.
  separateOutputDirs.set(true)
  generateComposePreviewRobolectricTests {
    enable = true
    packages = listOf("com.github.takahirom.preview.tests")
  }
  generateComposePreviewDesktopTests {
    enable = true
    packages = listOf("com.github.takahirom.preview.tests")
  }
}

repositories {
  mavenCentral()
  google()
}

// ComposablePreviewScanner publishes JVM 17 metadata; raise only the desktop side.
afterEvaluate {
  listOf("desktopTestCompileClasspath", "desktopTestRuntimeClasspath").forEach { name ->
    configurations.named(name) {
      attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
    }
  }
}
tasks.withType<KotlinCompile>().matching { it.name.contains("Desktop") }.configureEach {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
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

kotlin {
  androidTarget()
  jvm("desktop")

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(compose.components.uiToolingPreview)
        implementation(compose.material3)
        implementation(compose.runtime)
      }
    }
    val androidMain by getting {
      dependencies {
        implementation(compose.ui)
        implementation(compose.uiTooling)
      }
    }

    val androidUnitTest by getting {
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

    val androidInstrumentedTest by getting {
      dependencies {
        implementation(libs.androidx.test.ext.junit)
        implementation(libs.androidx.test.espresso.core)
      }
    }

    val desktopMain by getting {
      dependencies {
        implementation(compose.desktop.currentOs)
      }
    }

    val desktopTest by getting {
      dependencies {
        implementation(project(":roborazzi-compose-desktop-preview-scanner-support"))
        implementation(libs.junit)
        implementation(libs.composable.preview.scanner)
      }
    }
  }
}
