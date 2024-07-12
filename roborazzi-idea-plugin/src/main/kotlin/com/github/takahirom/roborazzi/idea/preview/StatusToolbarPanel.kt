package com.github.takahirom.roborazzi.idea.preview

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout

class StatusToolbarPanel(
  onActionClicked: (taskName: String) -> Unit
) : JBPanel<Nothing>(BorderLayout()){
  private val _statusLabel = JBLabel()
  private val dropdownMenu = TaskComboBox()

  var statusLabel: String = ""
    get() = _statusLabel.text
    set(value) {
      _statusLabel.text = value
      field = value
    }

  init {
    dropdownMenu.addItemListener { event ->
      val selectedTaskLabel = event.item as String
      val selectedTask = dropdownMenu.actions.firstOrNull {
        roborazziLog("label: ${it.label}, selectedTaskLabel: $selectedTaskLabel")
        it.label == selectedTaskLabel
      }
      if (selectedTask?.id?.isEmpty() == true) {
        return@addItemListener
      }
      roborazziLog("Selected: ${selectedTask?.label} (${selectedTask?.id})")
      onActionClicked(selectedTask?.id ?: return@addItemListener)
    }
    add(_statusLabel, BorderLayout.WEST)
    add(dropdownMenu, BorderLayout.EAST)
  }

  fun setActions(actions: List<ToolbarAction>) {
    val prompt = if (actions.isEmpty()) "No tasks found" else "Select a Task"
    val actionList = listOf(
      ToolbarAction(prompt, ""),
      *actions.toTypedArray()
    )
    dropdownMenu.setActions(actionList)
  }

  data class ToolbarAction(val label: String, val id: String)
}

class TaskComboBox : ComboBox<String>() {

  private val _actions = mutableListOf<StatusToolbarPanel.ToolbarAction>()
  val actions get() = _actions.toList()

  fun setActions(actions: List<StatusToolbarPanel.ToolbarAction>) {
    removeAllItems()
    _actions.clear()
    _actions.addAll(actions)
    actions.forEach { addItem(it.label) }
  }
}