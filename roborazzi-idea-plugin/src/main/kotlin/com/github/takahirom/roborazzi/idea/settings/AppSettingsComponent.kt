package com.github.takahirom.roborazzi.idea.settings

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Supports creating and managing a [JPanel] for the Settings Dialog.
 */
class AppSettingsComponent {
    private val myMainPanel: JPanel
    private val imagesPathFromModuleText: JBTextField = JBTextField()

    init {
        myMainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Enter images path from module: "), imagesPathFromModuleText, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .getPanel()
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
}