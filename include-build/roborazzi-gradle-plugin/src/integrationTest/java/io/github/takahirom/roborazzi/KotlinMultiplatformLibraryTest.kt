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
  fun buildWithKmpLibraryPlugin() {
    RoborazziGradleRootProject(testProjectDir).kmpLibraryModule.apply {
      val result = runHelp()
      assert(result.output.contains("BUILD SUCCESSFUL")) {
        "Project configuration should succeed with KMP library plugin"
      }
      assert(result.output.contains("testAndroidHostTest")) {
        "Should have testAndroidHostTest task when using androidHostTest source set"
      }
    }
  }

  @Test
  fun recordScreenshotWithKmpLibraryPlugin() {
    RoborazziGradleRootProject(testProjectDir).kmpLibraryModule.apply {
      val result = recordRoborazzi()
      assert(result.output.contains("BUILD SUCCESSFUL")) {
        "Recording should succeed with KMP library plugin"
      }
      checkRecordedFileExists("GreetingTest.captureGreeting")
    }
  }
}
