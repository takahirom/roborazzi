package com.github.takahirom.roborazzi

import android.app.Activity
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDisplay.getDefaultDisplay
import kotlin.math.roundToInt

@ExperimentalRoborazziApi
data class RoborazziComposeDecorationScope(
  val scenario: ActivityScenario<out Activity>,
  val content: @Composable () -> Unit
)

@ExperimentalRoborazziApi
data class RoborazziComposeDecorationResult(val content: @Composable () -> Unit)

@ExperimentalRoborazziApi
interface RoborazziComposeDecorator {
  fun decorate(scope: RoborazziComposeDecorationScope): RoborazziComposeDecorationResult
}

@ExperimentalRoborazziApi
class RoborazziComposeDecorationBuilder {
  private val decorators = mutableListOf<RoborazziComposeDecorator>()

  fun with(decorator: RoborazziComposeDecorator): RoborazziComposeDecorationBuilder {
    decorators.add(decorator)
    return this
  }

  fun sized(widthDp: Int = 0, heightDp: Int = 0): RoborazziComposeDecorationBuilder {
    return with(RoborazziComposeSizeDecorator(widthDp, heightDp))
  }

  fun colored(
    showBackground: Boolean,
    backgroundColor: Long = 0L
  ): RoborazziComposeDecorationBuilder {
    return with(RoborazziComposeBackgroundDecorator(showBackground, backgroundColor))
  }

  @InternalRoborazziApi
  fun apply(
    scenario: ActivityScenario<out Activity>,
    content: @Composable () -> Unit
  ): @Composable () -> Unit {
    var currentResult = RoborazziComposeDecorationResult(content)
    val scope = RoborazziComposeDecorationScope(scenario, content)
    for (decorator in decorators) {
      currentResult = decorator.decorate(scope.copy(content = currentResult.content))
    }
    return currentResult.content
  }
}

@ExperimentalRoborazziApi
data class RoborazziComposeSizeDecorator(val widthDp: Int, val heightDp: Int) :
  RoborazziComposeDecorator {
  override fun decorate(scope: RoborazziComposeDecorationScope): RoborazziComposeDecorationResult {
    var result: (@Composable () -> Unit)? = null
    scope.scenario.onActivity { activity ->
      activity.setDisplaySize(widthDp = widthDp, heightDp = heightDp)
      result = scope.content.size(widthDp = widthDp, heightDp = heightDp)
    }
    return RoborazziComposeDecorationResult(
      result
        ?: throw IllegalStateException("The preview could not be successfully sized to widthDp = $widthDp and heightDp = $heightDp")
    )
  }

  private fun Activity.setDisplaySize(
    widthDp: Int,
    heightDp: Int
  ) {
    if (widthDp <= 0 && heightDp <= 0) return

    val display = shadowOf(getDefaultDisplay())
    val density = resources.displayMetrics.density
    if (widthDp > 0) {
      val widthPx = (widthDp * density).roundToInt()
      display.setWidth(widthPx)
    }
    if (heightDp > 0) {
      val heightPx = (heightDp * density).roundToInt()
      display.setHeight(heightPx)
    }
    recreate()
  }

  /**
   * WARNING:
   * For this to work, it requires that the Display is within the widthDp and heightDp dimensions
   * You can ensure that by calling [Activity.setDisplaySize] before
   */
  private fun (@Composable () -> Unit).size(
    widthDp: Int,
    heightDp: Int,
  ): @Composable () -> Unit {
    val resizedPreview = @Composable {
      val modifier = when {
        widthDp > 0 && heightDp > 0 -> Modifier.size(widthDp.dp, heightDp.dp)
        widthDp > 0 -> Modifier.width(widthDp.dp)
        heightDp > 0 -> Modifier.height(heightDp.dp)
        else -> Modifier
      }
      Box(modifier = modifier) {
        this@size()
      }
    }
    return resizedPreview
  }
}

@ExperimentalRoborazziApi
data class RoborazziComposeBackgroundDecorator(
  val showBackground: Boolean,
  val backgroundColor: Long
) : RoborazziComposeDecorator {
  override fun decorate(scope: RoborazziComposeDecorationScope): RoborazziComposeDecorationResult {
    when (showBackground) {
      false -> {
        scope.scenario.onActivity { activity ->
          activity.window.decorView.setBackgroundColor(Color.TRANSPARENT)
        }
      }

      true -> {
        val color = when (backgroundColor != 0L) {
          true -> backgroundColor.toInt()
          false -> Color.WHITE
        }
        scope.scenario.onActivity { activity ->
          activity.window.decorView.setBackgroundColor(color)
        }
      }
    }
    return RoborazziComposeDecorationResult(scope.content)
  }
}
