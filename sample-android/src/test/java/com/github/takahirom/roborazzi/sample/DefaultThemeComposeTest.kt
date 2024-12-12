package com.github.takahirom.roborazzi.sample

import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.GraphicsMode

class DefaultThemeActivity: ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(android.R.style.Theme_Material_Light)
    super.onCreate(savedInstanceState)
  }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DefaultThemeComposeTest {
  @get:Rule(order = 1)
  val addActivityToRobolectricRule = object : TestWatcher() {
    override fun starting(description: Description?) {
      super.starting(description)
      val appContext: Application = ApplicationProvider.getApplicationContext()
      val activityInfo = ActivityInfo().apply {
        name = DefaultThemeActivity::class.java.name
        packageName = appContext.packageName
      }
      Shadows.shadowOf(appContext.packageManager).addOrUpdateActivity(activityInfo)
    }
  }

  @get:Rule(order = 2)
  val composeTestRule = createAndroidComposeRule<DefaultThemeActivity>()

  @get:Rule
  val roborazziRule = RoborazziRule(
    composeRule = composeTestRule,
    captureRoot = composeTestRule.onRoot(),
  )

  @Test
  fun composable() {
    composeTestRule.setContent {
      Text("a\nb\nc\nd\ne\nf\ng\nh\ni\nj\nk\nl\nm\nn\no\np\nq\nr\ns\nt\nu\nv\nw\nx\ny\nz")
    }
    composeTestRule.onRoot().captureRoboImage()
  }
}
