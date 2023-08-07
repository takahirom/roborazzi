package com.github.takahirom.roborazzi

import org.robolectric.config.ConfigurationRegistry
import org.robolectric.annotation.GraphicsMode

actual fun canScreenshot(): Boolean = try {
  Class.forName("org.robolectric.annotation.GraphicsMode")
  ConfigurationRegistry.get(GraphicsMode.Mode::class.java) == GraphicsMode.Mode.NATIVE
} catch (e: ClassNotFoundException) {
  false
}
