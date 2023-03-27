package com.github.takahirom.roborazzi.sample

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.RoborazziRule.CaptureType
import com.github.takahirom.roborazzi.RoborazziRule.Options
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RuleTestWithAllImage {
  @get:Rule val roborazziRule = RoborazziRule(
    onView(isRoot()),
    Options(CaptureType.AllImage)
  )
  @Test
  fun captureRoboGifSample() {
    // launch
    launch(MainActivity::class.java)

    // Change image
    onView(withId(R.id.image))
      .perform(object : ViewAction {
        override fun getDescription() = ""

        override fun getConstraints() = withId(R.id.image)

        override fun perform(uiController: UiController?, view: View?) {
          (view as ImageView).setImageDrawable(ColorDrawable(Color.RED))
        }
      })

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
