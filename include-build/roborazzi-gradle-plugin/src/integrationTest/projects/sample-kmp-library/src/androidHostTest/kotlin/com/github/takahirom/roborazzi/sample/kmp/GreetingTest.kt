package com.github.takahirom.roborazzi.sample.kmp

import android.graphics.Color
import android.widget.TextView
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class GreetingTest {
  @Test
  fun captureGreeting() {
    val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
    val textView = TextView(activity).apply {
      text = "Hello, KMP!"
      setTextColor(Color.BLACK)
      setBackgroundColor(Color.WHITE)
    }
    activity.setContentView(textView)
    textView.captureRoboImage()
  }
}
