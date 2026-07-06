package com.github.takahirom.roborazzi

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.io.files.Path
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.posix.getenv
import platform.posix.setenv
import platform.posix.unsetenv

class FileSystemSupportTest {
  @Test
  fun absolutePathIsReturnedAsIs() {
    assertEquals(
      "/abs/results/report.json",
      resolveRoborazziAbsolutePath(
        path = "/abs/results/report.json",
        projectPath = "/project",
        currentDirectory = "/cwd",
      ),
    )
  }

  @Test
  fun relativePathResolvesAgainstProjectPathWhenSet() {
    // The plugin passes an absolute roborazzi.project.path plus project-relative
    // result/output dirs; those must land under the project, not the simulator
    // working directory.
    assertEquals(
      "/project/build/test-results/roborazzi/results/report.json",
      resolveRoborazziAbsolutePath(
        path = "build/test-results/roborazzi/results/report.json",
        projectPath = "/project",
        currentDirectory = "/simulator/cwd",
      ),
    )
  }

  @Test
  fun relativePathFallsBackToCurrentDirectoryWhenProjectPathUnset() {
    // "." is the default of roborazziSystemPropertyProjectPath() (non-Gradle run).
    assertEquals(
      "/simulator/cwd/build/results",
      resolveRoborazziAbsolutePath(
        path = "build/results",
        projectPath = ".",
        currentDirectory = "/simulator/cwd",
      ),
    )
  }

  @OptIn(ExperimentalForeignApi::class)
  @Test
  fun makeDirectoriesResolvesRelativePathAgainstProjectPath() {
    val projectDir = createUniqueTempDir()
    val cwd = NSFileManager.defaultManager.currentDirectoryPath
    val relativeDir = "some/relative/dir-${roborazziCurrentTimeNs()}"
    withProjectPath(projectDir) {
      roborazziMakeDirectories(relativeDir)

      // The directory must be created under the project path (matching the write
      // path), not the simulator process working directory.
      assertTrue(
        SystemFileSystem.exists(Path("$projectDir/$relativeDir")),
        "expected directory under project path",
      )
      assertFalse(
        SystemFileSystem.exists(Path("$cwd/$relativeDir")),
        "directory must not be created under the process working directory",
      )
    }
  }

  @OptIn(ExperimentalForeignApi::class)
  @Test
  fun fileExistsResolvesRelativePathAgainstProjectPath() {
    val projectDir = createUniqueTempDir()
    val relativePath = "results/report-${roborazziCurrentTimeNs()}.json"
    withProjectPath(projectDir) {
      assertFalse(roborazziFileExists(relativePath))

      // Create the file under the project path, then check via a relative path.
      SystemFileSystem.createDirectories(Path("$projectDir/results"))
      SystemFileSystem.sink(Path("$projectDir/$relativePath")).buffered().use { sink ->
        sink.writeString("{}")
      }

      assertTrue(
        roborazziFileExists(relativePath),
        "roborazziFileExists must resolve the relative path against the project path",
      )
    }
  }

  @OptIn(ExperimentalForeignApi::class)
  private fun createUniqueTempDir(): String {
    val base = NSTemporaryDirectory().trimEnd('/')
    val dir = "$base/roborazzi-test-${roborazziCurrentTimeNs()}"
    SystemFileSystem.createDirectories(Path(dir))
    return dir
  }

  @OptIn(ExperimentalForeignApi::class)
  private inline fun withProjectPath(projectPath: String, block: () -> Unit) {
    val key = "roborazzi.project.path"
    val previous = getenv(key)?.toKString()
    setenv(key, projectPath, 1)
    try {
      block()
    } finally {
      if (previous == null) {
        unsetenv(key)
      } else {
        setenv(key, previous, 1)
      }
    }
  }
}
