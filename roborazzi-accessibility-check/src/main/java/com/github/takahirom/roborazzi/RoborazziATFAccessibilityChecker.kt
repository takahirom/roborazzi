package com.github.takahirom.roborazzi

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
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
fun SemanticsNodeInteraction.checkRoboAccessibility(
  roborazziATFAccessibilityCheckOptions: RoborazziATFAccessibilityCheckOptions = provideATFAccessibilityOptionsOrCreateDefault(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  roborazziATFAccessibilityCheckOptions.checker.runAccessibilityChecks(
    checkNode = RoborazziATFAccessibilityChecker.CheckNode.Compose(this),
    roborazziOptions = roborazziOptions,
    failureLevel = roborazziATFAccessibilityCheckOptions.failureLevel,
  )
}

@ExperimentalRoborazziApi
fun ViewInteraction.checkRoboAccessibility(
  roborazziATFAccessibilityCheckOptions: RoborazziATFAccessibilityCheckOptions = provideATFAccessibilityOptionsOrCreateDefault(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  roborazziATFAccessibilityCheckOptions.checker.runAccessibilityChecks(
    checkNode = RoborazziATFAccessibilityChecker.CheckNode.View(this),
    roborazziOptions = roborazziOptions,
    failureLevel = roborazziATFAccessibilityCheckOptions.failureLevel,
  )
}

private fun provideATFAccessibilityOptionsOrCreateDefault(): RoborazziATFAccessibilityCheckOptions =
  ((provideRoborazziContext().roborazziAccessibilityOptions as? RoborazziATFAccessibilityCheckOptions)
    ?: RoborazziATFAccessibilityCheckOptions())

@ExperimentalRoborazziApi
data class RoborazziATFAccessibilityCheckOptions(
  val checker: RoborazziATFAccessibilityChecker = RoborazziATFAccessibilityChecker(),
  val failureLevel: RoborazziATFAccessibilityChecker.CheckLevel = RoborazziATFAccessibilityChecker.CheckLevel.Error,
) : RoborazziAccessibilityOptions

@ExperimentalRoborazziApi
data class RoborazziATFAccessibilityChecker(
  val checks: Set<AccessibilityHierarchyCheck> = AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(
    AccessibilityCheckPreset.LATEST
  ),
  val suppressions: Matcher<in AccessibilityViewCheckResult> = Matchers.not(Matchers.anything()),
) : RoborazziAccessibilityOptions {
  constructor(
    preset: AccessibilityCheckPreset,
    suppressions: Matcher<in AccessibilityViewCheckResult> = Matchers.not(Matchers.anything()),
  ) : this(
    checks = AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(preset),
    suppressions = suppressions,
  )

  internal sealed interface CheckNode {
    data class View(val viewInteraction: ViewInteraction) : CheckNode
    data class Compose(val semanticsNodeInteraction: SemanticsNodeInteraction) : CheckNode
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


  @SuppressLint("VisibleForTests")
  internal fun runAccessibilityChecks(
    checkNode: CheckNode,
    roborazziOptions: RoborazziOptions,
    failureLevel: CheckLevel,
  ) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      roborazziErrorLog("Skipping accessibilityChecks on API " + Build.VERSION.SDK_INT + "(< ${Build.VERSION_CODES.UPSIDE_DOWN_CAKE})")
      return
    }

    if (!canScreenshot()) {
      roborazziErrorLog("Skipping accessibilityChecks on GraphicsMode.Mode.LEGACY")
      return
    }

    if (Build.FINGERPRINT == "robolectric") {
      // TODO remove this once ATF doesn't bail out
      // https://github.com/google/Accessibility-Test-Framework-for-Android/blob/c65cab02b2a845c29c3da100d6adefd345a144e3/src/main/java/com/google/android/apps/common/testing/accessibility/framework/uielement/AccessibilityHierarchyAndroid.java#L667
      ShadowBuild.setFingerprint("roborazzi")
    }

    if (checkNode is CheckNode.Compose) {
      val view =
        (checkNode.semanticsNodeInteraction.fetchSemanticsNode().root as ViewRootForTest).view

      // Will throw based on configuration

      val screenshot: Bitmap? = RoboComponent.Compose(
        node = checkNode.semanticsNodeInteraction.fetchSemanticsNode(),
        roborazziOptions = roborazziOptions
      ).image

      val results = runAllChecks(
        view = view, screenshotBitmap = screenshot, checks = checks, suppressions = suppressions
      )

      reportResults(results, failureLevel)

    } else if (checkNode is CheckNode.View) {
      val viewInteraction = checkNode.viewInteraction
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

    return results.applySuppressions(suppressions)
  }

  private fun List<AccessibilityViewCheckResult>.applySuppressions(
    suppressions: Matcher<in AccessibilityViewCheckResult>
  ): List<AccessibilityViewCheckResult> {
    val ranTypes = listOf(
      AccessibilityCheckResultType.ERROR,
      AccessibilityCheckResultType.WARNING,
      AccessibilityCheckResultType.INFO
    )

    return map { checkResult ->
      if (suppressions.matches(checkResult) && ranTypes.contains(checkResult.type)) {
        checkResult.suppressedResultCopy
      } else {
        checkResult
      }
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
        AccessibilityCheckResultType.SUPPRESSED -> roborazziReportLog(
          "Suppressed: $check"
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
data class AccessibilityCheckAfterTestStrategy(
  val accessibilityOptionsFactory: () -> RoborazziATFAccessibilityCheckOptions = { provideATFAccessibilityOptionsOrCreateDefault() },
) : RoborazziRule.AccessibilityCheckStrategy {
  override fun runAccessibilityChecks(
    captureRoot: CaptureRoot, roborazziOptions: RoborazziOptions
  ) {
    val accessibilityOptions = accessibilityOptionsFactory()
    accessibilityOptions
      .checker
      .runAccessibilityChecks(
        checkNode = when (captureRoot) {
          is CaptureRoot.Compose -> RoborazziATFAccessibilityChecker.CheckNode.Compose(
            semanticsNodeInteraction = captureRoot.semanticsNodeInteraction
          )

          CaptureRoot.None -> return
          is CaptureRoot.View -> RoborazziATFAccessibilityChecker.CheckNode.View(
            viewInteraction = captureRoot.viewInteraction
          )
        },
        roborazziOptions = roborazziOptions,
        failureLevel = accessibilityOptions.failureLevel,
      )
  }
}
