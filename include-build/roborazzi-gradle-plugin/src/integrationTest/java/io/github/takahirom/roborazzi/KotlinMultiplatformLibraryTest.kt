package io.github.takahirom.roborazzi

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Integration test for com.android.kotlin.multiplatform.library plugin support
 */
class KotlinMultiplatformLibraryTest {

  @get:Rule
  val testProjectDir = TemporaryFolder()

  @Test
  fun recordScreenshotWithKmpLibraryPlugin() {
    RoborazziGradleRootProject(testProjectDir).kmpLibraryModule.apply {
      val result = recordRoborazzi()
      assert(result.output.contains("BUILD SUCCESSFUL")) {
        "Recording should succeed with KMP library plugin"
      }
      assert(result.output.contains("testAndroidHostTest") ||
             result.output.contains("recordRoborazziAndroidHostTest")) {
        "Should execute Roborazzi recording tasks. Output:\n${result.output}"
      }
      checkRecordedFileExists("GreetingTest.captureGreeting")
    }
  }

  @Test
  fun verifyIsIncludeAndroidResourcesCheckForKmpLibrary() {
    val rootProject = RoborazziGradleRootProject(testProjectDir)
    rootProject.kmpLibraryModule.apply {
      // Note: testProjectDir is a fresh temporary folder created per test via @Rule,
      // so this file mutation is isolated and doesn't require cleanup
      val buildFile = testProjectDir.root.resolve("sample-kmp-library/build.gradle.kts")
      val originalContent = buildFile.readText()

      // Remove the isIncludeAndroidResources setting by commenting it out
      val withoutResources = originalContent.replace(
        "isIncludeAndroidResources = true",
        "// isIncludeAndroidResources = true  // Removed for testing warning"
      )

      buildFile.writeText(withoutResources)

      val result = recordRoborazzi()

      assert(result.output.contains("BUILD SUCCESSFUL")) {
        "Build should succeed"
      }
      assert(result.output.contains("Please set 'isIncludeAndroidResources = true'")) {
        "Should warn about missing isIncludeAndroidResources. Output:\n${result.output}"
      }
    }
  }
}
