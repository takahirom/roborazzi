package com.github.takahirom.roborazzi.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.AccessibilityCheckEachScreenshotStrategy
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.RoborazziRule.Options
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType.INFO
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType.NOT_RUN
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheckResult
import com.google.android.apps.common.testing.accessibility.framework.Parameters
import com.google.android.apps.common.testing.accessibility.framework.uielement.AccessibilityHierarchy
import com.google.android.apps.common.testing.accessibility.framework.uielement.ViewHierarchyElement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Test demonstrating a completely custom ATF Check. Expected to be a niche usecase, but critical when required.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel4, sdk = [35])
class ComposeA11yAfterScreenshotTest {
  @Suppress("DEPRECATION")
  @get:Rule(order = Int.MIN_VALUE)
  var thrown: ExpectedException = ExpectedException.none()

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  val textCollectingCheck = TextCollectingCheck()

  val taskType: RoborazziTaskType = roborazziSystemPropertyTaskType()

  @get:Rule
  val roborazziRule = RoborazziRule(
    composeRule = composeTestRule,
    captureRoot = composeTestRule.onRoot(),
    options = Options(
      captureType = RoborazziRule.CaptureType.AllImage(),
      roborazziAccessibilityOptions = RoborazziATFAccessibilityCheckOptions(
        checker = RoborazziATFAccessibilityChecker(
          checks = setOf(textCollectingCheck),
        ),
        failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Warning
      ),
      accessibilityCheckStrategy = AccessibilityCheckEachScreenshotStrategy(),
    )
  )

  class TextCollectingCheck : CustomAccessibilityHierarchyCheck("Text Collecting Check") {
    val foundText = mutableListOf<String>()

    override fun runCheckOnHierarchy(
      hierarchy: AccessibilityHierarchy,
      element: ViewHierarchyElement?,
      parameters: Parameters?
    ): List<AccessibilityHierarchyCheckResult> {
      return getElementsToEvaluate(element, hierarchy).map { childElement ->
        val text = childElement.text?.toString()

        if (text == null) {
          result(childElement, NOT_RUN, 1, null)
        } else {
          foundText.add(text)
          result(childElement, INFO, 3, null)
        }
      }
    }
  }

  @Test
  fun takesScreenshots() {
    val count = mutableIntStateOf(0)

    composeTestRule.setContent {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier
          .size(100.dp)
          .background(Color.White)) {
          Text("Clicks: ${count.intValue}", color = Color.Black)
          Spacer(modifier = Modifier.size(10.dp * count.intValue))
          Button(onClick = {
            count.intValue++
          }, modifier = Modifier.testTag("increment")) {
            Icon(Icons.Filled.Add, contentDescription = null)
          }
        }
      }
    }

    composeTestRule.onNodeWithTag("increment").performClick()
    composeTestRule.waitUntil { count.intValue == 1 }

    composeTestRule.onNodeWithTag("increment").performClick()
    composeTestRule.waitUntil { count.intValue == 2 }

    if (taskType.isEnabled()) {
      // Last check happens after test
      assertEquals(listOf("Clicks: 0", "Clicks: 1"), textCollectingCheck.foundText)
    } else {
      assertEquals(listOf<String>(), textCollectingCheck.foundText)
    }
  }
}

