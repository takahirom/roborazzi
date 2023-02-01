package com.github.takahirom.roborazzi.sample

import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.roboAutoCapture
import com.github.takahirom.roborazzi.roboCapture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleUnitTest {

  @Test
  fun roboExample() {
    // launch
    launch(MainActivity::class.java)

    onView(ViewMatchers.isRoot())
      .roboCapture("build/first_screen.png")

    onView(withId(R.id.compose))
      .roboCapture("build/compose.png")

    // move to next page
    onView(withId(R.id.button_first))
      .perform(click())

    onView(ViewMatchers.isRoot())
      .roboCapture("build/second_screen.png")
  }

  @Test
  fun roboAutoCaptureSample() {
    onView(ViewMatchers.isRoot())
      .roboAutoCapture("build/test.gif") {
        // launch
        launch(MainActivity::class.java)
        // move to next page
        onView(withId(R.id.button_first))
          .perform(click())
        // back
        pressBack()
        // move to next page
        onView(withId(R.id.button_first))
          .perform(click())
      }
  }
}
