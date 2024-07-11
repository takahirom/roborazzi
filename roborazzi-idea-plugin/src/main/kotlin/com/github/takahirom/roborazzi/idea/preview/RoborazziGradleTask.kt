package com.github.takahirom.roborazzi.idea.preview

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleConstants

class RoborazziGradleTask {

  fun fetchTasks(project: Project): List<String> {
    // Redo this to fetch gradle's tasks for Roborazzi
    return roborazziTasks()
  }

  fun executeTaskByName(project: Project, taskName: String) {
    val settings = ExternalSystemTaskExecutionSettings().apply {
      externalProjectPath = project.basePath!!
      taskNames = listOf(taskName)
      externalSystemIdString = GradleConstants.SYSTEM_ID.id
    }

    ExternalSystemUtil.runTask(
      settings,
      DefaultRunExecutor.EXECUTOR_ID,
      project,
      GradleConstants.SYSTEM_ID,
      null,
      ProgressExecutionMode.IN_BACKGROUND_ASYNC,
      false
    )
  }

  private fun roborazziTasks(): List<String> {
    return listOf(
      "recordRoborazziDebug",
      "recordRoborazziRelease",
      "compareRoborazziDebug",
      "compareRoborazziRelease",
      "verifyRoborazziDebug",
      "verifyRoborazziRelease"
    )
  }
}