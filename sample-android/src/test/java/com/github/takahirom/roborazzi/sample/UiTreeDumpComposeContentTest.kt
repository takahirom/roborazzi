package com.github.takahirom.roborazzi.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.UiTreeDumpOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The compose content path `captureRoboImage { content() }` hands the
 * AndroidComposeView to the tree traversal as the capture root. That view is not
 * an AbstractComposeView, so the traversal used to stop before reaching any
 * semantics node and the sidecar held only the root view shell.
 * https://github.com/takahirom/roborazzi/issues/913
 *
 * Deliberately no compose test rule here: with one in place a second window
 * exists, the capture takes the multiple-windows path down from the DecorView,
 * and the traversal reaches the semantics tree through a real ComposeView -
 * which hides the bug this test is about.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel4, sdk = [35])
class UiTreeDumpComposeContentTest {

  @Test
  fun dumpsComposeSemanticsForComposeContentCapture() {
    val prefix =
      "${roborazziSystemPropertyOutputDirectory()}/${this::class.qualifiedName}.composeContent"
    val imageFile = File("$prefix.png")
    val sidecarFile = File("$prefix.uitree.json")
    val annotatedFile = File("$prefix.annotated.png")
    listOf(imageFile, sidecarFile, annotatedFile).forEach { it.delete() }

    captureRoboImage(
      file = imageFile,
      roborazziOptions = RoborazziOptions(
        taskType = RoborazziTaskType.Record,
        uiTreeDumpOptions = UiTreeDumpOptions(),
      ),
    ) {
      Column {
        Text(
          text = "Login",
          modifier = Modifier
            .testTag("login_button")
            .size(120.dp)
            .clickable { }
        )
      }
    }

    assertTrue("sidecar not found: ${sidecarFile.absolutePath}", sidecarFile.exists())
    val json = sidecarFile.readText()

    assertTrue("expected compose nodes in:\n$json", json.contains("\"type\": \"compose\""))
    assertTrue("expected the testTag in:\n$json", json.contains("\"login_button\""))
    assertTrue("expected the Text property in:\n$json", json.contains("\"Text\": \"[Login]\""))
    assertTrue("expected the OnClick action in:\n$json", json.contains("\"OnClick\""))

    listOf(imageFile, sidecarFile, annotatedFile).forEach { it.delete() }
  }
}
