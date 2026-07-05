package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalRoborazziApi::class)
class AddedAiAssertionsTest {

  @Test
  fun addedAiAssertionWithoutAiAssertionOptionsThrowsDescriptiveError() {
    // Default CompareOptions has aiAssertionOptions = null.
    val options = RoborazziOptions()

    val exception = runCatching {
      options.addedAiAssertion(
        assertionPrompt = "The screen shows a login form",
        requiredFulfillmentPercent = 80
      )
    }.exceptionOrNull()

    assertTrue(
      "Expected IllegalStateException but was ${exception?.let { it::class.simpleName }}",
      exception is IllegalStateException
    )
    assertTrue(
      "Expected the message to mention aiAssertionOptions but was: ${exception?.message}",
      exception?.message?.contains("aiAssertionOptions") == true
    )
  }
}
