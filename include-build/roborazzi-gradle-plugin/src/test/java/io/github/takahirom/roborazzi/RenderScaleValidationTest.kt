package io.github.takahirom.roborazzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RenderScaleValidationTest {
  @Test
  fun validScaleIsAccepted() {
    assertEquals(0.5, validateRenderScale(0.5), 0.0)
  }

  @Test
  fun upscaleIsAccepted() {
    assertEquals(2.0, validateRenderScale(2.0), 0.0)
  }

  @Test
  fun zeroIsRejected() {
    assertThrows(IllegalArgumentException::class.java) { validateRenderScale(0.0) }
  }

  @Test
  fun negativeIsRejected() {
    assertThrows(IllegalArgumentException::class.java) { validateRenderScale(-0.5) }
  }

  @Test
  fun nanIsRejected() {
    assertThrows(IllegalArgumentException::class.java) { validateRenderScale(Double.NaN) }
  }

  @Test
  fun infinityIsRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      validateRenderScale(Double.POSITIVE_INFINITY)
    }
  }
}
