package com.github.takahirom.roborazzi

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
