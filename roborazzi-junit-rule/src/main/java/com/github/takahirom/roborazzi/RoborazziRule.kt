package com.github.takahirom.roborazzi

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.espresso.ViewInteraction
import java.io.File
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement

private val defaultFileProvider: FileProvider =
  { description, folder, fileExtension ->
    File(
      folder.absolutePath,
      DefaultFileNameGenerator.generate(description) + "." + fileExtension
    )
  }

class RoborazziRule private constructor(
  private val captureRoot: CaptureRoot,
  private val options: Options = Options()
) : TestWatcher() {
  /**
   * If you add this annotation to the test, the test will be ignored by roborazzi
   */
  annotation class Ignore

  internal sealed interface CaptureRoot {
    class Compose(
      val composeRule: AndroidComposeTestRule<*, *>,
      val semanticsNodeInteraction: SemanticsNodeInteraction
    ) : CaptureRoot

    class View(val viewInteraction: ViewInteraction) : CaptureRoot
  }

  data class Options(
    val captureType: CaptureType = CaptureType.LastImage(),
    /**
     * capture only when the test fail
     */
    val onlyFail: Boolean = false,
    /**
     * output directory path
     */
    val outputDirectoryPath: String = DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH,

    val outputFileProvider: FileProvider = defaultFileProvider,
    val roborazziOptions: RoborazziOptions = RoborazziOptions(),
  )

  sealed interface CaptureType {
    /**
     * Do not generate images. Just provide the image path and run the test.
     */
    @ExperimentalRoborazziApi
    object None : CaptureType

    /**
     * Generate last images for each test
     */
    class LastImage : CaptureType

    /**
     * Generate images for Each layout change like TestClass_method_0.png for each test
     */
    class AllImage : CaptureType

    /**
     * Generate gif images for each test
     */
    class Gif : CaptureType
  }


  constructor(
    captureRoot: ViewInteraction,
    options: Options = Options()
  ) : this(
    captureRoot = CaptureRoot.View(captureRoot),
    options = options
  )

  constructor(
    composeRule: AndroidComposeTestRule<*, *>,
    captureRoot: SemanticsNodeInteraction,
    options: Options = Options()
  ) : this(
    captureRoot = CaptureRoot.Compose(composeRule, captureRoot),
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
          provideRoborazziContext().setRunnerOverrideDescription(description)
          runTest(base, description, captureRoot)
        } finally {
          provideRoborazziContext().clearRuleOverrideOutputDirectory()
          provideRoborazziContext().clearRuleOverrideRoborazziOptions()
          provideRoborazziContext().clearRuleOverrideFileProvider()
          provideRoborazziContext().clearRunnerOverrideDescription()
        }
      }
    }
  }

  @OptIn(ExperimentalRoborazziApi::class)
  private fun runTest(
    base: Statement,
    description: Description,
    captureRoot: CaptureRoot
  ) {
    val evaluate = {
      try {
        base.evaluate()
      } catch (e: Exception) {
        throw e
      }
    }
    val captureType = options.captureType
    if (!roborazziEnabled()) {
      evaluate()
      return
    }
    if (!roborazziRecordingEnabled() && options.captureType is CaptureType.Gif) {
      // currently, gif compare is not supported
      evaluate()
      return
    }
    if (description.annotations.filterIsInstance<Ignore>().isNotEmpty()) return evaluate()
    val folder = File(options.outputDirectoryPath)
    if (!folder.exists()) {
      folder.mkdirs()
    }

    when (captureType) {
      CaptureType.None -> {
        evaluate()
      }

      is CaptureType.AllImage, is CaptureType.Gif -> {
        val roborazziOptions = when (captureType) {
          is CaptureType.AllImage -> options.roborazziOptions
          is CaptureType.Gif -> options.roborazziOptions
          else -> error("Unsupported captureType")
        }
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
        }
        if (!options.onlyFail || result.result.isFailure) {
          if (captureType is CaptureType.AllImage) {
            result.saveAllImage {
              options.outputFileProvider(description, folder, "png")
            }
          } else {
            val file = options.outputFileProvider(description, folder, "gif")
            result.saveGif(file)
          }
        }
        result.clear()
        result.result.exceptionOrNull()?.let {
          throw it
        }

      }

      is CaptureType.LastImage -> {
        val roborazziOptions = options.roborazziOptions
        val result = runCatching {
          evaluate()
        }
        if (!options.onlyFail || result.isFailure) {
          val outputFile = options.outputFileProvider(description, folder, "png")
          when (captureRoot) {
            is CaptureRoot.Compose -> captureRoot.semanticsNodeInteraction.captureRoboImage(
              file = outputFile,
              roborazziOptions = roborazziOptions
            )

            is CaptureRoot.View -> captureRoot.viewInteraction.captureRoboImage(
              file = outputFile,
              roborazziOptions = roborazziOptions
            )
          }
        }
        result.exceptionOrNull()?.let {
          throw it
        }
      }
    }

  }
}