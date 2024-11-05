package com.github.takahirom.roborazzi.idea.settings

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator

/**
 * Supports creating and managing a [JPanel] for the Settings Dialog.
 */
class AppSettingsComponent {
  private val myMainPanel: JPanel
  private val imagesPathFromModuleText: JBTextField = JBTextField()

  private val descriptionText = """
    <html>To enable the display of Roborazzi tasks, please enable<br>
    <b>Configure all Gradle tasks during Gradle Sync (this can make Gradle Sync slower)</b> in the Settings | Experimental | Gradle.</html>
""".trimIndent()

  init {
    myMainPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(
        JBLabel("Enter images directory path from module: "),
        imagesPathFromModuleText,
        1,
        false
      )
      // adjust margin between components
      .addVerticalGap(8)
      .addComponent(createNoteSection())
      .addComponent(JBLabel(descriptionText).apply {
        verticalAlignment = JBLabel.TOP
        preferredSize = Dimension(400, 200)
      })
      .addComponentFillVertically(JPanel(), 0)
      .panel
  }
    val panel: JPanel
        get() = myMainPanel
    val preferredFocusedComponent: JComponent
        get() = imagesPathFromModuleText

    var imagesPathForModule: String
        get() = imagesPathFromModuleText.getText()
        set(newText) {
            imagesPathFromModuleText.setText(newText)
        }

  private fun createNoteSection(): JPanel {
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)

    val label = JLabel("Note")
    val separator = JSeparator().apply {
      alignmentY = 0f
    }

    panel.add(label)
    panel.add(Box.createHorizontalStrut(8))
    panel.add(separator)

    return panel
  }
}