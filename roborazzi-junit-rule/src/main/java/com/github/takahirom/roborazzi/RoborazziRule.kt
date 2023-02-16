package com.github.takahirom.roborazzi

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.espresso.ViewInteraction
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

internal sealed interface CaptureRoot {
  class Compose(
    val composeRule: AndroidComposeTestRule<*, *>,
    val semanticsNodeInteraction: SemanticsNodeInteraction
  ) : CaptureRoot

  class View(val viewInteraction: ViewInteraction) : CaptureRoot
}

class RoborazziRule private constructor(
  private val captureRoot: CaptureRoot,
  private val captureMode: CaptureMode = CaptureMode()
) : TestWatcher() {
  constructor(
    captureRoot: ViewInteraction,
    captureMode: CaptureMode = CaptureMode()
  ) : this(
    captureRoot = CaptureRoot.View(captureRoot),
    captureMode = captureMode
  )

  constructor(
    composeRule: AndroidComposeTestRule<*, *>,
    captureRoot: SemanticsNodeInteraction,
    captureMode: CaptureMode = CaptureMode()
  ) : this(
    captureRoot = CaptureRoot.Compose(composeRule, captureRoot),
    captureMode = captureMode
  )

  override fun failed(e: Throwable?, description: Description?) {
    super.failed(e, description)
  }

  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        val folder = File(DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH)
        if (!folder.exists()) {
          folder.mkdirs()
        }
        val evaluate = {
          try {
            base.evaluate()
          } catch (e: Exception) {
            throw e
          }
        }
        val result = when (captureRoot) {
          is CaptureRoot.Compose -> captureRoot.semanticsNodeInteraction.captureComposeNode(
            captureRoot.composeRule,
            evaluate
          )
          is CaptureRoot.View -> captureRoot.viewInteraction.captureAndroidView(
            evaluate
          )
        }
        if (!captureMode.onlyFail || result.result.isFailure) {
          when (captureMode.captureType) {
            CaptureType.LastImage -> {
              val file = File(
                folder.absolutePath,
                description.className + "_" + description.methodName + ".png"
              )
              result.saveLastImage(file)
            }
            CaptureType.AllImage -> {
              result.saveAllImage { suffix ->
                File(
                  folder.absolutePath,
                  description.className + "_" + description.methodName + "_" + suffix + ".png"
                )
              }
            }
            CaptureType.Gif -> {
              val file = File(
                folder.absolutePath,
                description.className + "_" + description.methodName + ".gif"
              )
              result.saveGif(file)
            }
          }
        }
        result.clear()
        result.result.exceptionOrNull()?.let {
          throw it
        }
      }
    }
  }
}