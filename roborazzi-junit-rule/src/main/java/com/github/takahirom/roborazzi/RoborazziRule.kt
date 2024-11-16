package com.github.takahirom.roborazzi

import android.annotation.SuppressLint
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.espresso.ViewInteraction
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.shadows.ShadowBuild
import java.io.File

private val defaultFileProvider: FileProvider =
  { description, directory, fileExtension ->
    File(
      directory.absolutePath,
      DefaultFileNameGenerator.generateCountableOutputNameWithDescription(description) + "." + fileExtension
    )
  }

/**
 * This rule is a JUnit rule for roborazzi.
 * This rule is optional. You can use [captureRoboImage] without this rule.
 *
 * This rule have two features.
 * 1. Provide context such as `RoborazziOptions` and `outputDirectoryPath` etc for [captureRoboImage].
 * 2. Capture screenshots for each test when specifying RoborazziRule.options.captureType.
 */
class RoborazziRule private constructor(
  private val captureRoot: CaptureRoot,
  private val options: Options = Options()
) : TestWatcher() {
  init {
    try {
      val clazz = Class.forName("com.github.takahirom.roborazzi.RoborazziAi")
      // RoborazziAi is available
      clazz.getDeclaredMethod("loadRoboAi").invoke(null)
    } catch (ignored: ClassNotFoundException) {
      // RoborazziAi is not available
    } catch (ignored: NoSuchMethodException) {
      // RoborazziAi is not available
    }
  }

  /**
   * If you add this annotation to the test, the test will be ignored by
   * roborazzi's CaptureType.LastImage, CaptureType.AllImage and CaptureType.Gif.
   */
  annotation class Ignore

  data class Options(
    val captureType: CaptureType = CaptureType.None,
    /**
     * output directory path
     */
    val outputDirectoryPath: String = provideRoborazziContext().outputDirectory,

    val outputFileProvider: FileProvider = provideRoborazziContext().fileProvider
      ?: defaultFileProvider,
    val roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  )

  sealed interface CaptureType {
    /**
     * Do not generate images. Just provide the image path to [captureRoboImage].
     */
    object None : CaptureType

    /**
     * Generate last images for each test
     */
    data class LastImage(
      /**
       * capture only when the test fail
       */
      val onlyFail: Boolean = false,
    ) : CaptureType

    /**
     * Generate images for Each layout change like TestClass_method_0.png for each test
     */
    data class AllImage(
      /**
       * capture only when the test fail
       */
      val onlyFail: Boolean = false,
    ) : CaptureType

    /**
     * Generate gif images for each test
     */
    data class Gif(
      /**
       * capture only when the test fail
       */
      val onlyFail: Boolean = false,
    ) : CaptureType
  }

  internal sealed interface CaptureRoot {
    object None : CaptureRoot
    class Compose(
      val composeRule: ComposeTestRule,
      val semanticsNodeInteraction: SemanticsNodeInteraction
    ) : CaptureRoot

    class View(val viewInteraction: ViewInteraction) : CaptureRoot
  }

  constructor(
    captureRoot: ViewInteraction,
    options: Options = Options()
  ) : this(
    captureRoot = CaptureRoot.View(captureRoot),
    options = options
  )

  constructor(
    composeRule: ComposeTestRule,
    captureRoot: SemanticsNodeInteraction,
    options: Options = Options()
  ) : this(
    captureRoot = CaptureRoot.Compose(composeRule, captureRoot),
    options = options
  )

  constructor(
    options: Options = Options()
  ) : this(
    captureRoot = CaptureRoot.None,
    options = options
  )

  override fun failed(e: Throwable?, description: Description?) {
    super.failed(e, description)
  }

  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        try {
          provideRoborazziContext().setRuleOverrideOutputDirectory(options.outputDirectoryPath)
          provideRoborazziContext().setRuleOverrideRoborazziOptions(options.roborazziOptions)
          provideRoborazziContext().setRuleOverrideFileProvider(options.outputFileProvider)
          provideRoborazziContext().setRuleOverrideDescription(description)
          runTest(base, description, captureRoot)
        } finally {
          provideRoborazziContext().clearRuleOverrideOutputDirectory()
          provideRoborazziContext().clearRuleOverrideRoborazziOptions()
          provideRoborazziContext().clearRuleOverrideFileProvider()
          provideRoborazziContext().clearRuleOverrideDescription()
        }
      }
    }
  }

  private fun runTest(
    base: Statement,
    description: Description,
    captureRoot: CaptureRoot
  ) {
    val evaluate: () -> Unit = {
      try {
        val accessibilityValidator: AccessibilityValidator? = findActiveAccessibilityValidator()
        // TODO enable a11y before showing content

        base.evaluate()

        accessibilityValidator?.runAccessibilityChecks(captureRoot)
      } catch (e: Exception) {
        throw e
      }
    }
    val captureType = options.captureType
    if (!options.roborazziOptions.taskType.isEnabled()) {
      evaluate()
      return
    }
    if (!options.roborazziOptions.taskType.isRecording() && options.captureType is CaptureType.Gif) {
      // currently, gif compare is not supported
      evaluate()
      return
    }
    if (description.annotations.filterIsInstance<Ignore>().isNotEmpty()) return evaluate()
    val directory = File(options.outputDirectoryPath)
    if (!directory.exists()) {
      directory.mkdirs()
    }

    val roborazziOptions = provideRoborazziContext().options
    when (captureType) {
      CaptureType.None -> {
        evaluate()
      }

      is CaptureType.AllImage, is CaptureType.Gif -> {
        val result = when (captureRoot) {
          is CaptureRoot.Compose -> captureRoot.semanticsNodeInteraction.captureComposeNode(
            composeRule = captureRoot.composeRule,
            roborazziOptions = roborazziOptions,
            block = evaluate
          )

          is CaptureRoot.View -> captureRoot.viewInteraction.captureAndroidView(
            roborazziOptions = roborazziOptions,
            block = evaluate
          )

          CaptureRoot.None -> {
            error("captureRoot is required for AllImage and Gif")
          }
        }
        val isOnlyFail = when (captureType) {
          is CaptureType.AllImage -> captureType.onlyFail
          is CaptureType.Gif -> captureType.onlyFail
          else -> false
        }
        if (!isOnlyFail || result.result.isFailure) {
          if (captureType is CaptureType.AllImage) {
            result.saveAllImage {
              fileWithRecordFilePathStrategy(DefaultFileNameGenerator.generateFilePath())
            }
          } else {
            val file =
              fileWithRecordFilePathStrategy(DefaultFileNameGenerator.generateFilePath("gif"))
            result.saveGif(file)
          }
        }
        result.clear()
        result.result.exceptionOrNull()?.let {
          throw it
        }

      }

      is CaptureType.LastImage -> {
        val result = runCatching {
          evaluate()
        }
        if (!captureType.onlyFail || result.isFailure) {
          val outputFile =
            fileWithRecordFilePathStrategy(DefaultFileNameGenerator.generateFilePath())
          when (captureRoot) {
            is CaptureRoot.Compose -> captureRoot.semanticsNodeInteraction.captureRoboImage(
              file = outputFile,
              roborazziOptions = roborazziOptions
            )

            is CaptureRoot.View -> captureRoot.viewInteraction.captureRoboImage(
              file = outputFile,
              roborazziOptions = roborazziOptions
            )

            CaptureRoot.None -> {
              error("captureRoot is required for LastImage")
            }
          }
        }
        result.exceptionOrNull()?.let {
          throw it
        }
      }
    }

  }

  @SuppressLint("VisibleForTests")
  private fun AccessibilityValidator.runAccessibilityChecks(
    captureRoot: CaptureRoot,
  ) {
    // TODO remove this once ATF doesn't bail out
    // https://github.com/google/Accessibility-Test-Framework-for-Android/blob/c65cab02b2a845c29c3da100d6adefd345a144e3/src/main/java/com/google/android/apps/common/testing/accessibility/framework/uielement/AccessibilityHierarchyAndroid.java#L667
    ShadowBuild.setFingerprint("roborazzi")

    if (captureRoot is CaptureRoot.Compose) {
      setRunChecksFromRootView(true)
      val view = (captureRoot.semanticsNodeInteraction.fetchSemanticsNode().root as ViewRootForTest).view.rootView

      // Will throw based on configuration
      val results = checkAndReturnResults(view)

      // Report on any warnings in the log output if not failing
      results.forEach { check ->
        when (check.accessibilityHierarchyCheckResult.type) {
          AccessibilityCheckResultType.ERROR -> System.err.println("Error: " + check.explain())
          AccessibilityCheckResultType.WARNING -> System.err.println(
            "Warning: " + check.explain()
          )

          AccessibilityCheckResultType.INFO -> println(
            "Info: " + check.explain()
          )

          else -> {}
        }
      }
      // TODO handle View cases
//    } else if (captureRoot is CaptureRoot.View) {
    }
  }

  private fun AccessibilityViewCheckResult.explain() = "" + accessibilityHierarchyCheckResult.getShortMessage(
    java.util.Locale.getDefault()
  ) + " " + this.element

  private fun findActiveAccessibilityValidator(): AccessibilityValidator? {
    var accessibilityValidator: AccessibilityValidator? = null
    val accessibilityChecks = options.roborazziOptions.recordOptions.accessibilityChecks
    if (accessibilityChecks is RoborazziOptions.AccessibilityChecks.Validate) {
      accessibilityValidator = (accessibilityChecks.checker as? ATFAccessibilityChecker)?.accessibilityValidator
    }
    return accessibilityValidator
  }
}