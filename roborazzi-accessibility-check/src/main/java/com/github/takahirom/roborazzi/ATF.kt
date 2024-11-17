package com.github.takahirom.roborazzi

import android.graphics.Bitmap
import android.view.View
import androidx.annotation.RequiresApi
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheck
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import com.google.android.apps.common.testing.accessibility.framework.Parameters
import com.google.android.apps.common.testing.accessibility.framework.ViewChecker
import com.google.android.apps.common.testing.accessibility.framework.utils.contrast.BitmapImage
import com.google.common.collect.ImmutableSet
import org.hamcrest.Matcher
import org.hamcrest.Matchers

@RequiresApi(34)
internal fun runAllChecks(
  view: View,
  screenshotBitmap: Bitmap?,
  checks: Set<AccessibilityHierarchyCheck>,
  suppressions: Matcher<in AccessibilityViewCheckResult>,
): List<AccessibilityViewCheckResult> {
  val parameters = Parameters().apply {
    if (screenshotBitmap != null) {
      putScreenCapture(BitmapImage(screenshotBitmap))
    }
    setSaveViewImages(true)
  }

  val viewChecker = ViewChecker().apply {
    setObtainCharacterLocations(true)
  }

  val results = viewChecker.runChecksOnView(ImmutableSet.copyOf(checks), view, parameters)

  return results.filter {
    !suppressions.matches(it)
  }
}

fun ATFAccessibilityChecker.Companion.atf(
  preset: AccessibilityCheckPreset? = AccessibilityCheckPreset.LATEST,
  suppressions: Matcher<in AccessibilityViewCheckResult> = Matchers.not(Matchers.anything()),
) =
  ATFAccessibilityChecker(
    checks = AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(preset),
    suppressions = suppressions,
  )

fun ATFAccessibilityChecker.Companion.atf(
  checks: Set<AccessibilityHierarchyCheck>,
  suppressions: Matcher<in AccessibilityViewCheckResult> = Matchers.not(Matchers.anything()),
) =
  ATFAccessibilityChecker(
    checks = checks,
    suppressions = suppressions,
  )
