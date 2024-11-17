package com.github.takahirom.roborazzi

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.ui.platform.ViewRootForTest
import com.github.takahirom.roborazzi.RoborazziRule.CaptureRoot
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheck
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityViewCheckException
import org.hamcrest.Matcher
import org.robolectric.shadows.ShadowBuild

@ExperimentalRoborazziApi
data class ATFAccessibilityChecker(
  val checks: Set<AccessibilityHierarchyCheck>,
  val suppressions: Matcher<in AccessibilityViewCheckResult>,
) {
  @SuppressLint("VisibleForTests")
  fun runAccessibilityChecks(
    captureRoot: CaptureRoot,
    roborazziOptions: RoborazziOptions,
    failureLevel: CheckLevel,
  ) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      roborazziErrorLog("Skipping accessibilityChecks on API " + Build.VERSION.SDK_INT + "(< ${Build.VERSION_CODES.UPSIDE_DOWN_CAKE})")
      return
    }
    // TODO remove this once ATF doesn't bail out
    // https://github.com/google/Accessibility-Test-Framework-for-Android/blob/c65cab02b2a845c29c3da100d6adefd345a144e3/src/main/java/com/google/android/apps/common/testing/accessibility/framework/uielement/AccessibilityHierarchyAndroid.java#L667
    ShadowBuild.setFingerprint("roborazzi")

    if (captureRoot is CaptureRoot.Compose) {
      val view =
        (captureRoot.semanticsNodeInteraction.fetchSemanticsNode().root as ViewRootForTest).view.rootView

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

      val failures = results.filter { failureLevel.isFailure(it.type) }
      if (failures.isNotEmpty()) {
        throw AccessibilityViewCheckException(failures.toMutableList())
      }

      // TODO handle View cases
//    } else if (captureRoot is CaptureRoot.View) {
    }
  }

  companion object
}

enum class CheckLevel(private vararg val failedTypes: AccessibilityCheckResultType) {
  Error(AccessibilityCheckResultType.ERROR),

  Warning(
    AccessibilityCheckResultType.ERROR,
    AccessibilityCheckResultType.WARNING
  ),

  LogOnly;

  fun isFailure(type: AccessibilityCheckResultType): Boolean = failedTypes.contains(type)
}

data class AccessibilityChecksValidate(
  val checker: ATFAccessibilityChecker = ATFAccessibilityChecker.atf(),
  val failureLevel: CheckLevel = CheckLevel.Error,
) : RoborazziRule.AccessibilityChecks {
  override fun runAccessibilityChecks(
    captureRoot: CaptureRoot,
    roborazziOptions: RoborazziOptions
  ) {
    checker.runAccessibilityChecks(
      captureRoot = captureRoot,
      roborazziOptions = roborazziOptions,
      failureLevel = failureLevel,
    )
  }
}
