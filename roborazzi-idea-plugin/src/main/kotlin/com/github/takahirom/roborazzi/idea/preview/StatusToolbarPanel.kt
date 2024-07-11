package com.github.takahirom.roborazzi.idea.preview

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout

class StatusToolbarPanel(
  actions: List<ToolbarAction>,
  onActionClicked: (taskName: String) -> Unit
) : JBPanel<Nothing>(BorderLayout()){
  private val _statusLabel = JBLabel()

  var statusLabel: String = ""
    get() = _statusLabel.text
    set(value) {
      _statusLabel.text = value
      field = value
    }

  init {
    val dropdownMenu = ComboBox(actions.map { it.label }.toTypedArray())
    dropdownMenu.addItemListener { event ->
      val selectedTaskLabel = event.item as String
      val selectedTask = actions.first { it.label == selectedTaskLabel }
      if (selectedTask.id.isEmpty()) {
        return@addItemListener
      }
      onActionClicked(selectedTask.id)
      roborazziLog("Selected: ${selectedTask.label} (${selectedTask.id})")
    }
    add(_statusLabel, BorderLayout.WEST)
    add(dropdownMenu, BorderLayout.EAST)
  }

  data class ToolbarAction(val label: String, val id: String)
}