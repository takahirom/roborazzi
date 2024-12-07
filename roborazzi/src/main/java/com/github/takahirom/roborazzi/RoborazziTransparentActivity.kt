package com.github.takahirom.roborazzi

import android.os.Bundle
import androidx.activity.ComponentActivity

class RoborazziTransparentActivity: ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
    super.onCreate(savedInstanceState)
  }
}