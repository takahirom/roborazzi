package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.CaptureResults
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.rules.TemporaryFolder
import java.io.File

class RoborazziGradleProject(val testProjectDir: TemporaryFolder) {
  init {
    File("./src/integrationTest/projects").copyRecursively(testProjectDir.root, true)
  }

  fun record(): BuildResult {
    val task = "recordRoborazziDebug"
    return runTask(task)
  }

  fun recordWithFilter1(): BuildResult {
    val task = "recordRoborazziDebug"
    return runTask(
      task,
      additionalParameters = arrayOf(
        "--tests",
        "com.github.takahirom.integration_test_project.RoborazziTest.testCapture1"
      )
    )
  }

  fun recordWithFilter2(): BuildResult {
    val task = "recordRoborazziDebug"
    return runTask(
      task,
      additionalParameters = arrayOf(
        "--tests",
        "com.github.takahirom.integration_test_project.RoborazziTest.testCapture2"
      )
    )
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

  fun BuildResult.shouldDetectChangedPngCapture() {
    assert(output.contains("png is changed"))
  }

  fun BuildResult.shouldDetectNonExistentPngCapture() {
    assert(output.contains(".png) was not found."))
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

  fun compareWithSystemParameter(): BuildResult {
    val task = "testDebugUnitTest"
    return runTask(task, additionalParameters = arrayOf("-Proborazzi.test.compare=true"))
  }

  fun removeRoborazziOutputDir() {
    File(testProjectDir.root, "app/build/outputs/roborazzi").deleteRecursively()
  }

  fun removeRoborazziAndIntermediateOutputDir() {
    File(testProjectDir.root, "app/build/outputs/roborazzi").deleteRecursively()
    File(testProjectDir.root, "app/build/intermediates/roborazzi").deleteRecursively()
  }

  fun assertNotSkipped(output: String) {
    assert(output.contains("testDebugUnitTest' is not up-to-date because"))
  }


  fun assertSkipped(output: String) {
    assert(output.contains("testDebugUnitTest UP-TO-DATE"))
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
        "--build-cache",
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
    println(
      "app/output/roborazzi/ list files:" + testProjectDir.root.resolve("app/output/roborazzi/")
        .listFiles()
    )
    return buildResult
  }

  class AppBuildFile(private val folder: TemporaryFolder) {
    private val PATH = "app/build.gradle.kts"
    var removeOutputDirBeforeTestTypeTask = false

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
    kotlinCompilerExtensionVersion = "1.4.8"
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
  implementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.0.0")
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
      if (removeOutputDirBeforeTestTypeTask) {
        buildFile.appendText(
          """
            tasks.withType<Test> {
              doLast {
                delete("build/outputs/roborazzi")
              }
            }
          """.trimIndent()
        )
      }
    }
  }

  fun checkResultFileExists(nameSuffix: String) {
    testProjectDir.root.resolve("app/build/test-results/roborazzi/results/").listFiles()
      .firstOrNull { it.name.endsWith(nameSuffix) }
      ?: error("File not found: $nameSuffix")
  }

  fun checkResultFileNotExists(nameSuffix: String) {
    testProjectDir.root.resolve("app/build/test-results/roborazzi/results/").listFiles()
      ?.firstOrNull { it.name.endsWith(nameSuffix) }
      ?.let {
        error("File exists: $nameSuffix")
      }
  }


  fun checkResultsSummaryFileNotExists() {
    val recordedFile =
      testProjectDir.root.resolve("app/build/test-results/roborazzi/results-summary.json")
    assert(!recordedFile.exists()) {
      "File exists: ${recordedFile.absolutePath}"
    }
  }

  fun checkResultsSummaryFileExists() {
    val recordedFile =
      testProjectDir.root.resolve("app/build/test-results/roborazzi/results-summary.json")
    println("mytest:" + recordedFile.readText())
    assert(recordedFile.exists()) {
      "File not exists: ${recordedFile.absolutePath}"
    }
  }

  fun checkResultCount(count: Int) {
    val recordedFile =
      testProjectDir.root.resolve("app/build/test-results/roborazzi/results-summary.json")
    val resutls = CaptureResults.fromJsonFile(recordedFile.absolutePath)
    assert(resutls.captureResults.size == count) {
      "Expected count: $count, actual count: ${resutls.captureResults.size}"
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

  fun addTest() {
    val originalFileText =
      testProjectDir.root.resolve("app/src/test/java/com/github/takahirom/integration_test_project/RoborazziTest.kt")
        .readText()
    val file =
      testProjectDir.root.resolve("app/src/test/java/com/github/takahirom/integration_test_project/AddedRoborazziTest.kt")
    file.writeText(originalFileText.replace("RoborazziTest", "AddedRoborazziTest"))
  }

  fun removeTests() {
    val file =
      testProjectDir.root.resolve("app/src/test/java/com/github/takahirom/integration_test_project")
    file.deleteRecursively()
  }

  fun addTestClass() {
    val file =
      testProjectDir.root.resolve("app/src/test/java/com/github/takahirom/integration_test_project/AddedClass.kt")
    file.parentFile.mkdirs()
    file.writeText(
      """
      |package com.github.takahirom.integration_test_project
      |import org.junit.Test
      |class AddedClass {
      |  @Test
      |  fun test() {
      |    println("test")
      |  }
      |}
    """.trimMargin()
    )
  }

  fun addRelativeFromContextRecordFilePathStrategyGradleProperty() {
    val file = testProjectDir.root.resolve("gradle.properties")
    file.appendText("\nroborazzi.record.filePathStrategy=relativePathFromRoborazziContextOutputDirectory")
  }

  fun addRuleTest() {
    val file =
      testProjectDir.root.resolve("app/src/test/java/com/github/takahirom/integration_test_project/RoborazziTest.kt")
    file.parentFile.mkdirs()
    file.writeText(
      """
package com.github.takahirom.integration_test_project

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.*
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import android.content.ComponentName
import org.junit.Test
import org.junit.Rule
import org.robolectric.annotation.GraphicsMode

import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoborazziTest {

  @get:Rule val roborazziRule = RoborazziRule()
  init {
    com.github.takahirom.roborazzi.ROBORAZZI_DEBUG = true
  }
  @Test
  fun testCapture() {
    val appContext: Application = ApplicationProvider.getApplicationContext()
    Shadows.shadowOf(appContext.packageManager).addActivityIfNotPresent(
      ComponentName(
        appContext.packageName,
        MainActivity::class.java.name,
      )
    )
    ActivityScenario.launch(MainActivity::class.java)
    onView(ViewMatchers.isRoot()).captureRoboImage()
    onView(ViewMatchers.isRoot()).captureRoboImage()
  }
}
    """
    )
  }

  fun addTestCaptureWithCustomPathTest() {
    val file =
      testProjectDir.root.resolve("app/src/test/java/com/github/takahirom/integration_test_project/RoborazziTest.kt")
    file.parentFile.mkdirs()
    file.writeText(
      """
package com.github.takahirom.integration_test_project

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.*
import com.github.takahirom.roborazzi.RoborazziRule.CaptureType
import com.github.takahirom.roborazzi.RoborazziRule.Options
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import android.content.ComponentName
import java.io.File
import org.junit.Test
import org.junit.Rule
import org.robolectric.annotation.GraphicsMode

import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoborazziTest {

  @get:Rule
  val roborazziRule = RoborazziRule(
    captureRoot = onView(isRoot()),
    options = Options(
      captureType = RoborazziRule.CaptureType.LastImage(),
      outputDirectoryPath = "${"$"}DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/custom_outputDirectoryPath_from_rule",
      outputFileProvider = { description, directory, fileExtension ->
        File(
          directory,
          "custom_outputFileProvider-${"$"}{description.testClass.name}.${"$"}{description.methodName}.${"$"}fileExtension"
        )
      },
      roborazziOptions = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
          outputDirectoryPath = "${"$"}DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/custom_compare_outputDirectoryPath_from_rule",
        )
      )
    ),
  )
  init {
    com.github.takahirom.roborazzi.ROBORAZZI_DEBUG = true
  }
  
  @Test
  fun testCaptureWithCustomPath() {
    ActivityScenario.launch(MainActivity::class.java)
    onView(ViewMatchers.isRoot()).captureRoboImage(
      filePath = "${"$"}DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/customdir/custom_file.png",
      roborazziOptions = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
          outputDirectoryPath = "${"$"}DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/custom_compare_outputDirectoryPath",
        )
      )
    )
  }
}
    """
    )
  }

  fun addMultipleTest() {
    val file =
      testProjectDir.root.resolve("app/src/test/java/com/github/takahirom/integration_test_project/RoborazziTest.kt")
    file.parentFile.mkdirs()
    file.writeText(
      """
package com.github.takahirom.integration_test_project

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.*
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import android.content.ComponentName
import org.junit.Test
import org.junit.Rule
import org.robolectric.annotation.GraphicsMode

import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoborazziTest {

  @get:Rule val roborazziRule = RoborazziRule()
  init {
    com.github.takahirom.roborazzi.ROBORAZZI_DEBUG = true
  }
  @Test
  fun testCapture1() {
    val appContext: Application = ApplicationProvider.getApplicationContext()
    Shadows.shadowOf(appContext.packageManager).addActivityIfNotPresent(
      ComponentName(
        appContext.packageName,
        MainActivity::class.java.name,
      )
    )
    ActivityScenario.launch(MainActivity::class.java)
    onView(ViewMatchers.isRoot()).captureRoboImage()
  }

  @Test
  fun testCapture2() {
    val appContext: Application = ApplicationProvider.getApplicationContext()
    Shadows.shadowOf(appContext.packageManager).addActivityIfNotPresent(
      ComponentName(
        appContext.packageName,
        MainActivity::class.java.name,
      )
    )
    ActivityScenario.launch(MainActivity::class.java)
    onView(ViewMatchers.isRoot()).captureRoboImage()
  }
}
    """
    )
  }

  val appBuildFile = AppBuildFile(testProjectDir)
}
