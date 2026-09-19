package io.github.takahirom.roborazzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RenderScaleValidationTest {
  @Test
  fun validScaleIsAccepted() {
    assertEquals(0.5f, validateRenderScale(0.5f))
  }

  @Test
  fun upscaleIsAccepted() {
    assertEquals(2f, validateRenderScale(2f))
  }

  @Test
  fun zeroIsRejected() {
    assertThrows(IllegalArgumentException::class.java) { validateRenderScale(0f) }
  }

  @Test
  fun negativeIsRejected() {
    assertThrows(IllegalArgumentException::class.java) { validateRenderScale(-0.5f) }
  }

  @Test
  fun nanIsRejected() {
    assertThrows(IllegalArgumentException::class.java) { validateRenderScale(Float.NaN) }
  }

  @Test
  fun infinityIsRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      validateRenderScale(Float.POSITIVE_INFINITY)
    }
  }
}
