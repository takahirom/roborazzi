package com.github.takahirom.roborazzi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

@Deprecated("Use RoborazziActivity instead", ReplaceWith("RoborazziActivity"), level = DeprecationLevel.ERROR)
class RoborazziTransparentActivity: RoborazziActivity()

open class RoborazziActivity: ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(intent.getIntExtra(EXTRA_THEME, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen))
    super.onCreate(savedInstanceState)
  }
  companion object {
    const val EXTRA_THEME = "EXTRA_THEME"
    fun createIntent(context: Context, theme: Int = android.R.style.Theme_Translucent_NoTitleBar_Fullscreen): Intent {
      return Intent(context, RoborazziActivity::class.java).apply {
        putExtra(EXTRA_THEME, theme)
      }
    }
  }
}