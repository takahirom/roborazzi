package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.DesktopPreviewRenderProfile
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalRoborazziApi::class)
class DesktopPreviewRenderProfileTest {
  @Test
  fun presetsSurviveEncoding() {
    for (profile in listOf(
      DesktopPreviewRenderProfile.Desktop,
      DesktopPreviewRenderProfile.AndroidCompatible,
    )) {
      assertEquals(profile, DesktopPreviewRenderProfile.decode(profile.encode()))
    }
  }

  @Test
  fun deviceSpecWithSeparatorsSurvivesEncoding() {
    // Device specs carry '=' and ',', and a user could write anything here, so round-trip the
    // characters the codec has to escape as well.
    for (device in listOf(
      "id:pixel_4a",
      "spec:width=411dp,height=891dp,dpi=420",
      "spec:width=411dp;height=891dp",
      """spec:weird\;escaped\\""",
    )) {
      val profile = DesktopPreviewRenderProfile.Desktop.copy(defaultDevice = device)
      assertEquals(device, DesktopPreviewRenderProfile.decode(profile.encode()).defaultDevice)
    }
  }

  @Test
  fun encodingIsCommandLineSafe() {
    // The plugin puts the encoding on a forked test JVM's command line, where a control character
    // would be silently truncated away rather than rejected.
    for (profile in listOf(
      DesktopPreviewRenderProfile.Desktop,
      DesktopPreviewRenderProfile.AndroidCompatible,
    )) {
      val encoded = profile.encode()
      assertTrue("'$encoded' must not be empty", encoded.isNotEmpty())
      assertTrue(
        "'$encoded' must not contain control characters",
        encoded.none { it.isISOControl() },
      )
    }
  }

  @Test
  fun nullDeviceIsNotConfusedWithTheStringNull() {
    assertNull(DesktopPreviewRenderProfile.Desktop.defaultDevice)
    val literal = DesktopPreviewRenderProfile.Desktop.copy(defaultDevice = "null")
    assertEquals("null", DesktopPreviewRenderProfile.decode(literal.encode()).defaultDevice)
    assertNotEquals(literal, DesktopPreviewRenderProfile.Desktop)
  }

  @Test
  fun encodingDistinguishesProfilesSoItCanBeATaskInput() {
    assertNotEquals(
      DesktopPreviewRenderProfile.Desktop.encode(),
      DesktopPreviewRenderProfile.AndroidCompatible.encode(),
    )
  }

  @Test
  fun equalProfilesHaveEqualHashCodes() {
    val profile = DesktopPreviewRenderProfile.AndroidCompatible
    val same = DesktopPreviewRenderProfile.Desktop.copy(defaultDevice = profile.defaultDevice)
    assertEquals(profile, same)
    assertEquals(profile.hashCode(), same.hashCode())
  }
}
