package com.github.takahirom.roborazzi.idea

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages


class HelloWorldAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project: Project? = e.project
    Messages.showMessageDialog(
      project, "Hello, Welcom to IntellJ IDEA plugin development.", "Welcome",
      Messages.getInformationIcon()
    )
  }
}
