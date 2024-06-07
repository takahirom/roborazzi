package com.github.takahirom.roborazzi.idea.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import groovyjarjarantlr4.v4.runtime.misc.NotNull
import java.io.File

@State(
  name = "com.github.takahirom.roborazzi.idea.settings.AppSettingsState",
  storages = [Storage("RoborazziSettingsPlugin.xml")]
)
class AppSettingsState : PersistentStateComponent<AppSettingsState?> {
  var imagesPathForModule: String = File.separator + "build" + File.separator + "outputs" + File.separator + "roborazzi"

  override fun getState(): AppSettingsState? {
    return this
  }

  override fun loadState(@NotNull state: AppSettingsState) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    val instance: AppSettingsState by lazy {
      ApplicationManager.getApplication().getService(
        AppSettingsState::class.java
      )
    }
  }
}