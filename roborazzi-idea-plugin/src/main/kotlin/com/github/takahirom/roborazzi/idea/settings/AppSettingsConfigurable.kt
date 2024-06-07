package com.github.takahirom.roborazzi.idea.settings

import com.github.takahirom.roborazzi.idea.settings.AppSettingsState.Companion.instance
import com.intellij.openapi.options.Configurable
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

/**
 * Provides controller functionality for application settings.
 */
class AppSettingsConfigurable : Configurable {
  private var mySettingsComponent: AppSettingsComponent? = null

  @Nls(capitalization = Nls.Capitalization.Title)
  override fun getDisplayName(): String {
    return "Roborazzi"
  }

  override fun getPreferredFocusedComponent(): JComponent {
    return mySettingsComponent!!.preferredFocusedComponent
  }

  override fun createComponent(): JComponent? {
    mySettingsComponent = AppSettingsComponent()
    return mySettingsComponent!!.panel
  }

  override fun isModified(): Boolean {
    val settings = instance
    val modified = mySettingsComponent!!.imagesPathForModule != settings.imagesPathForModule
    return modified
  }

  override fun apply() {
    val settings = instance
    settings.imagesPathForModule = mySettingsComponent!!.imagesPathForModule
  }

  override fun reset() {
    val settings = instance
    mySettingsComponent!!.imagesPathForModule = settings.imagesPathForModule
  }

  override fun disposeUIResources() {
    mySettingsComponent = null
  }
}