package com.github.takahirom.roborazzi.idea.preview

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.components.JBBox
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import javax.swing.BorderFactory
import javax.swing.JComponent

class TaskToolbarPanel(
  project: Project,
  onTaskExecuteButtonClicked: (taskName: String) -> Unit
) : JBPanel<JBPanel<*>>(BorderLayout()) {

  private val jbBox = JBBox.createHorizontalBox()
  private val actionToolbar = RoborazziGradleTaskToolbar(
    project = project,
    place = "RoborazziGradleTaskToolbar",
    horizontal = true,
    onTaskExecuteButtonClicked = onTaskExecuteButtonClicked
  )

  var isExecuteGradleTaskActionEnabled: Boolean
    get() = actionToolbar.isExecuteGradleTaskActionEnabled
    set(value) {
      actionToolbar.isExecuteGradleTaskActionEnabled = value
    }

  init {
    actionToolbar.setTargetComponent(this)
    add(jbBox.apply {
      isVisible = false
      setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4))
      add(actionToolbar.component, GridBagConstraints().apply {
        gridx = 0
        gridy = 0
        anchor = GridBagConstraints.WEST
        insets = JBUI.insets(4)
      })
    })
  }

  fun setActions(actions: List<ToolbarAction>) {
    if (actions.isEmpty()) {
      jbBox.isVisible = false
      return
    }
    jbBox.isVisible = true
    val actionList = listOf(*actions.toTypedArray())
    actionToolbar.setActions(actionList)
  }

  data class ToolbarAction(val label: String, val id: String)
}

class RoborazziGradleTaskToolbar(
  project: Project,
  place: String,
  actionGroup: DefaultActionGroup = DefaultActionGroup(),
  horizontal: Boolean,
  onTaskExecuteButtonClicked: (taskName: String) -> Unit
) : ActionToolbarImpl(place, actionGroup, horizontal) {

  private val propertiesComponent = PropertiesComponent.getInstance(project)
  private val roborazziGradleTaskAction = GradleTaskComboBoxAction(propertiesComponent)
  private val executeGradleTaskExecuteAction =
    ExecuteGradleTaskExecuteAction(propertiesComponent, onTaskExecuteButtonClicked)

  var isExecuteGradleTaskActionEnabled: Boolean
    get() = isEnabled
    set(value) {
      executeGradleTaskExecuteAction.isActionEnabled = value
      isEnabled = value
    }

  init {
    actionGroup.addAll(
      listOf(
        roborazziGradleTaskAction,
        executeGradleTaskExecuteAction
      )
    )
  }

  override fun createToolbarButton(
    action: AnAction,
    look: ActionButtonLook?,
    place: String,
    presentation: Presentation,
    minimumSize: Dimension
  ): ActionButton {
    val toolbarButton = super.createToolbarButton(action, look, place, presentation, minimumSize)
    presentation.putClientProperty(Key(action::class.simpleName!! + "Component"), toolbarButton)
    return toolbarButton
  }

  fun setActions(actions: List<TaskToolbarPanel.ToolbarAction>) {
    roborazziGradleTaskAction.setActions(actions)
  }

  class GradleTaskComboBoxAction(
    private val propertiesComponent: PropertiesComponent,
  ) : ComboBoxAction() {

    private val popupActionGroup: DefaultActionGroup = DefaultActionGroup()

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext) =
      popupActionGroup

    override fun update(e: AnActionEvent) {
      e.presentation.text = propertiesComponent.getValue(SELECTED_TASK_KEY)
        ?: if (popupActionGroup.childActionsOrStubs.isNotEmpty()) {
          val defaultTask = popupActionGroup.childActionsOrStubs[0].templatePresentation.text
          propertiesComponent.setSelectedTask(defaultTask)
          defaultTask
        } else {
          "Select Task"
        }
      e.presentation.icon = AllIcons.General.Gear
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    fun setActions(actions: List<TaskToolbarPanel.ToolbarAction>) {
      popupActionGroup.removeAll()
      actions.forEach {
        popupActionGroup.add(
          GradleTaskSelectAction(
            it.label,
            propertiesComponent
          )
        )
      }
    }

  }

  class ExecuteGradleTaskExecuteAction(
    private val propertiesComponent: PropertiesComponent,
    private val onActionClicked: (taskName: String) -> Unit
  ) : DumbAwareAction("Execute Selected Task", "Execute selected task", AllIcons.Actions.Refresh) {

    var isActionEnabled = true
      set(value) {
        field = value
        updateAllToolbarsImmediately()
      }

    override fun actionPerformed(e: AnActionEvent) {
      propertiesComponent.getSelectedTask()?.let { onActionClicked(it) }
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabled = isActionEnabled
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
  }

  class GradleTaskSelectAction(
    private val taskName: String,
    private val propertiesComponent: PropertiesComponent,
  ) : DumbAwareAction(taskName, taskName, AllIcons.General.Gear) {

    override fun actionPerformed(e: AnActionEvent) {
      e.presentation.text = taskName
      propertiesComponent.setSelectedTask(taskName)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.text = taskName
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
  }

  companion object {
    internal const val SELECTED_TASK_KEY = "roborazzi.idea.selectedTask"
  }
}

fun PropertiesComponent.getSelectedTask(): String? {
  return getValue(RoborazziGradleTaskToolbar.SELECTED_TASK_KEY)
}

fun PropertiesComponent.setSelectedTask(taskName: String) {
  setValue(RoborazziGradleTaskToolbar.SELECTED_TASK_KEY, taskName)
}