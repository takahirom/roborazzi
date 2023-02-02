package com.github.takahirom.roborazzi

import androidx.test.espresso.ViewInteraction
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

class RoborazziRule(
  val captureRoot: ViewInteraction,
  val captureOnlyFail: Boolean = false
) : TestWatcher() {
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
        val result = captureRoot.justCaptureRoboGif(
          File(
            folder.absolutePath,
            description.className + "_" + description.methodName + ".gif"
          )
        ) {
          try {
            base.evaluate()
          } catch (e: Exception) {
            isFail = true
            throw e
          }
        }
        if (!captureOnlyFail || isFail) {
          result.save()
        }
        result.result.exceptionOrNull()?.let {
          throw it
        }
      }
    }
  }
}