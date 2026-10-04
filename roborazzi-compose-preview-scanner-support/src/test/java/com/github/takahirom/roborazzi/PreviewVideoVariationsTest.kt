package com.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.annotations.ManualClockOptions
import com.github.takahirom.roborazzi.annotations.PreviewVideoFormat
import com.github.takahirom.roborazzi.annotations.PreviewVideoOptions
import com.github.takahirom.roborazzi.annotations.RoboComposePreviewOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalRoborazziApi::class)
class PreviewVideoVariationsTest {
  private object Annotated {
    @RoboComposePreviewOptions
    fun none() {}

    @RoboComposePreviewOptions(videoOptions = [PreviewVideoOptions(durationMillis = 500)])
    fun oneVideo() {}

    @RoboComposePreviewOptions(
      videoOptions = [
        PreviewVideoOptions(),
        PreviewVideoOptions(format = PreviewVideoFormat.APNG),
      ]
    )
    fun twoVideos() {}

    @RoboComposePreviewOptions(
      manualClockOptions = [ManualClockOptions(advanceTimeMillis = 100)],
      videoOptions = [PreviewVideoOptions()]
    )
    fun videoWithManualClock() {}

    @RoboComposePreviewOptions(videoOptions = [PreviewVideoOptions(durationMillis = 0)])
    fun zeroDuration() {}
  }

  private fun options(name: String) =
    Annotated::class.java.getDeclaredMethod(name).getAnnotation(RoboComposePreviewOptions::class.java)!!

  @Test
  fun withoutVideoThereIsOnlyTheStill() {
    val variations = options("none").variations()
    assertEquals(1, variations.size)
    assertNull(variations.single().videoOptions)
    assertEquals("", variations.single().nameWithPrefix())
  }

  @Test
  fun aVideoIsAddedNextToTheStill() {
    val variations = options("oneVideo").variations()
    assertEquals(2, variations.size)
    assertNull(variations[0].videoOptions)
    assertEquals(500L, variations[1].videoOptions!!.durationMillis)
    assertEquals("_VIDEO", variations[1].nameWithPrefix())
  }

  @Test
  fun severalVideosGetDistinctNames() {
    val names = options("twoVideos").variations().map { it.nameWithPrefix() }
    assertEquals(listOf("", "_VIDEO_1", "_VIDEO_2"), names)
  }

  @Test
  fun videoWithManualClockIsRejected() {
    val e = assertThrows(IllegalArgumentException::class.java) {
      options("videoWithManualClock").variations("Foo.bar")
    }
    assertTrue(e.message!!.contains("Foo.bar"))
    assertTrue(e.message!!.contains("manualClockOptions"))
  }

  @Test
  fun nonPositiveDurationIsRejected() {
    assertThrows(IllegalArgumentException::class.java) { options("zeroDuration").variations() }
  }
}
