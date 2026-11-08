@file:OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)

package com.github.takahirom.roborazzi.sample

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.AccessibilityLegend
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoboComponent
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.accessibilityLegendText
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.fetchRobolectricWindowRoots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel4, sdk = [35])
class AccessibilityLegendTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val legendOptions = RoborazziOptions(
    captureType = RoborazziOptions.CaptureType.AccessibilityLegend(),
  )

  @Test
  fun compose() {
    composeTestRule.setContent { ComposeScreen() }

    composeTestRule.onRoot().captureRoboImage(roborazziOptions = legendOptions)
  }

  @Test
  @Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet)
  fun composeOnLandscapeTablet() {
    composeTestRule.setContent { ComposeScreen() }

    composeTestRule.onRoot().captureRoboImage(roborazziOptions = legendOptions)
  }

  @Test
  fun composeLegendText() {
    composeTestRule.setContent { ComposeScreen() }

    val node = composeTestRule.onRoot().fetchSemanticsNode()
    val root = RoboComponent.Compose(node, RoborazziOptions())

    assertEquals(
      """
      1. "Sound settings" · Heading
      2. "Volume"
      3. ⚠ NO LABEL · Slider
         value: 30 of 0–100
      4. "Email, Invalid address" · EditText
         value: "anna@"
         ⚠ error: "Invalid address"
      5. "Save passwords" · Switch · checked
      6. "Downloaded 3 of 5 files"
         live region: Polite, changes are announced
      7. "Unread only"
      8. "Inbox" · Tab · selected
      9. "Sent" · Tab
      [pane "Filters": announced when shown]
      """.trimIndent(),
      accessibilityLegendText(root),
    )
  }

  @Test
  fun composeLegendTextFollowsTraversalIndex() {
    composeTestRule.setContent {
      Column {
        Text(text = "Read second", modifier = Modifier.semantics { traversalIndex = 1f })
        Text(text = "Read first")
      }
    }

    val node = composeTestRule.onRoot().fetchSemanticsNode()
    val root = RoboComponent.Compose(node, RoborazziOptions())

    assertEquals(
      """
      1. "Read first"
      2. "Read second"
      """.trimIndent(),
      accessibilityLegendText(root),
    )
  }

  @Test
  fun captureRestoresAccessibilityState() {
    composeTestRule.setContent { ComposeScreen() }
    val manager = composeTestRule.activity.getSystemService(AccessibilityManager::class.java)
    assertFalse(manager.isEnabled)

    composeTestRule.onRoot().captureRoboImage(
      filePath = "build/outputs/roborazzi/accessibility-legend-restore.png",
      roborazziOptions = legendOptions,
    )

    assertFalse(manager.isEnabled)
    assertFalse(manager.isTouchExplorationEnabled)
    assertEquals(
      emptyList<Any>(),
      manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK),
    )
  }

  @Test
  @Config(sdk = [25])
  fun composeLegendFailsBelowApi26() {
    composeTestRule.setContent { ComposeScreen() }

    val node = composeTestRule.onRoot().fetchSemanticsNode()
    val error = assertThrows(IllegalStateException::class.java) {
      accessibilityLegendText(RoboComponent.Compose(node, RoborazziOptions()))
    }
    assertTrue(error.message!!.contains("API 26"))
  }

  @Test
  fun view() {
    composeTestRule.runOnUiThread { composeTestRule.activity.setContentView(viewScreen()) }

    onView(isRoot()).captureRoboImage(roborazziOptions = legendOptions)
  }

  @Test
  fun viewLegendText() {
    composeTestRule.runOnUiThread { composeTestRule.activity.setContentView(viewScreen()) }
    composeTestRule.waitForIdle()

    val content = composeTestRule.activity.findViewById<ViewGroup>(android.R.id.content)
    val root = RoboComponent.View(content.getChildAt(0), RoborazziOptions())

    assertEquals(
      """
      1. "Account" · Heading
      2. "SEND" · Button
      3. ⚠ NO LABEL · Button
      4. "Remember me" · Checkbox · not checked
      5. "Email" · EditText
         ⚠ error: "Required"
      """.trimIndent(),
      accessibilityLegendText(root),
    )
  }

  @Test
  fun dialog() {
    composeTestRule.setContent { DialogScreen() }

    captureScreenRoboImage(roborazziOptions = legendOptions)
  }

  @Test
  fun dialogLegendText() {
    composeTestRule.setContent { DialogScreen() }
    composeTestRule.waitForIdle()

    val screen = RoboComponent.Screen(
      rootsOrderByDepth = fetchRobolectricWindowRoots(),
      roborazziOptions = RoborazziOptions(),
    )

    assertEquals(
      """
      1. "Inbox"
      2. "Delete the message?"
      3. "Delete" · Button
      """.trimIndent(),
      accessibilityLegendText(screen),
    )
  }

  private fun viewScreen(): LinearLayout {
    val activity = composeTestRule.activity
    return LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      addView(TextView(activity).apply {
        text = "Account"
        textSize = 24f
        isAccessibilityHeading = true
      })
      addView(Button(activity).apply { text = "Send" })
      addView(ImageButton(activity).apply { setImageResource(android.R.drawable.ic_menu_share) })
      addView(CheckBox(activity).apply { text = "Remember me" })
      addView(EditText(activity).apply {
        hint = "Email"
        error = "Required"
      })
    }
  }
}

@androidx.compose.runtime.Composable
private fun DialogScreen() {
  MaterialTheme {
    Surface {
      Text(text = "Inbox", modifier = Modifier.padding(16.dp))
    }
    Dialog(onDismissRequest = {}) {
      Surface(shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(24.dp)) {
          Text(text = "Delete the message?")
          TextButton(onClick = {}) { Text(text = "Delete") }
        }
      }
    }
  }
}

@androidx.compose.runtime.Composable
private fun ComposeScreen() {
  MaterialTheme {
    Surface {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = "Sound settings",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.semantics { heading() },
        )
        Text(text = "Volume")
        Slider(value = 30f, onValueChange = {}, valueRange = 0f..100f, steps = 9)
        OutlinedTextField(
          value = "anna@",
          onValueChange = {},
          label = { Text(text = "Email") },
          isError = true,
          supportingText = { Text(text = "Invalid address") },
          modifier = Modifier
            .fillMaxWidth()
            .semantics { error("Invalid address") },
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = true, role = Role.Switch, onValueChange = {})
            .padding(vertical = 8.dp),
        ) {
          Text(text = "Save passwords", modifier = Modifier.weight(1f))
          Switch(checked = true, onCheckedChange = null)
        }
        Text(
          text = "Downloaded 3 of 5 files",
          modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Surface(
          tonalElevation = 4.dp,
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics { paneTitle = "Filters" },
        ) {
          Text(text = "Unread only", modifier = Modifier.padding(8.dp))
        }
        TabRow(selectedTabIndex = 0) {
          listOf("Inbox", "Sent").forEachIndexed { index, title ->
            Tab(selected = index == 0, onClick = {}, text = { Text(text = title) })
          }
        }
      }
    }
  }
}
