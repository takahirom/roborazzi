package io.github.takahirom.roborazzi

import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.rules.TemporaryFolder

class RoborazziGradleProject(val testProjectDir: TemporaryFolder) {
  init {
    File("./src/integrationTest/projects").copyRecursively(testProjectDir.root, true)
  }

  fun record(): BuildResult {
    val task = "recordRoborazziDebug"
    return runTask(task)
  }

  fun unitTest(): BuildResult {
    val task = "testDebugUnitTest"
    return runTask(task)
  }

  fun recordWithScaleSize(): BuildResult {
    val task = "recordRoborazziDebug"
    return runTask(task, additionalParameters = arrayOf("-Proborazzi.record.resizeScale=0.5"))
  }

  fun recordWithCompareParameter(): BuildResult {
    val task = "recordRoborazziDebug"
    return runTask(task, additionalParameters = arrayOf("-Proborazzi.test.compare=true"))
  }

  fun recordWithSystemParameter(): BuildResult {
    val task = "testDebugUnitTest"
    return runTask(task, additionalParameters = arrayOf("-Proborazzi.test.record=true"))
  }

  fun verify(): BuildResult {
    val task = "verifyRoborazziDebug"
    return runTask(task)
  }

  fun verifyAndFail(): BuildResult {
    val task = "verifyRoborazziDebug"
    return runTask(task, BuildType.BuildAndFail)
  }

  fun verifyAndRecord(): BuildResult {
    val task = "verifyAndRecordRoborazziDebug"
    return runTask(task)
  }

  fun verifyAndRecordAndFail(): BuildResult {
    val task = "verifyAndRecordRoborazziDebug"
    return runTask(task, BuildType.BuildAndFail)
  }


  fun compare(): BuildResult {
    val task = "compareRoborazziDebug"
    return runTask(task)
  }

  enum class BuildType {
    Build, BuildAndFail
  }

  private fun runTask(
    task: String,
    buildType: BuildType = BuildType.Build,
    additionalParameters: Array<String> = arrayOf()
  ): BuildResult {
    appBuildFile.addIncludeBuild()

    val buildResult = GradleRunner.create()
      .withProjectDir(testProjectDir.root)
      .withArguments(
        task,
        "--stacktrace",
        "--info",
        *additionalParameters
      )
      .withPluginClasspath()
      .forwardStdOutput(System.out.writer())
      .forwardStdError(System.err.writer())
      .withEnvironment(
        mapOf(
          "ANDROID_HOME" to System.getenv("ANDROID_HOME"),
          "INTEGRATION_TEST" to "true",
          "ROBORAZZI_ROOT_PATH" to File("../..").absolutePath,
          "ROBORAZZI_INCLUDE_BUILD_ROOT_PATH" to File("..").absolutePath,
        )
      )
      .let {
        if (buildType == BuildType.BuildAndFail) {
          it.buildAndFail()
        } else {
          it.build()
        }
      }
    println(testProjectDir.root.resolve("app/output/roborazzi/").listFiles())
    return buildResult
  }

  class AppBuildFile(private val folder: TemporaryFolder) {
    private val PATH = "app/build.gradle.kts"

    fun addIncludeBuild() {
      folder.root.resolve(PATH).delete()
      val buildFile = folder.newFile(PATH)
      buildFile.writeText(
        """
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
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions {
    jvmTarget = "1.8"
  }
  buildFeatures {
    compose = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = "1.3.2"
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
//  packaging {
//    resources {
//      excludes += "/META-INF/{AL2.0,LGPL2.1}"
//    }
//  }
}

dependencies {
  implementation("io.github.takahirom.roborazzi:roborazzi:1.0.0")
  testImplementation("org.robolectric:robolectric:4.10.3")

  implementation(libs.core.ktx)
  implementation(libs.lifecycle.runtime.ktx)
  implementation(libs.activity.compose)
  implementation(platform(libs.compose.bom))
  implementation(libs.ui)
  implementation(libs.ui.graphics)
  implementation(libs.ui.tooling.preview)
  implementation(libs.material3)
  testImplementation(libs.junit)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.espresso.core)
  testImplementation(platform(libs.compose.bom))
  testImplementation(libs.ui.test.junit4)
  debugImplementation(libs.ui.tooling)
  debugImplementation(libs.ui.test.manifest)
}
          """
      )
    }
  }

  fun checkCompareFileNotExists() {
    val recordedFile =
      testProjectDir.root.resolve("app/build/test-results/roborazzi/compare-report.json")
    assert(!recordedFile.exists()) {
      "File exists: ${recordedFile.absolutePath}"
    }
  }

  fun checkCompareFileExists() {
    val recordedFile =
      testProjectDir.root.resolve("app/build/test-results/roborazzi/compare-report.json")
    assert(recordedFile.exists()) {
      "File not exists: ${recordedFile.absolutePath}"
    }
  }

  fun checkRecordedFileExists(path: String) {
    val recordedFile = testProjectDir.root.resolve(path)
    assert(recordedFile.exists()) {
      "File not exists: $path"
    }
  }

  fun getFileHash(path: String): Int {
    val recordedFile = testProjectDir.root.resolve(path)
    return recordedFile.readBytes().contentHashCode()
  }


  fun checkRecordedFileNotExists(path: String) {
    val recordedFile = testProjectDir.root.resolve(path)
    assert(!recordedFile.exists()) {
      "File exists: $path"
    }
  }

  fun changeScreen() {
    val file =
      testProjectDir.root.resolve("app/src/main/java/com/github/takahirom/integration_test_project/Greeting.kt")
    file.writeText(
      """package com.github.takahirom.integration_test_project

    import androidx.compose.material3.MaterialTheme
      import androidx.compose.material3.Text
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Greeting(name: String, modifier: Modifier = Modifier) {
        Text(
          text = "This screen has been changed ${'$'}name!",
          style = MaterialTheme.typography.headlineLarge,
          modifier = modifier
        )
      }"""
    )
  }

  val appBuildFile = AppBuildFile(testProjectDir)
}