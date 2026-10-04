package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.fileWithRecordFilePathStrategy
import com.github.takahirom.roborazzi.isAbsoluteFilePath
import com.github.takahirom.roborazzi.provideRoborazziContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(InternalRoborazziApi::class, ExperimentalRoborazziApi::class)
class FileWithRecordFilePathStrategyTest {

  @After
  fun tearDown() {
    System.clearProperty("roborazzi.record.filePathStrategy")
    provideRoborazziContext().clearRuleOverrideOutputDirectory()
  }

  // https://github.com/takahirom/roborazzi/issues/915
  @Test
  fun whenWindowsAbsolutePathWithContextOutputDirectoryStrategyOutputDirectoryShouldNotBePrependedAgain() {
    System.setProperty(
      "roborazzi.record.filePathStrategy",
      "relativePathFromRoborazziContextOutputDirectory"
    )
    provideRoborazziContext().setRuleOverrideOutputDirectory("src/screenshots")
    val windowsAbsolutePath =
      "D:\\a\\robolectric\\robolectric\\integration_tests\\roborazzi\\src\\screenshots\\RoborazziCaptureTest.checkViewWithElevationRendering[31].png"

    val file = fileWithRecordFilePathStrategy(windowsAbsolutePath, isWindows = true)

    assertEquals(File(windowsAbsolutePath), file)
  }

  @Test
  fun whenUnixAbsolutePathWithContextOutputDirectoryStrategyOutputDirectoryShouldNotBePrependedAgain() {
    System.setProperty(
      "roborazzi.record.filePathStrategy",
      "relativePathFromRoborazziContextOutputDirectory"
    )
    provideRoborazziContext().setRuleOverrideOutputDirectory("src/screenshots")
    val unixAbsolutePath = "/home/user/project/src/screenshots/Test.method.png"

    val file = fileWithRecordFilePathStrategy(unixAbsolutePath, isWindows = false)

    assertEquals(File(unixAbsolutePath), file)
  }

  @Test
  fun whenRelativePathWithContextOutputDirectoryStrategyOutputDirectoryShouldBePrepended() {
    System.setProperty(
      "roborazzi.record.filePathStrategy",
      "relativePathFromRoborazziContextOutputDirectory"
    )
    provideRoborazziContext().setRuleOverrideOutputDirectory("src/screenshots")

    val file = fileWithRecordFilePathStrategy("Test.method.png", isWindows = true)

    assertEquals(File("src/screenshots", "Test.method.png"), file)
  }

  @Test
  fun windowsDriveLetterPathsShouldBeAbsoluteOnWindows() {
    assertTrue(isAbsoluteFilePath("D:\\a\\screenshots\\Test.method.png", isWindows = true))
    assertTrue(isAbsoluteFilePath("d:\\a\\screenshots\\Test.method.png", isWindows = true))
    assertTrue(isAbsoluteFilePath("D:/a/screenshots/Test.method.png", isWindows = true))
  }

  @Test
  fun windowsUncPathsShouldBeAbsoluteOnWindows() {
    assertTrue(isAbsoluteFilePath("\\\\server\\share\\Test.method.png", isWindows = true))
  }

  @Test
  fun windowsDriveRelativePathsShouldNotBeAbsolute() {
    // "D:foo" (no separator after the colon) is relative to the current directory of drive D.
    assertFalse(isAbsoluteFilePath("D:Test.method.png", isWindows = true))
  }

  @Test
  fun relativePathsShouldNotBeAbsolute() {
    assertFalse(isAbsoluteFilePath("src/screenshots/Test.method.png", isWindows = true))
    assertFalse(isAbsoluteFilePath("src/screenshots/Test.method.png", isWindows = false))
  }

  @Test
  fun unixAbsolutePathsShouldBeAbsoluteOnUnix() {
    assertTrue(isAbsoluteFilePath("/home/user/screenshots/Test.method.png", isWindows = false))
  }

  @Test
  fun windowsLikeDirectoryNamesShouldStayRelativeOnUnix() {
    // On Unix, ':' and '\' are valid file name characters, so a path like
    // "D:/..." can be a relative path under a directory literally named "D:".
    assertFalse(isAbsoluteFilePath("D:/a/screenshots/Test.method.png", isWindows = false))
    assertFalse(isAbsoluteFilePath("D:\\a\\screenshots\\Test.method.png", isWindows = false))
  }
}
