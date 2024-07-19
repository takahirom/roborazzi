package com.github.takahirom.roborazzi.idea.preview

import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleUtil

class RoborazziGradleTask {

  suspend fun fetchTasks(project: Project): List<String> {
    val gradleModuleData = readAction { getGradleData(project) } ?: return emptyList()
    val nodes = readAction {
      ExternalSystemApiUtil.findAll(gradleModuleData, ProjectKeys.TASK)
    }
    return withContext(Dispatchers.Default) {
      nodes
        .filter {
          it.data.name.contains("Roborazzi", true) && it.data.name.contains(
            "DirRoborazzi",
            true
          ).not() && !it.data.name.contains("finalize", true)
        }
        .map { gradleModuleData.data.id + ":" + it.data.name }
        .sortedBy { com.github.takahirom.roborazzi.RoborazziTaskType.getOrderOfTaskName(it) }
    }
  }

  fun executeTaskByName(
    project: Project,
    taskName: String,
  ) {
    val runManager = RunManager.getInstance(project)
    val configurationFactory = GradleExternalTaskConfigurationType.getInstance().factory
    val runConfiguration = runManager.createConfiguration(
      "Execute $taskName",
      configurationFactory
    )

    val gradleRunConfiguration = runConfiguration.configuration as GradleRunConfiguration
    gradleRunConfiguration.settings.apply {
      externalProjectPath = project.basePath
      taskNames = listOf(taskName)
      externalSystemIdString = GradleConstants.SYSTEM_ID.id
    }

    runManager.addConfiguration(runConfiguration)
    runManager.selectedConfiguration = runConfiguration

    val environment = ExecutionEnvironment(
      DefaultRunExecutor.getRunExecutorInstance(),
      ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, gradleRunConfiguration)!!,
      runConfiguration,
      project
    )

    ExecutionManager.getInstance(project).restartRunProfile(environment)
    ExecutionManager.getInstance(project).getRunningProcesses()
  }
}

private fun getGradleData(project: Project): DataNode<ModuleData>? {
  val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
  val psiFile: PsiFile =
    PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
  val ktFile = psiFile as? KtFile ?: return null
  val module = ktFile.module ?: return null
  return GradleUtil.findGradleModuleData(module)
}
