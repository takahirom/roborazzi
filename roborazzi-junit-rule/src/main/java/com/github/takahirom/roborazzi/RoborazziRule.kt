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
  private val captureOnlyFail: Boolean
) : TestWatcher() {
  constructor(
    captureRoot: ViewInteraction,
    captureOnlyFail: Boolean = false
  ) : this(
    CaptureRoot.View(captureRoot),
    captureOnlyFail
  )

  constructor(
    composeRule: AndroidComposeTestRule<*, *>,
    captureRoot: SemanticsNodeInteraction,
    captureOnlyFail: Boolean = false
  ) : this(
    CaptureRoot.Compose(composeRule, captureRoot),
    captureOnlyFail
  )

  override fun failed(e: Throwable?, description: Description?) {
    super.failed(e, description)
  }

  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        val folder = File("build/outputs/roborazzi")
        if (!folder.exists()) {
          folder.mkdirs()
        }
        var isFail = false
        val file = File(
          folder.absolutePath,
          description.className + "_" + description.methodName + ".gif"
        )
        val evaluate = {
          try {
            base.evaluate()
          } catch (e: Exception) {
            isFail = true
            throw e
          }
        }
        val result = when (captureRoot) {
          is CaptureRoot.Compose -> captureRoot.semanticsNodeInteraction.justCaptureRoboGif(
            captureRoot.composeRule,
            file, evaluate
          )
          is CaptureRoot.View -> captureRoot.viewInteraction.justCaptureRoboGif(
            file, evaluate
          )
        }
        if (!captureOnlyFail || isFail) {
          result.save()
        }
        result.clear()
        result.result.exceptionOrNull()?.let {
          throw it
        }
      }
    }
  }
}