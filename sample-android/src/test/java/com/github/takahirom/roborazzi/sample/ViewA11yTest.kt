package com.github.takahirom.roborazzi.sample

import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.AccessibilityCheckAfterTestStrategy
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.RoborazziRule.Options
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.checkRoboAccessibility
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils.matchesElements
import com.google.android.apps.common.testing.accessibility.framework.matcher.ElementMatchers.withTestTag
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel4, sdk = [35])
class ViewA11yTest {
  @Suppress("DEPRECATION")
  @get:Rule(order = Int.MIN_VALUE)
  var thrown: ExpectedException = ExpectedException.none()

  @get:Rule
  val activityScenarioRule = ActivityScenarioRule(ComponentActivity::class.java)

  val taskType: RoborazziTaskType = roborazziSystemPropertyTaskType()

  @get:Rule
  val roborazziRule = RoborazziRule(
    captureRoot = Espresso.onView(ViewMatchers.isRoot()),
    options = Options(
      roborazziAccessibilityOptions = RoborazziATFAccessibilityCheckOptions(
        checker = RoborazziATFAccessibilityChecker(
          preset = AccessibilityCheckPreset.LATEST,
          suppressions = matchesElements(withTestTag("suppress"))
        ),
        failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Warning
      ),
      accessibilityCheckStrategy = AccessibilityCheckAfterTestStrategy()
    )
  )

  @Test
  fun clickableWithoutSemantics() {
    if (taskType.isEnabled()) {
      thrown.expectMessage("SpeakableTextPresentCheck")
    }

    activityScenarioRule.scenario.onActivity { activity ->
      activity.setContentView(
        FrameLayout(activity).apply {
          id = android.R.id.content
          addView(
            View(activity).apply {
              setBackgroundColor(Color.BLACK)
              setOnClickListener { }
            }
          )
        }
      )
    }
  }

  @Test
  fun smallClickable() {
    // for(ViewHierarchyElement view : getElementsToEvaluate(fromRoot, hierarchy)) {
    //   if (!Boolean.TRUE.equals(view.isClickable()) && !Boolean.TRUE.equals(view.isLongClickable())) {
    // TODO investigate
//    thrown.expectMessage("TouchTargetSizeCheck")

    activityScenarioRule.scenario.onActivity { activity ->
      activity.setContentView(
        FrameLayout(activity).apply {
          id = android.R.id.content
          addView(
            View(activity).apply {
              contentDescription = "clickable"
              setBackgroundColor(Color.BLACK)
              setOnClickListener { }
            }
          )
        }
      )
    }
    onView(ViewMatchers.withContentDescription("clickable")).check(ViewAssertions.matches(ViewMatchers.isClickable()))
  }

  @Test
  fun clickableBox() {
    activityScenarioRule.scenario.onActivity { activity ->
      activity.setContentView(
        FrameLayout(activity).apply {
          id = android.R.id.content
          setBackgroundColor(Color.WHITE)
          addView(
            TextView(activity).apply {
              text = "Something to Click"
              setTextColor(Color.BLACK)
              setOnClickListener { }
            }
          )
        }
      )
    }
  }


  @Test
  fun checkRoboAccessibilityCheck() {
    thrown.expectMessage("TextContrastCheck")
    activityScenarioRule.scenario.onActivity { activity ->
      val frameLayout = FrameLayout(activity)
      activity.setContentView(
        frameLayout.apply {
          setBackgroundColor(Color.BLACK)
          addView(
            TextView(activity).apply {
              text = "Something hard to read"
              setTextColor(Color.DKGRAY)
            }
          )
        }
      )
      onView(ViewMatchers.isRoot())
        .checkRoboAccessibility()
      frameLayout.removeAllViews()
    }
  }

  @Test
  fun supressionsTakeEffect() {
    activityScenarioRule.scenario.onActivity { activity ->
      activity.setContentView(
        FrameLayout(activity).apply {
          id = android.R.id.content
          addView(
            View(activity).apply {
              setBackgroundColor(Color.BLACK)
              contentDescription = ""
              tag = "suppress"
            }
          )
        }
      )
    }
  }

  @Test
  fun faintText() {
    if (taskType.isEnabled()) {
      thrown.expectMessage("TextContrastCheck")
    }

    activityScenarioRule.scenario.onActivity { activity ->
      activity.setContentView(
        FrameLayout(activity).apply {
          id = android.R.id.content
          setBackgroundColor(Color.BLACK)
          addView(
            TextView(activity).apply {
              text = "Something hard to read"
              setTextColor(Color.DKGRAY)
            }
          )
        }
      )
    }
  }

  @Test
  fun normalText() {
    activityScenarioRule.scenario.onActivity { activity ->
      activity.setContentView(
        FrameLayout(activity).apply {
          id = android.R.id.content
          setBackgroundColor(Color.DKGRAY)
          addView(
            TextView(activity).apply {
              text = "Something not hard to read"
              setTextColor(Color.WHITE)
            }
          )
        }
      )
    }
  }
}

