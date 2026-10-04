package com.github.takahirom.preview.crossruntime

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.github.takahirom.roborazzi.annotations.ManualClockOptions
import com.github.takahirom.roborazzi.annotations.RoboComposePreviewOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.awaitCancellation

/**
 * Previews rendered by both runtimes: the Robolectric generator captures them from the Android
 * target, the Compose Desktop generator captures the same source from the `desktop` JVM target.
 * `roborazzi.separateOutputDirs` keeps the two sets apart, so the images of one preview can be
 * compared across runtimes.
 *
 * Three properties shape this set:
 *
 * 1. Several previews share one configuration. Scene reuse groups previews by configuration, so a
 *    set where every preview had its own configuration would make every group a single preview and
 *    hide both the speedup and the state leaking reuse can cause.
 * 2. One device spec uses a density that does not divide the size evenly (411dp at 420dpi is
 *    1078.875px), because a spec whose pixel size is an integer passes any rounding rule.
 * 3. Some previews hold state - a remembered value, a coroutine that never completes, a value
 *    written to a singleton, an endless animation - so that a reused scene leaking into the next
 *    preview shows up as a pixel difference rather than passing unnoticed.
 */

/** 411dp x 891dp at 420dpi: density 2.625, so the surface is 1078.875 x 2338.875 px. */
private const val PHONE_SPEC = "spec:width=411dp,height=891dp,dpi=420"

/**
 * 201dp x 400dp at 440dpi: density 2.75, so 201dp is 552.75px - deliberately fractional.
 *
 * This is the fixture that pins down how the runtimes round. A single truncation of dp * density
 * gives 552; reconstructing dp from already-truncated pixels first (552.75 -> 552 -> 200dp) gives
 * 550. The two device sizes above cannot tell those apart, because one is exact and the other
 * agrees by coincidence.
 */
private const val FRACTIONAL_SPEC = "spec:width=201dp,height=400dp,dpi=440"

/** 800dp x 1280dp at 240dpi: density 1.5, an even pixel size to contrast with [PHONE_SPEC]. */
private const val TABLET_SPEC = "spec:width=800dp,height=1280dp,dpi=240"

/**
 * The same tablet, turned around. `orientation=landscape` contradicts the declared portrait
 * dimensions, which is the one case where a runtime has to choose between trusting the keyword and
 * trusting the order of the dimensions. Pinning it here keeps the two runtimes from choosing
 * differently.
 */
private const val LANDSCAPE_SPEC =
  "spec:width=800dp,height=1280dp,dpi=240,orientation=landscape"

/** `Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL`, spelled out because
 * `android.content.res.Configuration` is not on the desktop classpath. */
private const val NIGHT_MODE = 0x21

@Composable
private fun Labeled(text: String, fontSize: Int = 14) {
  Surface {
    Text(
      text = text,
      fontSize = fontSize.sp,
      textAlign = TextAlign.Center,
      // The UI tree dump pairs nodes across the runtimes by test tag, not by text.
      modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("label"),
    )
  }
}

// --- Group: default configuration ------------------------------------------------------------

@Preview
@Composable
fun DefaultText() {
  Labeled("Default configuration")
}

@Preview
@Composable
fun DefaultButton() {
  Surface {
    Button(onClick = {}, modifier = Modifier.padding(16.dp)) { Text("Press") }
  }
}

@Preview
@Composable
fun DefaultCard() {
  Surface {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
      Text("Card body", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
  }
}

// --- Group: phone spec, fractional pixel size ------------------------------------------------

@Preview(device = PHONE_SPEC)
@Composable
fun PhoneSpecText() {
  Labeled("411dp at 420dpi")
}

@Preview(device = PHONE_SPEC)
@Composable
fun PhoneSpecCard() {
  Surface {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
      Text("Second preview of the same spec", Modifier.padding(16.dp))
    }
  }
}

// --- Group: tablet spec ------------------------------------------------------------------------

@Preview(device = FRACTIONAL_SPEC)
@Composable
fun FractionalSpecText() {
  Labeled("Fractional device width")
}

@Preview(device = TABLET_SPEC)
@Composable
fun TabletSpecText() {
  Labeled("800dp at 240dpi")
}

@Preview(device = TABLET_SPEC)
@Composable
fun TabletSpecButton() {
  Surface {
    Button(onClick = {}, modifier = Modifier.padding(16.dp)) { Text("Tablet") }
  }
}

@Preview(device = LANDSCAPE_SPEC)
@Composable
fun LandscapeSpecText() {
  Labeled("The tablet spec turned landscape")
}

// --- Group: font scale -------------------------------------------------------------------------
// Android bends font scale non-linearly from 1.03 upwards, and the bend differs per sp size, so
// both previews carry several sizes.

@Preview(fontScale = 2f)
@Composable
fun LargeFontSizes() {
  Surface {
    Column(Modifier.padding(16.dp)) {
      Text("14sp", fontSize = 14.sp)
      Text("20sp", fontSize = 20.sp)
      Text("24sp", fontSize = 24.sp)
    }
  }
}

@Preview(fontScale = 2f)
@Composable
fun LargeFontParagraph() {
  Labeled("A longer line of text that wraps at the larger font scale", fontSize = 16)
}

// --- Group: night mode ---------------------------------------------------------------------------

@Preview(uiMode = NIGHT_MODE)
@Composable
fun NightText() {
  Labeled("Night mode")
}

@Preview(uiMode = NIGHT_MODE)
@Composable
fun NightCard() {
  Surface {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
      Text("Night card", Modifier.padding(16.dp))
    }
  }
}

// --- Group: explicit widthDp / heightDp ------------------------------------------------------

@Preview(widthDp = 200, heightDp = 120)
@Composable
fun FixedSizeText() {
  Labeled("200 x 120")
}

/**
 * A `widthDp` whose pixel size is fractional: 201dp at the Medium Phone's 2.625 density is 527.625px.
 *
 * Android's window is an integer number of pixels and `Modifier.size` is coerced into it, while
 * Compose Desktop's `requiredSize` rounds the dp itself. This preview is here to measure which
 * way each runtime goes rather than to assume.
 */
@Preview(widthDp = 201, heightDp = 120)
@Composable
fun OddFixedSizeText() {
  Labeled("201 x 120")
}

@Preview(widthDp = 200, heightDp = 120)
@Composable
fun FixedSizeButton() {
  Surface {
    Button(onClick = {}, modifier = Modifier.padding(8.dp)) { Text("Fixed") }
  }
}

// --- State that a reused scene could carry into the next preview -----------------------------

/**
 * Counts how many times [CompositionCount.next] ran in this JVM. [ScenePosition] reads it, so a
 * scene that is reused without resetting its composition renders a number the fresh scene would
 * not, and the image changes.
 */
object CompositionCount {
  private var value = 0
  fun next(): Int {
    value++
    return value
  }
}

@Preview
@Composable
fun RememberedCounter() {
  val counter = remember { mutableIntStateOf(0) }
  Labeled("Remembered ${counter.intValue}")
}

@Preview
@Composable
fun ScenePosition() {
  val position = remember { CompositionCount.next() }
  Labeled("Composition $position")
}

/**
 * A coroutine that never completes but also never produces work. A loop that kept mutating state
 * would never let the runtime reach idle, and the capture waits for idle.
 */
@Preview
@Composable
fun NeverCompletingEffect() {
  LaunchedEffect(Unit) {
    awaitCancellation()
  }
  Labeled("Effect still running")
}

// The clock is driven by hand so that an endless animation lands on the same frame every run,
// which is what makes an ON/OFF comparison of scene reuse a byte comparison.

@Preview
@RoboComposePreviewOptions(
  manualClockOptions = [ManualClockOptions(advanceTimeMillis = 0L), ManualClockOptions(advanceTimeMillis = 500L)]
)
@Composable
fun EndlessAnimation() {
  val transition = rememberInfiniteTransition(label = "endless")
  val alpha by transition.animateFloat(
    initialValue = 0.2f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
    label = "alpha",
  )
  Surface {
    Box(Modifier.fillMaxWidth().padding(24.dp)) {
      Text("Alpha ${(alpha * 100).toInt()}")
    }
  }
}

@Preview
@RoboComposePreviewOptions(
  manualClockOptions = [ManualClockOptions(advanceTimeMillis = 0L), ManualClockOptions(advanceTimeMillis = 500L)]
)
@Composable
fun EndlessSpinner() {
  Surface {
    Box(Modifier.padding(24.dp)) {
      CircularProgressIndicator(Modifier.size(48.dp))
    }
  }
}
