package com.github.takahirom.roborazzi

import android.annotation.SuppressLint
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ViewRootForTest
import com.github.takahirom.roborazzi.RoborazziRule.ATFAccessibilityChecker
import com.github.takahirom.roborazzi.RoborazziRule.CaptureRoot
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheck
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import com.google.android.apps.common.testing.accessibility.framework.Parameters
import com.google.android.apps.common.testing.accessibility.framework.ViewChecker
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityViewCheckException
import com.google.android.apps.common.testing.accessibility.framework.utils.contrast.BitmapImage
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.robolectric.shadows.ShadowBuild


@SuppressLint("VisibleForTests")
@RequiresApi(34)
internal fun ATFAccessibilityChecker.runAccessibilityChecks(
  captureRoot: CaptureRoot,
  roborazziOptions: RoborazziOptions,
) {
  // TODO remove this once ATF doesn't bail out
  // https://github.com/google/Accessibility-Test-Framework-for-Android/blob/c65cab02b2a845c29c3da100d6adefd345a144e3/src/main/java/com/google/android/apps/common/testing/accessibility/framework/uielement/AccessibilityHierarchyAndroid.java#L667
  ShadowBuild.setFingerprint("roborazzi")

  if (captureRoot is CaptureRoot.Compose) {
    val view = (captureRoot.semanticsNodeInteraction.fetchSemanticsNode().root as ViewRootForTest).view.rootView

    // Will throw based on configuration
    val results = runAllChecks(roborazziOptions, view, captureRoot)

    // Report on any warnings in the log output if not failing
    results.forEach { check ->
      when (check.type) {
        AccessibilityCheckResultType.ERROR -> roborazziErrorLog("Error: $check")
        AccessibilityCheckResultType.WARNING -> roborazziErrorLog(
          "Warning: $check"
        )

        AccessibilityCheckResultType.INFO -> roborazziReportLog(
          "Info: $check"
        )

        else -> {}
      }
    }

    val failures = results.filter { it.type.ordinal <= failureLevel.ordinal }
    if (failures.isNotEmpty()) {
      throw AccessibilityViewCheckException(failures.toMutableList())
    }

    // TODO handle View cases
//    } else if (captureRoot is CaptureRoot.View) {
  }
}

@RequiresApi(34)
internal fun ATFAccessibilityChecker.runAllChecks(
  roborazziOptions: RoborazziOptions,
  view: View,
  captureRoot: CaptureRoot.Compose
): List<AccessibilityViewCheckResult> {
  val screenshot =
    RoboComponent.Compose(captureRoot.semanticsNodeInteraction.fetchSemanticsNode(), roborazziOptions).image

  val parameters = Parameters().apply {
    if (screenshot != null) {
      putScreenCapture(BitmapImage(screenshot))
    }
    setSaveViewImages(true)
  }

  val viewChecker = ViewChecker().apply {
    setObtainCharacterLocations(true)
  }

  val preset = AccessibilityCheckPreset.LATEST
  val checks = AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(preset)

  val results = viewChecker.runChecksOnView(checks, view, parameters)

  return results.filter {
    !suppressions.matches(it)
  }
}

fun ATFAccessibilityChecker.Companion.atf(
  preset: AccessibilityCheckPreset? = AccessibilityCheckPreset.LATEST,
  suppressions: Matcher<in AccessibilityViewCheckResult> = Matchers.not(Matchers.anything()),
  failureLevel: AccessibilityCheckResultType = AccessibilityCheckResultType.ERROR,
) =
  ATFAccessibilityChecker(
    checks = AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(preset),
    suppressions = suppressions,
    failureLevel = failureLevel
  )

fun ATFAccessibilityChecker.Companion.atf(
  checks: Set<AccessibilityHierarchyCheck>,
  suppressions: Matcher<in AccessibilityViewCheckResult> = Matchers.not(Matchers.anything()),
  failureLevel: AccessibilityCheckResultType = AccessibilityCheckResultType.ERROR,
) =
  ATFAccessibilityChecker(
    checks = checks,
    suppressions = suppressions,
    failureLevel = failureLevel
  )
