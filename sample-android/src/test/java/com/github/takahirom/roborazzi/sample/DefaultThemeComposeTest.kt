package com.github.takahirom.roborazzi.sample

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.RoborazziTransparentActivity
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.GraphicsMode

class DefaultThemeActivity : ComponentActivity() {
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
      Shadows.shadowOf(appContext.packageManager).addActivityIfNotPresent(
        ComponentName(
          appContext.packageName,
          DefaultThemeActivity::class.java.name,
        )
      )
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
      MaterialTheme {
        Surface {
          Box(modifier = Modifier.padding(8.dp)) {
            ElevatedCard(
              elevation = CardDefaults.cardElevation(
                defaultElevation = 6.dp
              ),
            ) {
              Text("a\nb\nc\nd\ne\nf\ng\nh\ni\nj\nk\nl\nm\nn\no\np\nq\nr\ns\nt\nu\nv\nw\nx\ny\nz")
            }
          }
        }
      }
    }
    composeTestRule.onRoot().captureRoboImage()
  }
}
