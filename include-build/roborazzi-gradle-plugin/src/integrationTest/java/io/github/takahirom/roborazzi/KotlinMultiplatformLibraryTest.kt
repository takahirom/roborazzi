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

  /**
   * Regression test for the preview diagnostics being gated on the feature being enabled.
   *
   * sample-kmp-library never configures generateComposePreviewRobolectricTests (it stays
   * disabled), so it is an ordinary KMP consumer that did not opt in. Even with
   * isIncludeAndroidResources absent, the preview isIncludeAndroidResources advisory must NOT
   * fire: emitting it here would advise (and, once it becomes a build error, break) users who
   * never asked for preview generation. This mirrors the Android path, where every preview
   * diagnostic is gated behind extension.enable.
   */
  @Test
  fun doesNotWarnAboutIncludeAndroidResourcesWhenPreviewGenerationDisabled() {
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
      assert(!result.output.contains("Diagnostic id: preview.includeAndroidResources")) {
        "Preview diagnostics must stay silent when preview generation is disabled. Output:\n${result.output}"
      }
      assert(!result.output.contains("Please set 'isIncludeAndroidResources = true'")) {
        "Preview isIncludeAndroidResources advisory must not fire when preview generation is disabled. Output:\n${result.output}"
      }
    }
  }
}
