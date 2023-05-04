package com.github.takahirom.roborazzi

import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.ComponentActivity

class RoborazziTransparentActivity: ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
    setTheme(R.style.RoborazziTransparentTheme)
    super.onCreate(savedInstanceState, persistentState)
  }
}