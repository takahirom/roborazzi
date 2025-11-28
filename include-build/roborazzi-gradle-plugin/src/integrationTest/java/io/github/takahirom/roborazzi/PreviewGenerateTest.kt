package io.github.takahirom.roborazzi

import org.gradle.testkit.runner.BuildResult
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GeneratePreviewTestTest {
  @get:Rule
  val testProjectDir = TemporaryFolder()

  @Test
  fun whenRecordRunImagesShouldBeRecorded() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      record()

      checkHasImages()
    }
  }

  @Test
  fun whenKmpModuleAndRecordRunImagesShouldBeRecorded() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      buildGradle.isKmp = true
      record()
      checkHasImages()
    }
  }

  @Test
  fun whenDisablePreviewAndRecordRunImagesShouldNotBeRecorded() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      buildGradle.enable = false

      record()

      checkNoImages()
    }
  }

  @Test
  fun whenCustomTesterAndRecordRunImagesShouldBeRecordedAndCanSeeJUnitLog() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      buildGradle.useCustomTester = true

      record {
        itShouldHaveJUnitRuleLog()
      }

      checkHasImages()
    }
  }

  @Test
  fun whenIncludePrivatePreviewsAndRecordRunImagesShouldBeRecorded() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      buildGradle.isIncludePrivatePreviews = true
      record()

      checkHasPrivatePreviewImages()
    }
  }

  @Test
  fun whenNotIncludingPreviewScannerSupportDependencyAndRecordRunImagesShouldBeError() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      buildGradle.includePreviewScannerSupportDependenciy = false

      record(BuildType.BuildAndFail) {
        assert(output.contains("Roborazzi: Please add the following 'testImplementation'(For Android Project) or 'kotlin.sourceSets.androidUnitTest.dependencies.implementation'(For KMP) dependency to the 'dependencies' block in the 'build.gradle' file: 'io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support' for the 'testImplementation'(For Android Project) or 'kotlin.sourceSets.androidUnitTest.dependencies.implementation'(For KMP) configuration."))
      }
    }
  }

  @Test
  fun whenUsingOldComposablePreviewScannerVersionShouldBeError() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      buildGradle.composablePreviewScannerVersion = "0.6.1"

      record(BuildType.BuildAndFail) {
        assert(output.contains("Roborazzi: ComposablePreviewScanner version 0.7.0 or higher is required"))
      }
    }
  }

  /**
   * Tests that GitHub Issue #732 should not occur: AGP 8.12+ should not cause task dependency errors
   * when running multiple KSP variant tasks simultaneously.
   *
   * This test will fail until the cross-variant dependency issue between Debug and Release KSP tasks is fixed.
   * Following TDD approach: Red (fails now) → Green (passes when issue is fixed).
   */
  @Test
  fun whenAgp812AndKspMultipleVariantsTaskDependencyErrorShouldNotBeReproduced() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      buildGradle.useKsp = true
      buildGradle.write()

      val buildResult = rootProject.runMultipleKspTasks(
        ":${PreviewModule.moduleName}:kspDebugUnitTestKotlin",
        ":${PreviewModule.moduleName}:kspReleaseUnitTestKotlin"
      )

      // Verify that both KSP tasks actually executed (both required for Issue #732)
      val kspTasksExecuted = buildResult.output.contains(":kspDebugUnitTestKotlin") &&
                            buildResult.output.contains(":kspReleaseUnitTestKotlin")

      assert(kspTasksExecuted) {
        "Expected KSP tasks to be executed, but not found in output: ${buildResult.output}"
      }

      // Check if the specific GitHub Issue #732 error is present
      val hasTaskDependencyError = buildResult.output.contains("uses this output of task") &&
                                  buildResult.output.contains("without declaring an explicit or implicit dependency")

      // Assert that the GitHub Issue #732 error should NOT be reproduced (TDD approach)
      assert(!hasTaskDependencyError) {
        "GitHub Issue #732 should be fixed, but task dependency error still occurs. Output: ${buildResult.output}"
      }
    }
  }

  @Test
  fun whenCustomTesterAndIncludePrivatePreviewsWithoutUseScanOptionsShouldFail() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      buildGradle.useCustomTester = true
      buildGradle.isIncludePrivatePreviews = true
      buildGradle.useScanOptionParametersInTester = false

      record(BuildType.BuildAndFail) {
        assert(output.contains("includePrivatePreviews cannot be set automatically when using a custom tester"))
        assert(output.contains("You have two options:"))
      }
    }
  }

  @Test
  fun whenCustomTesterAndIncludePrivatePreviewsWithUseScanOptionsShouldSucceed() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      buildGradle.useCustomTester = true
      buildGradle.isIncludePrivatePreviews = true
      buildGradle.useScanOptionParametersInTester = true

      record()

      checkHasImages()
    }
  }

  @Test
  fun whenNumOfShardsIs1ShouldGenerateSingleTestClass() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      buildGradle.numOfShards = 1

      record()

      checkHasImages()
      checkGeneratedTestClassCount(1)
      checkHasGeneratedTestClass("RoborazziPreviewParameterizedTests")
    }
  }

  @Test
  fun whenNumOfShardsIs4ShouldGenerateMultipleTestClasses() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      buildGradle.numOfShards = 4

      record()

      checkHasImages()
      checkGeneratedTestClassCount(4)
      checkHasGeneratedTestClass("RoborazziPreviewParameterizedTests0")
      checkHasGeneratedTestClass("RoborazziPreviewParameterizedTests1")
      checkHasGeneratedTestClass("RoborazziPreviewParameterizedTests2")
      checkHasGeneratedTestClass("RoborazziPreviewParameterizedTests3")
    }
  }

  @Test
  fun whenMaxParallelForksIs4ShouldAutoGenerateMultipleTestClasses() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      buildGradle.maxParallelForks = 4

      record()

      checkHasImages()
      checkGeneratedTestClassCount(4)
    }
  }


}

class PreviewModule(
  val rootProject: RoborazziGradleRootProject,
  val testProjectDir: TemporaryFolder
) {
  companion object {
    val moduleName = "sample-generate-preview-tests"
  }

  val buildGradle = BuildGradle(testProjectDir)

  class BuildGradle(private val projectFolder: TemporaryFolder) {
    private val PATH = moduleName + "/build.gradle.kts"
    var isKmp = false
    var includePreviewScannerSupportDependenciy = true
    var composablePreviewScannerVersion = "0.7.0"
    var useKsp = false
    var numOfShards: Int? = null
    var maxParallelForks: Int? = null
    
    private fun kspDependencies() = if (useKsp) """
                          ksp("com.google.dagger:hilt-android-compiler:2.57.1")
                          ksp("com.google.dagger:dagger-compiler:2.57.1")""" else ""
    
    private fun kspPlugins() = if (useKsp) """
    id("com.google.dagger.hilt.android") version "2.57.1"
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"""" else ""
    
    private fun hiltImplementationDependencies() = if (useKsp) """
    implementation("com.google.dagger:dagger:2.57.1")
    implementation("com.google.dagger:hilt-android:2.57.1")
    implementation("com.google.dagger:hilt-core:2.57.1")
    implementation("javax.inject:javax.inject:1")""" else ""
    
    fun write() {
      val file =
        projectFolder.root.resolve(PATH)
      file.parentFile.mkdirs()

      val roborazziExtension = createRoborazziExtension()
      val androidBlock = """
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
                  ${if (maxParallelForks != null) "it.maxParallelForks = $maxParallelForks" else ""}
                }
              }
            }
          }

      """.trimIndent()
      val buildGradleText = if (isKmp) {
        val previewScannerSupportDependency = if (includePreviewScannerSupportDependenciy) {
          """
          implementation("io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support:0.1.0")
          """.trimIndent()
        } else {
          ""
        }
        """
          plugins {
              kotlin("multiplatform")
              id("com.android.library")
              id("org.jetbrains.compose")
              id("org.jetbrains.kotlin.plugin.compose")
              id("io.github.takahirom.roborazzi")${kspPlugins()}
          }

          kotlin {
              androidTarget {
                  compilerOptions {
                      freeCompilerArgs.add("-Xopt-in=kotlin.RequiresOptIn")
                  }
              }
              
              sourceSets {
                  val commonMain by getting {
                      dependencies {
                          api(compose.components.uiToolingPreview)
                      }
                  }
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
                          $previewScannerSupportDependency
                          implementation(libs.junit)
                          implementation(libs.robolectric)
                          implementation("io.github.sergio-sastre.ComposablePreviewScanner:android:$composablePreviewScannerVersion")
                          implementation(libs.androidx.compose.ui.test.junit4)${kspDependencies()}
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
              }
          }

          $androidBlock

          $roborazziExtension
          
          // Replace AGP's default Compose Compiler with Kotlin's integrated version
          configurations.all {
              resolutionStrategy.dependencySubstitution {
                  substitute(module("androidx.compose.compiler:compiler"))
                    .using(module("org.jetbrains.kotlin:kotlin-compose-compiler-plugin-embeddable:2.0.21"))
                    .because("Compose Compiler is now shipped as part of Kotlin 2.0.21 distribution")
              }
          }

          repositories {
              mavenCentral()
              google()
          }
        """.trimIndent()
      } else {
        val previewScannerSupportDependency = if (includePreviewScannerSupportDependenciy) {
          """
          testImplementation("io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support:0.1.0")
          """.trimIndent()
        } else {
          ""
        }
        """
  plugins {
    id("com.android.application")
  //  id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")${kspPlugins()}
  }

  $roborazziExtension

  // Replace AGP's default Compose Compiler with Kotlin's integrated version
  configurations.all {
      resolutionStrategy.dependencySubstitution {
          substitute(module("androidx.compose.compiler:compiler"))
            .using(module("org.jetbrains.kotlin:kotlin-compose-compiler-plugin-embeddable:2.0.21"))
            .because("Compose Compiler is now shipped as part of Kotlin 2.0.21 distribution")
      }
  }

  repositories {
    mavenCentral()
    google()
  }
  $androidBlock

  dependencies {${hiltImplementationDependencies()}
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.runtime)

    // replaced by dependency substitution
    $previewScannerSupportDependency
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation("io.github.sergio-sastre.ComposablePreviewScanner:android:$composablePreviewScannerVersion")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)${kspDependencies()}
  }
"""
      }
      file.writeText(
        buildGradleText.trimIndent()
      )
    }

    var enable = true
    var isIncludePrivatePreviews = false
    var useCustomTester = false
    var useScanOptionParametersInTester = false

    private fun createRoborazziExtension(): String {
      val includePrivatePreviewsExpr = if (isIncludePrivatePreviews) {
        """includePrivatePreviews = $isIncludePrivatePreviews"""
      } else {
        ""
      }
      val customTesterExpr = if (useCustomTester) {
        """testerQualifiedClassName = "com.github.takahirom.sample.CustomPreviewTester""""
      } else {
        ""
      }
      val useScanOptionParametersInTesterExpr = if (useScanOptionParametersInTester) {
        """useScanOptionParametersInTester = $useScanOptionParametersInTester"""
      } else {
        ""
      }
      val numOfShardsExpr = if (numOfShards != null) {
        """numOfShards = $numOfShards"""
      } else {
        ""
      }
      val roborazziExtension = """
              roborazzi {
                generateComposePreviewRobolectricTests {
                  enable = $enable
                  packages = listOf("com.github.takahirom.preview.tests")
                  $includePrivatePreviewsExpr
                  $customTesterExpr
                  $useScanOptionParametersInTesterExpr
                  $numOfShardsExpr
                }
              }
          """.trimIndent()
      return roborazziExtension
    }
  }

  fun record(buildType: BuildType = BuildType.Build, checks: BuildResult.() -> Unit = {}) {
    val result = runTask("recordRoborazziDebug", buildType)
    result.checks()
  }

  fun BuildResult.itShouldHaveJUnitRuleLog() {
    assert(output.contains("JUnit4TestLifecycleOptions starting"))
    assert(output.contains("JUnit4TestLifecycleOptions finished"))
  }

  private fun runTask(
    task: String,
    buildType: BuildType = BuildType.Build,
    additionalParameters: Array<String> = arrayOf()
  ): BuildResult {
    buildGradle.write()
    val buildResult = rootProject.runTask(
      ":$moduleName:" + task,
      buildType,
      additionalParameters
    )
    return buildResult
  }

  fun checkHasImages() {
    val images = testProjectDir.root.resolve("$moduleName/build/outputs/roborazzi/").listFiles()
    assert(images?.isNotEmpty() == true)
  }

  fun checkNoImages() {
    val images = testProjectDir.root.resolve("$moduleName/build/outputs/roborazzi/").listFiles()
    checkResultCount(
      testProjectDir.root.resolve("$moduleName/build/test-results/roborazzi/results-summary.json")
      // All zero
    )

    assert(images?.isEmpty() == true)
  }

  fun checkHasPrivatePreviewImages() {
    val privateImages =
      testProjectDir.root.resolve("$moduleName/build/outputs/roborazzi/").listFiles()
        .orEmpty()
        .filter { it.name.contains("PreviewWithPrivate") }
    assert(privateImages.isNotEmpty() == true)
  }

  fun checkGeneratedTestClassCount(expectedCount: Int) {
    val generatedDir = testProjectDir.root.resolve(
      "$moduleName/build/generated/roborazzi/preview-screenshot/debug/com/github/takahirom/roborazzi/"
    )
    val testFiles = generatedDir.listFiles()?.filter { it.name.endsWith(".kt") }.orEmpty()
    assert(testFiles.size == expectedCount) {
      "Expected $expectedCount generated test classes, but found ${testFiles.size}: ${testFiles.map { it.name }}"
    }
  }

  fun checkHasGeneratedTestClass(className: String) {
    val generatedFile = testProjectDir.root.resolve(
      "$moduleName/build/generated/roborazzi/preview-screenshot/debug/com/github/takahirom/roborazzi/$className.kt"
    )
    assert(generatedFile.exists()) {
      "Expected generated test class $className.kt to exist at ${generatedFile.absolutePath}"
    }
  }
}