package com.github.takahirom.roborazzi.sample

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeTest {

  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  val roborazziRule = RoborazziRule(composeTestRule, composeTestRule.onRoot())

  @get:Rule
  var ruleChain: RuleChain = RuleChain.outerRule(this.composeTestRule).around(roborazziRule)

  init {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @Test
  fun composable() {
    println("start")
    composeTestRule.setContent {
      SampleComposableFunction()
    }
    println("start2")
    (0..50).forEach {
      println("click$it")
      composeTestRule
        .onNodeWithTag("MyComposeRoot")
        .performClick()
      composeTestRule
        .onNodeWithTag("MyComposeRoot")
        .assertExists("not ")
    }
    println("ok?")
  }
}
