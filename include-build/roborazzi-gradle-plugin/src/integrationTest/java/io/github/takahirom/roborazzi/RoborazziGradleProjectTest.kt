package io.github.takahirom.roborazzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Run this test with `cd include-build` and `./gradlew roborazzi-gradle-plugin:check`
 */
class RoborazziGradleProjectTest {

  @get:Rule
  val testProjectDir = TemporaryFolder()

  private val screenshotAndName =
    "app/build/outputs/roborazzi/com.github.takahirom.integration_test_project.RoborazziTest"

  private val customReferenceScreenshotAndName =
    "app/build/outputs/roborazzi/customdir/custom_file"
  private val customCompareScreenshotAndName =
    "app/build/outputs/roborazzi/custom_compare_outputDirectoryPath/custom_file"

  private val addedScreenshotAndName =
    "app/build/outputs/roborazzi/com.github.takahirom.integration_test_project.AddedRoborazziTest"

  @Test
  fun record() {
    RoborazziGradleProject(testProjectDir).apply {
      record()

      checkCompareFileNotExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
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
      assertNotSkipped(output)

      checkCompareFileNotExists()
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

      checkCompareFileNotExists()
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
      assertNotSkipped(output1)
      // files are not changed so should be skipped
      val output2 = record().output
      assertSkipped(output2)

      checkCompareFileNotExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }


  @Test
  fun unitTestWhenRunTwice() {
    RoborazziGradleProject(testProjectDir).apply {
      unitTest()
      val output = unitTest().output
      assertSkipped(output)

      checkCompareFileNotExists()
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
      recordWithSystemParameter()

      checkCompareFileNotExists()
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

      checkCompareFileNotExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
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
      assertEquals(recordFileHash1, recordFileHash2)
      checkCompareFileNotExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_actual.png")
    }
  }


  @Test
  fun verify_addDetect() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      addTest()

      verifyAndFail().shouldDetectNonExistentPngCapture()

      checkCompareFileNotExists()
      checkRecordedFileNotExists("$addedScreenshotAndName.testCapture.png")
      checkRecordedFileExists("$addedScreenshotAndName.testCapture_compare.png")
      checkRecordedFileExists("$addedScreenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordWithCompareParameter() {
    RoborazziGradleProject(testProjectDir).apply {
      recordWithCompareParameter()

      checkCompareFileNotExists()
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
      assertNotEquals(recordFileHash1, recordFileHash2)
      checkCompareFileNotExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun verify_nochange() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      verify()

      checkCompareFileNotExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
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
      assertNotEquals(recordFileHash1, recordFileHash2)
      checkCompareFileNotExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }


  @Test
  fun verifyAndRecord_nochange() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      verifyAndRecord()

      checkCompareFileNotExists()
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

      checkCompareFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
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

      checkCompareFileExists()
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

      checkCompareFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun compareWithCustomPath() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      changeScreen()
      compare()

      checkCompareFileExists()
      checkRecordedFileExists("$customReferenceScreenshotAndName.png")
      checkRecordedFileExists("${customCompareScreenshotAndName}_compare.png")
      checkRecordedFileExists("${customCompareScreenshotAndName}_actual.png")
    }
  }
}
