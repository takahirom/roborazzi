package com.github.takahirom.roborazzi.sample

import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    onView(ViewMatchers.withId(R.id.compose))
      .roboCapture("build/compose.png")

    // move to next page
    onView(ViewMatchers.withId(R.id.button_first))
      .perform(click())

    onView(ViewMatchers.isRoot())
      .roboCapture("build/second_screen.png")
  }
}
