package com.github.takahirom.roborazzi.sample

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.RepetitionInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [30],
  qualifiers = RobolectricDeviceQualifiers.NexusOne,
)
@ExtendWith(RobolectricExtension::class)
class JUnit5ManualTest {
  @Test
  @Config(qualifiers = "+land")
  fun captureRoboImageSample() {
    ActivityScenario.launch(MainActivity::class.java)

    onView(isRoot()).captureRoboImage()
    onView(withId(R.id.inputEditText)).perform(typeText("hello"))
    onView(withId(R.id.updateButton)).perform(click())
    onView(isRoot()).captureRoboImage()
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun parameterizedTest(value: Boolean) {
    ActivityScenario.launch(MainActivity::class.java)

    onView(withId(R.id.inputEditText)).perform(typeText("parameter=$value"))
    onView(withId(R.id.updateButton)).perform(click())
    onView(isRoot()).captureRoboImage()
  }

  @RepeatedTest(2)
  fun repeatedTest(repetitionInfo: RepetitionInfo) {
    ActivityScenario.launch(MainActivity::class.java)

    onView(withId(R.id.inputEditText)).perform(typeText("repeated=${repetitionInfo.currentRepetition}"))
    onView(withId(R.id.updateButton)).perform(click())
    onView(isRoot()).captureRoboImage()
  }

  @TestFactory
  fun testFactory() = listOf(
    dynamicTest("First") {
      ActivityScenario.launch(MainActivity::class.java)

      onView(withId(R.id.inputEditText)).perform(typeText("typed but not pressed"))
      onView(isRoot()).captureRoboImage()
    },
    dynamicTest("Second") {
      ActivityScenario.launch(MainActivity::class.java)

      onView(withId(R.id.inputEditText)).perform(typeText("typed and pressed"))
      onView(withId(R.id.updateButton)).perform(click())
      onView(isRoot()).captureRoboImage()
    },
  )
}
