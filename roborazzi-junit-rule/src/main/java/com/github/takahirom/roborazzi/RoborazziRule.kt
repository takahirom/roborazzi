package com.github.takahirom.roborazzi

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.espresso.ViewInteraction
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement
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
  private val ruleCaptureRoot: RuleCaptureRoot,
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
    val roborazziAccessibilityOptions: RoborazziAccessibilityOptions = provideRoborazziContext().roborazziAccessibilityOptions,
    val accessibilityCheckStrategy: AccessibilityCheckStrategy = AccessibilityCheckStrategy.None,
  ) {
    // Stable parameters
    constructor(
      captureType: CaptureType = CaptureType.None,
      /**
       * output directory path
       */
      outputDirectoryPath: String = provideRoborazziContext().outputDirectory,

      outputFileProvider: FileProvider = provideRoborazziContext().fileProvider
        ?: defaultFileProvider,
      roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
    ): this(
      captureType = captureType,
      outputDirectoryPath = outputDirectoryPath,
      outputFileProvider = outputFileProvider,
      roborazziOptions = roborazziOptions,
      roborazziAccessibilityOptions = provideRoborazziContext().roborazziAccessibilityOptions,
      accessibilityCheckStrategy = AccessibilityCheckStrategy.None
    )
  }

  @ExperimentalRoborazziApi
  interface AccessibilityCheckStrategy {
    fun afterScreenshot(ruleCaptureRoot: RuleCaptureRoot, roborazziOptions: RoborazziOptions) {}
    fun afterTest(ruleCaptureRoot: RuleCaptureRoot, roborazziOptions: RoborazziOptions) {}

    // Use `roborazzi-accessibility-check`'s [com.github.takahirom.roborazzi.AccessibilityCheckAfterTestStrategy]
    data object None : AccessibilityCheckStrategy
  }

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

  @InternalRoborazziApi
  sealed interface RuleCaptureRoot {
    object None : RuleCaptureRoot
    class Compose(
      val composeRule: ComposeTestRule,
      val semanticsNodeInteraction: SemanticsNodeInteraction
    ) : RuleCaptureRoot

    class View(val viewInteraction: ViewInteraction) : RuleCaptureRoot
  }

  constructor(
    captureRoot: ViewInteraction,
    options: Options = Options()
  ) : this(
    ruleCaptureRoot = RuleCaptureRoot.View(captureRoot),
    options = options
  )

  constructor(
    composeRule: ComposeTestRule,
    captureRoot: SemanticsNodeInteraction,
    options: Options = Options()
  ) : this(
    ruleCaptureRoot = RuleCaptureRoot.Compose(composeRule, captureRoot),
    options = options
  )

  constructor(
    options: Options = Options()
  ) : this(
    ruleCaptureRoot = RuleCaptureRoot.None,
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
          provideRoborazziContext().setRuleOverrideAccessibilityOptions(options.roborazziAccessibilityOptions)
          runTest(base, description, ruleCaptureRoot)
        } finally {
          provideRoborazziContext().clearRuleOverrideOutputDirectory()
          provideRoborazziContext().clearRuleOverrideRoborazziOptions()
          provideRoborazziContext().clearRuleOverrideFileProvider()
          provideRoborazziContext().clearRuleOverrideDescription()
          provideRoborazziContext().clearRuleOverrideAccessibilityOptions()
        }
      }
    }
  }

  private fun runTest(
    base: Statement,
    description: Description,
    ruleCaptureRoot: RuleCaptureRoot
  ) {
    val evaluate: () -> Unit = {
      try {
        base.evaluate()
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
        val result = when (ruleCaptureRoot) {
          is RuleCaptureRoot.Compose -> ruleCaptureRoot.semanticsNodeInteraction.captureComposeNode(
            composeRule = ruleCaptureRoot.composeRule,
            roborazziOptions = roborazziOptions,
            block = evaluate,
            onEach = {
              options.accessibilityCheckStrategy.afterScreenshot(
                ruleCaptureRoot = ruleCaptureRoot,
                roborazziOptions = options.roborazziOptions
              )
            },
          )

          is RuleCaptureRoot.View -> ruleCaptureRoot.viewInteraction.captureAndroidView(
            roborazziOptions = roborazziOptions,
            block = evaluate,
            onEach = {
              options.accessibilityCheckStrategy.afterScreenshot(
                ruleCaptureRoot = ruleCaptureRoot,
                roborazziOptions = options.roborazziOptions
              )
            },
          )

          RuleCaptureRoot.None -> {
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

          options.accessibilityCheckStrategy.afterTest(
            ruleCaptureRoot = ruleCaptureRoot,
            roborazziOptions = options.roborazziOptions
          )
        }
        if (!captureType.onlyFail || result.isFailure) {
          val outputFile =
            fileWithRecordFilePathStrategy(DefaultFileNameGenerator.generateFilePath())
          when (ruleCaptureRoot) {
            is RuleCaptureRoot.Compose -> ruleCaptureRoot.semanticsNodeInteraction.captureRoboImage(
              file = outputFile,
              roborazziOptions = roborazziOptions
            )

            is RuleCaptureRoot.View -> ruleCaptureRoot.viewInteraction.captureRoboImage(
              file = outputFile,
              roborazziOptions = roborazziOptions
            )

            RuleCaptureRoot.None -> {
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
}