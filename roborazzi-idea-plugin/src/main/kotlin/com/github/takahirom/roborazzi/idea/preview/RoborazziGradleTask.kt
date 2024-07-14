package com.github.takahirom.roborazzi.idea.preview

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleUtil

class RoborazziGradleTask {

  fun fetchTasks(project: Project): List<String> {
    val gradleModuleData = getGradleData(project) ?: return emptyList()

    return ExternalSystemApiUtil.findAll(gradleModuleData, ProjectKeys.TASK)
      .filter { it.data.name.contains("Roborazzi", true) && it.data.name.contains("DirRoborazzi", true).not()}
      .map { gradleModuleData.data.id + ":" + it.data.name }.sorted()
  }

  fun executeTaskByName(
    project: Project,
    taskName: String,
    onTaskExecuted:() -> Unit
  ) {
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
      object : TaskCallback {
        override fun onSuccess() {
          roborazziLog("Task '$taskName' executed successfully")
          onTaskExecuted()
        }

        override fun onFailure() {
          roborazziLog("Task '$taskName' execution failed")
          onTaskExecuted()
        }
      },
      ProgressExecutionMode.IN_BACKGROUND_ASYNC,
      false
    )
  }
}

private fun getGradleData(project: Project): DataNode<ModuleData>? {
  val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
  val psiFile: PsiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
  val ktFile = psiFile as? KtFile ?: return null
  val module = ktFile.module ?: return null
  return GradleUtil.findGradleModuleData(module)
}
