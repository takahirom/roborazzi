package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.CaptureResults
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.rules.TemporaryFolder
import java.io.File

enum class BuildType {
  Build, BuildAndFail
}

class RoborazziGradleRootProject(val testProjectDir: TemporaryFolder) {
  init {
    File("./src/integrationTest/projects").copyRecursively(testProjectDir.root, true)
  }

  val appModule = AppModule(this, testProjectDir)
  val previewModule = PreviewModule(this, testProjectDir)
  val kmpLibraryModule = KmpLibraryModule(this, testProjectDir)

  fun runTask(
    task: String,
    buildType: BuildType,
    additionalParameters: Array<String>
  ): BuildResult {
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
//          "GRADLE_USER_HOME" to File(testProjectDir.root, "gradle").absolutePath,
        )
      )
      .let {
        if (buildType == BuildType.BuildAndFail) {
          it.buildAndFail()
        } else {
          it.build()
        }
      }
    return buildResult
  }

  fun runMultipleKspTasks(vararg tasks: String): BuildResult {
    val buildResult = GradleRunner.create()
      .withProjectDir(testProjectDir.root)
      .withArguments(
        *tasks,
        "--stacktrace",
        "--build-cache",
        "--info"
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
        try {
          it.build()
        } catch (e: Exception) {
          // If build fails, we still want to examine the output
          it.buildAndFail()
        }
      }
    return buildResult
  }
}

class AppModule(val rootProject: RoborazziGradleRootProject, val testProjectDir: TemporaryFolder) {

  var buildDirName = "build"

  fun record(): BuildResult {
    val task = "recordRoborazziDebug"
    return runTask(task)
  }

  fun recordWithCleanupOldScreenshots(): BuildResult {
    val task = "recordRoborazziDebug"
    return runTask(task, additionalParameters = arrayOf("-Proborazzi.cleanupOldScreenshots=true"))
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

  fun compareWithCleanupOldScreenshots(): BuildResult {
    val task = "compareRoborazziDebug"
    return runTask(task, additionalParameters = arrayOf("-Proborazzi.cleanupOldScreenshots=true"))
  }

  fun clear(): BuildResult {
    val task = "clearRoborazziDebug"
    return runTask(task)
  }

  fun clean(): BuildResult {
    val task = "clean"
    return runTask(task)
  }

  fun compareWithSystemParameter(): BuildResult {
    val task = "testDebugUnitTest"
    return runTask(task, additionalParameters = arrayOf("-Proborazzi.test.compare=true"))
  }

  fun removeRoborazziOutputDir() {
    File(testProjectDir.root, "app/$buildDirName/outputs/roborazzi").deleteRecursively()
  }

  fun removeRoborazziAndIntermediateOutputDir() {
    File(testProjectDir.root, "app/$buildDirName/outputs/roborazzi").deleteRecursively()
    File(testProjectDir.root, "app/$buildDirName/intermediates/roborazzi").deleteRecursively()
  }

  fun assertNotSkipped(output: String) {
    assert(output.contains("testDebugUnitTest' is not up-to-date because"))
  }


  fun assertSkipped(output: String) {
    assert(output.contains("testDebugUnitTest UP-TO-DATE"))
  }

  fun assertFromCache(output: String) {
    assert(output.contains("testDebugUnitTest FROM-CACHE"))
  }

  private fun runTask(
    task: String,
    buildType: BuildType = BuildType.Build,
    additionalParameters: Array<String> = arrayOf()
  ): BuildResult {
    buildGradle.addIncludeBuild()

    val buildResult = rootProject.runTask(
      "app:" + task,
      buildType,
      additionalParameters
    )
    return buildResult
  }

  class BuildGradle(private val folder: TemporaryFolder) {
    private val PATH = "app/build.gradle.kts"
    var removeOutputDirBeforeTestTypeTask = false
    var customOutputDirPath: String? = null
    var customCompareOutputDirPath: String? = null

    init {
      addIncludeBuild()
    }

    fun addIncludeBuild() {
      folder.root.resolve(PATH).delete()
      val buildFile = folder.newFile(PATH)
      buildFile.writeText(
        """
@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
  id("com.android.application")
  // AGP 9.0: org.jetbrains.kotlin.android is no longer needed (built-in Kotlin)
  id("io.github.takahirom.roborazzi")
}

android {
  namespace = "com.github.takahirom.integration_test_project"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "com.github.takahirom.integration_test_project"
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()
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
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    buildConfig = false
    resValues = false
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
  implementation(libs.roborazzi)
  implementation(libs.roborazzi.junit.rule)
  testImplementation(libs.robolectric)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity)
//  implementation(libs.androidx.activity.compose)
//  implementation(libs.androidx.compose.ui)
//  implementation(libs.androidx.compose.ui.graphics)
//  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  testImplementation(libs.junit)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.androidx.test.espresso.core)
//  testImplementation(platform(libs.androidx.compose.bom))
//  testImplementation(libs.androidx.compose.ui.test.junit4)
//  debugImplementation(libs.androidx.compose.ui.tooling)
//  debugImplementation(libs.androidx.compose.ui.test.manifest)
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
      if (customOutputDirPath != null) {
        buildFile.appendText(
          """
            roborazzi {
              outputDir.set(file("$customOutputDirPath"))
            }
            
          """.trimIndent()
        )
      }
      if (customCompareOutputDirPath != null) {
        buildFile.appendText(
          """
            roborazzi {
              compare {
                outputDir.set(file("$customCompareOutputDirPath"))
              }
            }
            
          """.trimIndent()
        )
      }
    }
  }

  fun checkResultFileExists(nameSuffix: String) {
    testProjectDir.root.resolve("app/$buildDirName/test-results/roborazzi/debug/results/").listFiles()
      .firstOrNull { it.name.endsWith(nameSuffix) }
      ?: error("File not found: $nameSuffix")
  }

  fun checkResultFileNotExists(nameSuffix: String) {
    testProjectDir.root.resolve("app/$buildDirName/test-results/roborazzi/debug/results/").listFiles()
      ?.firstOrNull { it.name.endsWith(nameSuffix) }
      ?.let {
        error("File exists: $nameSuffix")
      }
  }


  fun checkResultsSummaryFileNotExists() {
    val recordedFile =
      testProjectDir.root.resolve("app/$buildDirName/test-results/roborazzi/debug/results-summary.json")
    assert(!recordedFile.exists()) {
      "File exists: ${recordedFile.absolutePath}"
    }
  }

  fun checkResultsSummaryFileExists() {
    val recordedFile =
      testProjectDir.root.resolve("app/$buildDirName/test-results/roborazzi/debug/results-summary.json")
    assert(recordedFile.exists()) {
      "File not exists: ${recordedFile.absolutePath}"
    }
  }

  fun checkResultCount(
    recorded: Int = 0,
    added: Int = 0,
    changed: Int = 0,
    unchanged: Int = 0
  ) {
    val recordedFile =
      testProjectDir.root.resolve("app/$buildDirName/test-results/roborazzi/debug/results-summary.json")
    checkResultCount(recordedFile, recorded, added, changed, unchanged)
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

  fun changeBuildDir(buildDirName: String) {
    testProjectDir.root.resolve("gradle.properties").appendText(
      "\nbuildDir=$buildDirName"
    )
    this.buildDirName = buildDirName
  }

  fun changeScreen() {
    val file =
      testProjectDir.root.resolve("app/src/main/java/com/github/takahirom/integration_test_project/MainActivity.kt")
    file.writeText(
      """package com.github.takahirom.integration_test_project

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.widget.TextView

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(TextView(this).apply{
      text = "■■■■■■■■■■■■■■■■  Roborazzi!!Detect this change!!!! ■■■■■■■■■■"
    })
  }
}
"""
    )
  }

  fun resetScreen() {
    val file =
      testProjectDir.root.resolve("app/src/main/java/com/github/takahirom/integration_test_project/MainActivity.kt")
    file.writeText(
      """package com.github.takahirom.integration_test_project

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.widget.TextView

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(TextView(this).apply{
      text = "Hello World!"
    })
  }
}
"""
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

  fun removeCompareOutputDir() {
    if(!testProjectDir.root.resolve("app/build/custom_compare_outputDirectoryPath")
      .deleteRecursively()){
      throw IllegalStateException("Failed to delete custom_compare_outputDirectoryPath")
    }
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

  val buildGradle = BuildGradle(testProjectDir)
}

class KmpLibraryModule(
  val rootProject: RoborazziGradleRootProject,
  private val testProjectDir: TemporaryFolder
) {
  companion object {
    val moduleName = "sample-kmp-library"
  }

  fun build(): BuildResult {
    return runTask("build")
  }

  fun runHelp(): BuildResult {
    return runTask("tasks", "--all")
  }

  fun recordRoborazzi(): BuildResult {
    return runTask("recordRoborazziAndroidHostTest")
  }

  fun checkRecordedFileExists(expectedFileName: String) {
    val outputDir = testProjectDir.root.resolve("$moduleName/build/outputs/roborazzi")
    val files = outputDir.listFiles()?.map { it.name } ?: emptyList()
    assert(files.any { it.contains(expectedFileName) }) {
      "Expected file containing '$expectedFileName' not found in: $files"
    }
  }

  private fun runTask(
    task: String,
    vararg additionalArgs: String
  ): BuildResult {
    return rootProject.runTask(
      ":$moduleName:$task",
      BuildType.Build,
      arrayOf(*additionalArgs)
    )
  }
}

fun checkResultCount(
  recordedFile: File,
  recorded: Int = 0,
  added: Int = 0,
  changed: Int = 0,
  unchanged: Int = 0
) {
  val results = CaptureResults.fromJsonFile(recordedFile.absolutePath)
  assert(results.resultSummary.recorded == recorded) {
    "Expected count: $recorded, actual count: ${results.resultSummary.recorded} summary:${results.resultSummary}"
  }
  assert(results.resultSummary.added == added) {
    "Expected count: $added, actual count: ${results.resultSummary.added} summary:${results.resultSummary}"
  }
  assert(results.resultSummary.changed == changed) {
    "Expected count: $changed, actual count: ${results.resultSummary.changed} summary:${results.resultSummary}"
  }
  assert(results.resultSummary.unchanged == unchanged) {
    "Expected count: $unchanged, actual count: ${results.resultSummary.unchanged} summary:${results.resultSummary}"
  }
}
