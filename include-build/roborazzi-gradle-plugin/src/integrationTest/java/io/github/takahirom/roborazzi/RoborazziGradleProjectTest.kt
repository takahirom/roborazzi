package io.github.takahirom.roborazzi

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Run this test with `cd include-build` and `./gradlew roborazzi-gradle-plugin:check`
 */
class RoborazziGradleProjectTest {

  @get:Rule
  val testProjectDir = TemporaryFolder()

  private val className = "com.github.takahirom.integration_test_project.RoborazziTest"

  private var defaultBuildDir = "build"

  private val screenshotAndName
    get() =
      "app/$defaultBuildDir/outputs/roborazzi/$className"

  private val defaultRoborazziOutputDir get() = "$defaultBuildDir/outputs/roborazzi"
  private val customReferenceScreenshotAndName
    get() =
      "app/$defaultRoborazziOutputDir/customdir/custom_file"
  private val customCompareScreenshotAndName
    get() =
      "app/$defaultRoborazziOutputDir/custom_compare_outputDirectoryPath/custom_file"
  private val customReferenceScreenshotAndNameWithRoborazziContext
    get() =
      "app/$defaultRoborazziOutputDir/custom_outputDirectoryPath_from_rule/$defaultRoborazziOutputDir/customdir/custom_file"
  private val customCompareScreenshotAndNameWithRoborazziContext
    get() =
      "app/$defaultRoborazziOutputDir/custom_compare_outputDirectoryPath/custom_file"

  private val addedScreenshotAndName
    get() =
      "app/$defaultRoborazziOutputDir/com.github.takahirom.integration_test_project.AddedRoborazziTest"

  private val resultFileSuffix get() = "$className.testCapture.json"

  @Test
  fun record() {
    RoborazziGradleProject(testProjectDir).apply {
      record()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkResultFileExists(resultFileSuffix)
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordWhenRemovedOutput() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      removeRoborazziOutputDir()
      // should not be skipped even if tests and sources are not changed
      // when output directory is removed
      val output = record().output
//      assertNotSkipped(output)

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordWhenRemovedOutputAndIntermediate() {
    RoborazziGradleProject(testProjectDir).apply {
      removeRoborazziAndIntermediateOutputDir()
      record()
      removeRoborazziAndIntermediateOutputDir()
      // should not be skipped even if tests and sources are not changed
      // when output directory is removed
      val output = record().output
      assertNotSkipped(output)

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordWhenRunTwice() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      // files are changed so should not be skipped
      val output1 = record().output
//      assertNotSkipped(output1)
      // files are not changed so should be skipped
      val output2 = record().output
      assertSkipped(output2)

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordWhenRunTwiceWithGradleCustomOutput() {
    RoborazziGradleProject(testProjectDir).apply {
      val customDirFromGradle = "src/screenshots/roborazzi_customdir_from_gradle"
      appBuildFile.customOutputDirPath = customDirFromGradle
      record()
      // files are changed so should not be skipped
      val output1 = record().output
//      assertNotSkipped(output1)
      // files are not changed so should be skipped
      val output2 = record().output
      assertSkipped(output2)

      checkResultsSummaryFileExists()
      checkRecordedFileExists("app/$customDirFromGradle/$className.testCapture.png")
      checkRecordedFileNotExists("$$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun unitTestWhenRunTwice() {
    RoborazziGradleProject(testProjectDir).apply {
      unitTest()
      val output = unitTest().output
      assertSkipped(output)

      checkResultsSummaryFileNotExists()
      checkRecordedFileNotExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordWithPropertiesAfterUnitTest() {
    RoborazziGradleProject(testProjectDir).apply {
      unitTest()
      // Record task shouldn't be skipped even after unit test
      val recordWithSystemParameter = recordWithSystemParameter()
      assertNotSkipped(recordWithSystemParameter.output)

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordAfterUnitTest() {
    RoborazziGradleProject(testProjectDir).apply {
      unitTest()
      // Record task shouldn't be skipped even after unit test
      record()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun canRecordWhenRemoveOutputDirBeforeTests() {
    RoborazziGradleProject(testProjectDir).apply {
      appBuildFile.removeOutputDirBeforeTestTypeTask = true
      record()
    }
  }

  @Test
  fun verify_changeDetect() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      changeScreen()
      val recordFileHash1 = getFileHash("$screenshotAndName.testCapture.png")

      verifyAndFail().shouldDetectChangedPngCapture()

      val recordFileHash2 = getFileHash("$screenshotAndName.testCapture.png")
      assert(recordFileHash1 == recordFileHash2)
      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_actual.png")
      checkResultCount(changed = 1)
    }
  }

  /**
   * This test is for the issue
   * https://github.com/takahirom/roborazzi/issues/261
   */
  @Test
  fun checkRevertCache() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      changeScreen()
      compare()
      resetScreen()
      clean()
      record()
      changeScreen()
      compare()

      checkResultsSummaryFileExists()
//      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_actual.png")
      checkResultCount(changed = 1)
    }
  }

  @Test
  fun verify_addDetect() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      addTest()

      verifyAndFail().shouldDetectNonExistentPngCapture()

      checkResultsSummaryFileExists()
      checkRecordedFileNotExists("$addedScreenshotAndName.testCapture.png")
      checkRecordedFileExists("$addedScreenshotAndName.testCapture_compare.png")
      checkRecordedFileExists("$addedScreenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordWithCompareParameter() {
    RoborazziGradleProject(testProjectDir).apply {
      recordWithCompareParameter()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordWithSmallProperty() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      changeScreen()
      val recordFileHash1 = getFileHash("$screenshotAndName.testCapture.png")

      recordWithScaleSize()

      val recordFileHash2 = getFileHash("$screenshotAndName.testCapture.png")
      assert(recordFileHash1 != recordFileHash2)
      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun record_noTestFiles() {
    RoborazziGradleProject(testProjectDir).apply {
      removeTests()
      record()

      // Summary file will be generated even if no test files
      checkResultsSummaryFileExists()
      // Test will be skipped when no source so no output
      checkResultFileNotExists(resultFileSuffix)
    }
  }

  @Test
  fun record_noTests() {
    RoborazziGradleProject(testProjectDir).apply {
      removeTests()
      addTestClass()
      record()

      checkResultsSummaryFileExists()
      checkResultFileNotExists(resultFileSuffix)
    }
  }

  @Test
  fun verify_nochange_with_changed_build_dir() {
    RoborazziGradleProject(testProjectDir).apply {
      val buildDirName = "testCustomBuildDirName"
      changeBuildDir(buildDirName)
      defaultBuildDir = buildDirName
      record()
      verify()

      checkResultsSummaryFileExists()
      checkResultFileExists(resultFileSuffix)
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
      checkResultCount(unchanged = 1)
    }
  }

  @Test
  fun verifyAndRecord_changeDetect() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      val recordFileHash1 = getFileHash("$screenshotAndName.testCapture.png")
      changeScreen()

      verifyAndRecordAndFail()

      val recordFileHash2 = getFileHash("$screenshotAndName.testCapture.png")
      assert(recordFileHash1 != recordFileHash2)
      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkResultFileExists(resultFileSuffix)
      checkRecordedFileExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun verifyAndRecord_nochange() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      verifyAndRecord()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun compare() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      changeScreen()
      compare()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkResultFileExists(resultFileSuffix)
      checkRecordedFileExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun compareWithSystemParameter() {
    println("start compareWithSystemParameter")
    RoborazziGradleProject(testProjectDir).apply {
      record()
      changeScreen()
      compareWithSystemParameter()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun compare_nochange() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      compare()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun compareWithCustomPath() {
    RoborazziGradleProject(testProjectDir).apply {
      removeTests()
      addTestCaptureWithCustomPathTest()
      record()
      changeScreen()
      compare()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$customReferenceScreenshotAndName.png")
      checkRecordedFileExists("${customCompareScreenshotAndName}_compare.png")
      checkRecordedFileExists("${customCompareScreenshotAndName}_actual.png")
      checkRecordedFileExists("app/build/outputs/roborazzi/custom_outputDirectoryPath_from_rule/custom_outputFileProvider-com.github.takahirom.integration_test_project.RoborazziTest.testCaptureWithCustomPath.png")
    }
  }

  @Test
  fun compareWithCustomPathAndCaptureFilePathStrategy() {
    RoborazziGradleProject(testProjectDir).apply {
      removeTests()
      addTestCaptureWithCustomPathTest()
      addRelativeFromContextRecordFilePathStrategyGradleProperty()
      record()
      changeScreen()
      compare()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$customReferenceScreenshotAndNameWithRoborazziContext.png")
      checkRecordedFileExists("${customCompareScreenshotAndNameWithRoborazziContext}_compare.png")
      checkRecordedFileExists("${customCompareScreenshotAndNameWithRoborazziContext}_actual.png")
      checkRecordedFileExists("app/build/outputs/roborazzi/custom_outputDirectoryPath_from_rule/custom_outputFileProvider-com.github.takahirom.integration_test_project.RoborazziTest.testCaptureWithCustomPath.png")
    }
  }

  @Test
  fun secondImagesIsSkippedIfFirstVerificationFails() {
    RoborazziGradleProject(testProjectDir).apply {
      removeTests()
      addRuleTest()
      record()
      changeScreen()

      verifyAndFail().shouldDetectChangedPngCapture()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_2.png")
      // If the first verification fails, the second verification will be skipped.
      checkRecordedFileNotExists("$screenshotAndName.testCapture_2_actual.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_2_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_2_actual.png")
    }
  }

  @Test
  fun shouldNotRetainPreviousTestResults() {
    RoborazziGradleProject(testProjectDir).apply {
      removeTests()
      addMultipleTest()

      recordWithFilter1()
      recordWithFilter2()

      checkResultCount(recorded = 1)
    }
  }
}
