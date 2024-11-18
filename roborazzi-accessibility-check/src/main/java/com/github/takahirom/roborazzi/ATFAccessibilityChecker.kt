package com.github.takahirom.roborazzi

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ViewRootForTest
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import com.github.takahirom.roborazzi.RoborazziRule.CaptureRoot
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheck
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import com.google.android.apps.common.testing.accessibility.framework.Parameters
import com.google.android.apps.common.testing.accessibility.framework.ViewChecker
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityViewCheckException
import com.google.android.apps.common.testing.accessibility.framework.utils.contrast.BitmapImage
import com.google.common.collect.ImmutableSet
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.robolectric.shadows.ShadowBuild

@ExperimentalRoborazziApi
data class ATFAccessibilityChecker(
  val checks: Set<AccessibilityHierarchyCheck> = AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(
    AccessibilityCheckPreset.LATEST
  ),
  val suppressions: Matcher<in AccessibilityViewCheckResult> = Matchers.not(Matchers.anything()),
) {
  constructor(
    preset: AccessibilityCheckPreset,
    suppressions: Matcher<in AccessibilityViewCheckResult> = Matchers.not(Matchers.anything()),
  ) : this(
    checks = AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(preset),
    suppressions = suppressions,
  )


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

  @SuppressLint("VisibleForTests")
  internal fun runAccessibilityChecks(
    captureRoot: CaptureRoot,
    roborazziOptions: RoborazziOptions,
    failureLevel: CheckLevel,
  ) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      roborazziErrorLog("Skipping accessibilityChecks on API " + Build.VERSION.SDK_INT + "(< ${Build.VERSION_CODES.UPSIDE_DOWN_CAKE})")
      return
    }

    if (Build.FINGERPRINT == "robolectric") {
      // TODO remove this once ATF doesn't bail out
      // https://github.com/google/Accessibility-Test-Framework-for-Android/blob/c65cab02b2a845c29c3da100d6adefd345a144e3/src/main/java/com/google/android/apps/common/testing/accessibility/framework/uielement/AccessibilityHierarchyAndroid.java#L667
      ShadowBuild.setFingerprint("roborazzi")
    }

    if (captureRoot is CaptureRoot.Compose) {
      val view = (captureRoot.semanticsNodeInteraction.fetchSemanticsNode().root as ViewRootForTest).view

      // Will throw based on configuration

      val screenshot: Bitmap? = RoboComponent.Compose(
        node = captureRoot.semanticsNodeInteraction.fetchSemanticsNode(), roborazziOptions = roborazziOptions
      ).image

      val results = runAllChecks(
        view = view, screenshotBitmap = screenshot, checks = checks, suppressions = suppressions
      )

      reportResults(results, failureLevel)

    } else if (captureRoot is CaptureRoot.View) {
      val viewInteraction = captureRoot.viewInteraction
      // Use perform to get the view
      viewInteraction.perform(object : ViewAction {
        override fun getDescription(): String {
          return "Run accessibility checks"
        }

        override fun getConstraints(): Matcher<View> {
          return Matchers.any(View::class.java)
        }

        override fun perform(uiController: UiController?, view: View?) {
          if (view == null) {
            throw IllegalStateException("View is null")
          }
          val results = runAllChecks(
            view = view,
            screenshotBitmap = RoboComponent.View(view, roborazziOptions).image,
            checks = checks,
            suppressions = suppressions
          )
          reportResults(results, failureLevel)
        }
      })
    }
  }

  private fun reportResults(
    results: List<AccessibilityViewCheckResult>, failureLevel: CheckLevel
  ) {
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
  }

  companion object
}

@ExperimentalRoborazziApi
enum class CheckLevel(private vararg val failedTypes: AccessibilityCheckResultType) {
  Error(AccessibilityCheckResultType.ERROR),

  Warning(
    AccessibilityCheckResultType.ERROR, AccessibilityCheckResultType.WARNING
  ),

  LogOnly;

  fun isFailure(type: AccessibilityCheckResultType): Boolean = failedTypes.contains(type)
}

@ExperimentalRoborazziApi
data class AccessibilityChecksValidate(
  val checker: ATFAccessibilityChecker = ATFAccessibilityChecker(),
  val failureLevel: CheckLevel = CheckLevel.Error,
) : RoborazziRule.AccessibilityChecks {
  override fun runAccessibilityChecks(
    captureRoot: CaptureRoot, roborazziOptions: RoborazziOptions
  ) {
    checker.runAccessibilityChecks(
      captureRoot = captureRoot,
      roborazziOptions = roborazziOptions,
      failureLevel = failureLevel,
    )
  }
}
