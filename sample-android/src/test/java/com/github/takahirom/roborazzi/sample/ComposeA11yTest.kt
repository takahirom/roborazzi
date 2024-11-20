package com.github.takahirom.roborazzi.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.AccessibilityCheckAfterTest
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.RoborazziRule.Options
import com.github.takahirom.roborazzi.checkRoboAccessibility
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
class ComposeA11yTest {
  @Suppress("DEPRECATION")
  @get:Rule(order = Int.MIN_VALUE)
  var thrown: ExpectedException = ExpectedException.none()

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @get:Rule
  val roborazziRule = RoborazziRule(
    composeRule = composeTestRule,
    captureRoot = composeTestRule.onRoot(),
    options = Options(
      accessibilityChecker = RoborazziATFAccessibilityChecker(
        preset = AccessibilityCheckPreset.LATEST,
        suppressions = matchesElements(withTestTag("suppress"))
      ),
      accessibilityCheckStrategy = AccessibilityCheckAfterTest(
        failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Warning,
      )
    )
  )

  @Test
  fun clickableWithoutSemantics() {
    thrown.expectMessage("SpeakableTextPresentCheck")

    composeTestRule.setContent {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
          Modifier
            .size(48.dp)
            .background(Color.Black)
            .clickable {})
      }
    }
  }

  @Test
  fun boxWithEmptyContentDescription() {
    thrown.expectMessage("SpeakableTextPresentCheck")

    composeTestRule.setContent {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
          Modifier
            .size(48.dp)
            .background(Color.Black)
            .semantics {
              contentDescription = ""
            })
      }
    }
  }

  @Test
  fun smallClickable() {
    // for(ViewHierarchyElement view : getElementsToEvaluate(fromRoot, hierarchy)) {
    //   if (!Boolean.TRUE.equals(view.isClickable()) && !Boolean.TRUE.equals(view.isLongClickable())) {
    // TODO investigate
//    thrown.expectMessage("TouchTargetSizeCheck")

    composeTestRule.setContent {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(
          onClick = {}, modifier = Modifier
            .size(30.dp)
            .testTag("clickable")
        ) {
          Text("Something to Click")
        }
      }
    }

    composeTestRule.onNodeWithTag("clickable").assertHasClickAction()
  }

  @Test
  fun clickableBox() {
    composeTestRule.setContent {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = {}) {
          Text("Something to Click")
        }
      }
    }
  }

  @Test
  fun supressionsTakeEffect() {
    composeTestRule.setContent {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
          Modifier
            .size(48.dp)
            .background(Color.Black)
            .testTag("suppress")
            .semantics {
              contentDescription = ""
            })
      }
    }
  }

  @Test
  fun faintText() {
    thrown.expectMessage("TextContrastCheck")

    composeTestRule.setContent {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
          modifier = Modifier
            .size(100.dp)
            .background(Color.DarkGray)
        ) {
          Text("Something hard to read", color = Color.DarkGray)
        }
      }
    }
  }

  @Test
  fun normalText() {
    composeTestRule.setContent {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
          modifier = Modifier
            .size(100.dp)
            .background(Color.DarkGray)
        ) {
          Text("Something not hard to read", color = Color.White)
        }
      }
    }
  }

  @Test
  fun composableOnly() {
    composeTestRule.setContent {
      Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Box(
          modifier = Modifier
            .size(100.dp)
            .background(Color.DarkGray)
            .testTag("nothard")
        ) {
          Text("Something not hard to read", color = Color.White)
        }
        // Use a single text otherwise the Box and Text both trigger and must be suppressed
        Text(
          modifier = Modifier
            .size(100.dp)
            .background(Color.DarkGray)
            .testTag("suppress"),
          text = "Something hard to read",
          color = Color.DarkGray
        )
      }
    }

    // Now run without suppressions
    // Run only against nothard, shouldn't fail because of the hard to read text
    composeTestRule.onNodeWithTag("nothard").checkRoboAccessibility(
      RoborazziATFAccessibilityChecker(
        preset = AccessibilityCheckPreset.LATEST,
      ),
      RoborazziATFAccessibilityChecker.CheckLevel.Warning
    )
  }
}

