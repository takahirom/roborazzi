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
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent

class StatusToolbarPanel(
  project: Project,
  onActionClicked: (taskName: String) -> Unit
) : JBPanel<Nothing>(BorderLayout()){
  private val _statusLabel = JBLabel()
  private val actionToolbar = RoborazziGradleTaskToolbar(
    project = project,
    place = "RoborazziGradleTaskToolbar",
    horizontal = true,
    onActionClicked = onActionClicked
  )

  var statusLabel: String = ""
    get() = _statusLabel.text
    set(value) {
      _statusLabel.text = value
      field = value
    }

  var isExecuteGradleTaskActionEnabled: Boolean
    get() = actionToolbar.isExecuteGradleTaskActionEnabled
    set(value) {
      actionToolbar.isExecuteGradleTaskActionEnabled = value
    }

  init {
    actionToolbar.setTargetComponent(this)
    add(_statusLabel, BorderLayout.WEST)
    add(actionToolbar.component, BorderLayout.EAST)
  }

  fun setActions(actions: List<ToolbarAction>) {
    if (actions.isEmpty()) {
      actionToolbar.isVisible = false
      return
    }
    actionToolbar.isVisible = true
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
  onActionClicked: (taskName: String) -> Unit
) : ActionToolbarImpl(place, actionGroup, horizontal) {

  private val propertiesComponent = PropertiesComponent.getInstance(project)
  private val roborazziGradleTaskAction = GradleTaskComboBoxAction(propertiesComponent)
  private val executeGradleTaskAction = ExecuteGradleTaskAction(propertiesComponent, onActionClicked)

  var isExecuteGradleTaskActionEnabled: Boolean
    get() = isEnabled
    set(value) {
      executeGradleTaskAction.isActionEnabled = value
      isEnabled = value
    }

  init {
    actionGroup.addAll(
      listOf(
        roborazziGradleTaskAction,
        executeGradleTaskAction
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

  fun setActions(actions: List<StatusToolbarPanel.ToolbarAction>) {
    roborazziGradleTaskAction.setActions(actions)
  }

  class GradleTaskComboBoxAction(
    private val propertiesComponent: PropertiesComponent,
  ) : ComboBoxAction() {

    private val popupActionGroup: DefaultActionGroup = DefaultActionGroup()

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext) = popupActionGroup

    override fun update(e: AnActionEvent) {
      e.presentation.text = propertiesComponent.getValue(SELECTED_TASK_KEY) ?: "Select Task"
      e.presentation.icon = AllIcons.General.Gear
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    fun setActions(actions: List<StatusToolbarPanel.ToolbarAction>) {
      popupActionGroup.removeAll()
      actions.forEach { popupActionGroup.add(GradleTaskAction(it.label, propertiesComponent)) }
    }

  }

  class ExecuteGradleTaskAction(
    private val propertiesComponent: PropertiesComponent,
    private val onActionClicked: (taskName: String) -> Unit
  ): DumbAwareAction("Execute Selected Task", "Execute selected task", AllIcons.Actions.Refresh) {

    var isActionEnabled = true
      set(value) {
        field = value
        updateAllToolbarsImmediately()
      }

    override fun actionPerformed(e: AnActionEvent) {
      propertiesComponent.getValue(SELECTED_TASK_KEY)?.let { onActionClicked(it) }
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabled = isActionEnabled
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
  }

  class GradleTaskAction(
    private val taskName: String,
    private val propertiesComponent: PropertiesComponent,
  ): DumbAwareAction(taskName, taskName, AllIcons.General.Gear) {

    override fun actionPerformed(e: AnActionEvent) {
      e.presentation.text = taskName
      propertiesComponent.setValue(SELECTED_TASK_KEY, taskName)
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