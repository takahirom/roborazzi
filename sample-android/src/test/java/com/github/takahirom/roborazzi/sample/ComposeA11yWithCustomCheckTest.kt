package com.github.takahirom.roborazzi.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.AccessibilityCheckAfterTestStrategy
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.RoborazziRule.CaptureType
import com.github.takahirom.roborazzi.RoborazziRule.Options
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType.ERROR
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType.INFO
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType.NOT_RUN
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils.matchesElements
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheck
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheckResult
import com.google.android.apps.common.testing.accessibility.framework.HashMapResultMetadata
import com.google.android.apps.common.testing.accessibility.framework.Parameters
import com.google.android.apps.common.testing.accessibility.framework.ResultMetadata
import com.google.android.apps.common.testing.accessibility.framework.matcher.ElementMatchers.withTestTag
import com.google.android.apps.common.testing.accessibility.framework.uielement.AccessibilityHierarchy
import com.google.android.apps.common.testing.accessibility.framework.uielement.ViewHierarchyElement
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Test demonstrating a completely custom ATF Check. Expected to be a niche usecase, but critical when required.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel4, sdk = [35])
class ComposeA11yWithCustomCheckTest {
  @Suppress("DEPRECATION")
  @get:Rule(order = Int.MIN_VALUE)
  var thrown: ExpectedException = ExpectedException.none()

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  val taskType: RoborazziTaskType = roborazziSystemPropertyTaskType()

  @get:Rule
  val roborazziRule = RoborazziRule(
    composeRule = composeTestRule,
    captureRoot = composeTestRule.onRoot(),
    options = Options(
      captureType = CaptureType.LastImage(),
      roborazziAccessibilityOptions = RoborazziATFAccessibilityCheckOptions(
        checker = RoborazziATFAccessibilityChecker(
          checks = setOf(NoRedTextCheck()),
          suppressions = matchesElements(withTestTag("suppress"))
        ),
        failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Warning
      ),
      accessibilityCheckStrategy = AccessibilityCheckAfterTestStrategy(),
    )
  )

  /**
   * Custom Check that demonstrates accessing the screenshot and element data.
   */
  class NoRedTextCheck : CustomAccessibilityHierarchyCheck("No Red Text") {
    override fun runCheckOnHierarchy(
      hierarchy: AccessibilityHierarchy,
      element: ViewHierarchyElement?,
      parameters: Parameters?
    ): List<AccessibilityHierarchyCheckResult> {
      return getElementsToEvaluate(element, hierarchy).map { childElement ->
        val textColors = primaryTextColors(childElement, parameters)

        if (textColors == null) {
          this.result(childElement, NOT_RUN, 1, null)
        } else if (textColors.find { it.isMostlyRed() } != null) {
          result(childElement, ERROR, 3, textColors)
        } else {
          result(childElement, INFO, 3, textColors)
        }
      }
    }

    private fun primaryTextColors(
      childElement: ViewHierarchyElement,
      parameters: Parameters?
    ): Set<Color>? = if (childElement.text == null) {
      null
    } else if (childElement.isVisibleToUser != true) {
      null
    } else {
      val textColor = childElement.textColor
      if (textColor != null) {
        setOf(Color(textColor))
      } else {
        val screenCapture = parameters?.screenCapture
        val textCharacterLocations = childElement.textCharacterLocations

        if (screenCapture == null || textCharacterLocations.isEmpty()) {
          null
        } else {
          textCharacterLocations.flatMap { rect ->
            screenCapture.crop(rect.left, rect.top, rect.width, rect.height).pixels.asSequence()
          }.distinct().map { Color(it) }.toSet()
        }
      }
    }

    private fun Color.isMostlyRed(): Boolean {
      return red > 0.8f && blue < 0.2f && green < 0.2f
    }
  }

  @Test
  fun redText() {
    if (taskType.isEnabled()) {
      thrown.expectMessage("NoRedTextCheck")
    }

    composeTestRule.setContent {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(100.dp).background(Color.DarkGray)) {
          Text("Something red and inappropriate", color = Color.Red)
        }
      }
    }
  }

  @Test
  fun normalText() {
    composeTestRule.setContent {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(100.dp).background(Color.White)) {
          Text("Something boring and black", color = Color.Black)
        }
      }
    }
  }
}

// TODO fix after https://github.com/google/Accessibility-Test-Framework-for-Android/issues/64
class CustomAccessibilityHierarchyCheckResult(
  val checkClass: Class<out AccessibilityHierarchyCheck>,
  type: AccessibilityCheckResultType,
  element: ViewHierarchyElement?,
  resultId: Int,
  metadata: ResultMetadata?
) : AccessibilityHierarchyCheckResult(checkClass, type, element, resultId, metadata) {
  override fun getMessage(locale: Locale?): CharSequence =
    (checkClass.getDeclaredConstructor().newInstance()).getMessageForResult(locale, this)
}

abstract class CustomAccessibilityHierarchyCheck(
  val name: String
) : AccessibilityHierarchyCheck() {
    override fun getHelpTopic(): String? = null

    override fun getCategory(): Category = Category.IMPLEMENTATION

    override fun getTitleMessage(locale: Locale): String = name

    override fun getMessageForResultData(locale: Locale, p1: Int, metadata: ResultMetadata?): String =
      "$name $metadata"

    override fun getShortMessageForResultData(locale: Locale, p1: Int, metadata: ResultMetadata?): String =
      "$name $metadata"

  protected fun result(
    childElement: ViewHierarchyElement?,
    result: AccessibilityCheckResultType,
    resultId: Int,
    textColors: Iterable<Color>?
  ) = CustomAccessibilityHierarchyCheckResult(
    this::class.java,
    result,
    childElement,
    resultId,
    HashMapResultMetadata().apply {
      if (textColors != null) {
        putString("textColors", textColors.joinToString { "0x${it.toArgb().toUInt().toString(16)}" })
      }
    }
  )
}

