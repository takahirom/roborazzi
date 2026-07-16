package com.github.takahirom.roborazzi.sample

import android.content.Context
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.Dump
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoboComponent
import com.github.takahirom.roborazzi.RoborazziOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel4, sdk = [35])
class RoboComponentStructureTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val dumpOptions =
    RoborazziOptions(captureType = RoborazziOptions.CaptureType.Dump(takeScreenShot = false))

  @Test
  fun composeNodeExposesStructuredSemantics() {
    composeTestRule.setContent {
      Box(
        Modifier
          .size(48.dp)
          .testTag("target")
          .clickable { }
          .semantics {
            contentDescription = "hello description"
            disabled()
          }
      )
    }

    val node = composeTestRule.onNodeWithTag("target").fetchSemanticsNode()
    val component = RoboComponent.Compose(node, dumpOptions)

    // testTag is promoted to the common surface.
    assertEquals("target", component.testTag)

    // properties mirror appendConfigInfo: non-action, non-flag, non-TestTag entries.
    assertTrue(
      "properties should contain ContentDescription but was ${component.properties}",
      component.properties.containsKey("ContentDescription")
    )
    assertTrue(
      component.properties["ContentDescription"]!!.contains("hello description")
    )
    // TestTag must never leak into the generic properties map.
    assertTrue(!component.properties.containsKey("TestTag"))
    // Flags and actions must not appear as properties.
    assertTrue(!component.properties.containsKey("OnClick"))
    assertTrue(!component.properties.containsKey("Disabled"))

    // actions are action-valued semantics keys.
    assertTrue(
      "actions should contain OnClick but was ${component.actions}",
      component.actions.contains("OnClick")
    )
    // actions are sorted.
    assertEquals(component.actions.sorted(), component.actions)

    // flags include Unit-valued semantics such as Disabled.
    assertTrue(
      "flags should contain Disabled but was ${component.flags}",
      component.flags.contains("Disabled")
    )
    assertEquals(component.flags.sorted(), component.flags)

    // bounds are populated from the laid-out node.
    assertTrue("width should be > 0 but was ${component.bounds.width}", component.bounds.width > 0)
    assertTrue("height should be > 0 but was ${component.bounds.height}", component.bounds.height > 0)
    assertEquals(component.bounds.width, component.width)
    assertEquals(component.bounds.height, component.height)
  }

  @Test
  fun composeMergeDescendantsFlag() {
    composeTestRule.setContent {
      Box(
        Modifier
          .size(48.dp)
          .testTag("merged")
          .semantics(mergeDescendants = true) {
            contentDescription = "merged node"
          }
      )
    }

    val node = composeTestRule.onNodeWithTag("merged").fetchSemanticsNode()
    val component = RoboComponent.Compose(node, dumpOptions)

    assertTrue(
      "flags should contain MergeDescendants but was ${component.flags}",
      component.flags.contains("MergeDescendants")
    )
  }

  @Test
  fun viewExposesStructuredAccessibilityMap() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val textView = TextView(context).apply {
      text = "view text"
      contentDescription = "view description"
      isClickable = true
      setOnClickListener { }
    }

    val component = RoboComponent.View(textView, dumpOptions)

    // properties hold only meaningful, present entries (no always-on booleans).
    assertEquals("view text", component.properties["Text"])
    assertEquals("view description", component.properties["ContentDescription"])
    assertTrue(!component.properties.containsKey("Clickable"))
    assertTrue(!component.properties.containsKey("Enabled"))
    assertTrue(!component.properties.containsKey("Focused"))
    assertTrue(!component.properties.containsKey("ImportantForAccessibility"))

    // Notable boolean states are reported as presence-only flags.
    assertTrue(
      "flags should contain Clickable but was ${component.flags}",
      component.flags.contains("Clickable")
    )
    // An enabled view has no Disabled flag.
    assertTrue(!component.flags.contains("Disabled"))
    assertEquals(component.flags.sorted(), component.flags)

    // A clickable View with a click listener exposes the OnClick action.
    assertTrue(
      "actions should contain OnClick but was ${component.actions}",
      component.actions.contains("OnClick")
    )

    // testTag is a Compose-only concept; Views report null.
    assertNull(component.testTag)
  }

  @Test
  fun disabledNonClickableViewReportsDisabledFlagOnly() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val textView = TextView(context).apply {
      text = "disabled text"
      isEnabled = false
      isClickable = false
    }

    val component = RoboComponent.View(textView, dumpOptions)

    // Disabled state surfaces as a flag; enabled/clickable never emit "false".
    assertTrue(
      "flags should contain Disabled but was ${component.flags}",
      component.flags.contains("Disabled")
    )
    assertTrue(!component.flags.contains("Clickable"))

    // No boolean noise entries in the properties map.
    assertTrue(!component.properties.containsKey("Enabled"))
    assertTrue(!component.properties.containsKey("Clickable"))
    assertTrue(!component.properties.containsKey("Focused"))
    assertTrue(!component.properties.containsKey("ImportantForAccessibility"))

    // A non-clickable view exposes no OnClick action.
    assertTrue(!component.actions.contains("OnClick"))
  }
}
