package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.DesktopPreviewDeviceProfile
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalRoborazziApi::class)
class DesktopPreviewDeviceProfileTest {
  @Test
  fun presetsSurviveEncoding() {
    for (profile in listOf(
      DesktopPreviewDeviceProfile.Desktop,
      DesktopPreviewDeviceProfile.AndroidCompatible,
    )) {
      assertEquals(profile, DesktopPreviewDeviceProfile.decode(profile.encode()))
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
      val profile = DesktopPreviewDeviceProfile.Desktop.copy(defaultDevice = device)
      assertEquals(device, DesktopPreviewDeviceProfile.decode(profile.encode()).defaultDevice)
    }
  }

  @Test
  fun encodingIsCommandLineSafe() {
    // The plugin puts the encoding on a forked test JVM's command line, where a control character
    // would be silently truncated away rather than rejected.
    for (profile in listOf(
      DesktopPreviewDeviceProfile.Desktop,
      DesktopPreviewDeviceProfile.AndroidCompatible,
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
    assertNull(DesktopPreviewDeviceProfile.Desktop.defaultDevice)
    val literal = DesktopPreviewDeviceProfile.Desktop.copy(defaultDevice = "null")
    assertEquals("null", DesktopPreviewDeviceProfile.decode(literal.encode()).defaultDevice)
    assertNotEquals(literal, DesktopPreviewDeviceProfile.Desktop)
  }

  @Test
  fun encodingDistinguishesProfilesSoItCanBeATaskInput() {
    assertNotEquals(
      DesktopPreviewDeviceProfile.Desktop.encode(),
      DesktopPreviewDeviceProfile.AndroidCompatible.encode(),
    )
  }

  @Test
  fun equalProfilesHaveEqualHashCodes() {
    val profile = DesktopPreviewDeviceProfile.AndroidCompatible
    val same = DesktopPreviewDeviceProfile.Desktop.copy(defaultDevice = profile.defaultDevice)
    assertEquals(profile, same)
    assertEquals(profile.hashCode(), same.hashCode())
  }
}
