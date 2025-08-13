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
    fun write() {
      val file =
        projectFolder.root.resolve(PATH)
      file.parentFile.mkdirs()

      val roborazziExtension = createRoborazziExtension()
      val androidBlock = """
          android {
            namespace = "com.github.takahirom.preview.tests"
            compileSdk = 34

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
              id("io.github.takahirom.roborazzi")
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
                          api("androidx.compose.ui:ui-tooling-preview:1.7.5")
                      }
                  }
                  val androidMain by getting {
                    dependencies {
                        implementation("androidx.compose.material3:material3:1.3.1")
                        implementation("androidx.compose.ui:ui:1.7.5")
                        implementation("androidx.compose.ui:ui-tooling:1.7.5")
                        implementation("androidx.compose.runtime:runtime:1.7.5")
                      }
                  }
                  
                  val androidUnitTest by getting {
                      dependencies {
                          $previewScannerSupportDependency
                          implementation("junit:junit:4.13.2")
                          implementation("org.robolectric:robolectric:4.13")
                          implementation("io.github.sergio-sastre.ComposablePreviewScanner:android:0.6.1")
                          implementation("androidx.compose.ui:ui-test-junit4:1.7.5")
                      }
                  }
                  val androidDebug by creating {
                      dependencies {
                          implementation("androidx.compose.ui:ui-test-manifest:1.7.5")
                      }
                  }
                  
                  val androidInstrumentedTest by getting {
                      dependencies {
                          implementation("androidx.test.ext:junit:1.2.1")
                          implementation("androidx.test.espresso:espresso-core:3.6.1")
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
    id("io.github.takahirom.roborazzi")
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

  dependencies {
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.ui:ui-tooling:1.7.5")
    implementation("androidx.compose.runtime:runtime:1.7.5")

    // replaced by dependency substitution
    $previewScannerSupportDependency
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.7.5")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.5")
    testImplementation("io.github.sergio-sastre.ComposablePreviewScanner:android:0.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
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
      val roborazziExtension = """
              roborazzi {
                generateComposePreviewRobolectricTests {
                  enable = $enable
                  packages = listOf("com.github.takahirom.preview.tests")
                  $includePrivatePreviewsExpr
                  $customTesterExpr
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
}