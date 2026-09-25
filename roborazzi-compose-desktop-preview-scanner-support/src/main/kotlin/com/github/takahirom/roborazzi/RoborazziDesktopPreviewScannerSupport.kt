package com.github.takahirom.roborazzi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.annotations.ManualClockOptions
import com.github.takahirom.roborazzi.annotations.RoboComposePreviewOptions
import io.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.rules.TestRule
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.android.screenshotid.AndroidPreviewScreenshotIdBuilder
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

/**
 * DesktopComposePreviewTester allows you to define how Composable previews are
 * captured on the Compose Desktop (JVM) target, without Robolectric.
 *
 * The class that implements this interface should have a parameterless constructor.
 * You can set the custom tester class name in the
 * roborazzi.generateComposePreviewDesktopTests.testerQualifiedClassName property.
 * A new instance of the tester class is created for each test execution and preview
 * generation.
 *
 * Previews are scanned with ComposablePreviewScanner's `android` artifact, which is a
 * pure-JVM jar: it finds the multiplatform `androidx.compose.ui.tooling.preview.Preview`
 * annotation on the classpath, so previews declared in `commonMain` are captured too.
 */
@ExperimentalRoborazziApi
interface DesktopComposePreviewTester {
  data class Options(
    val testLifecycleOptions: TestLifecycleOptions = JUnit4TestLifecycleOptions(),
    val scanOptions: ScanOptions = ScanOptions(packages = emptyList()),
    /**
     * How previews are sized and scaled.
     *
     * The plugin passes the profile configured for this test run, so one module can capture the
     * same previews under several profiles by giving each its own Kotlin test run.
     *
     * Required, with no default, because it decides the size and density of every golden the
     * module records and no value is right for every project. A custom tester that builds its own
     * options reads the configured one from `roborazziSystemPropertyDesktopDeviceProfile()`.
     */
    val deviceProfile: DesktopPreviewDeviceProfile,
    /**
     * Whether previews that need the same scene are captured in one scene instead of one each.
     *
     * Opening a Compose scene is most of what capturing a small preview costs, so reusing it
     * across a configuration group is the single largest speed-up available on this runtime. Off
     * by default: it changes how previews are handed to the tester, and a project should be able
     * to turn it on, compare the images, and turn it back off.
     *
     * Previews whose clock is driven by hand are never grouped; see [DesktopPreviewSceneKey].
     */
    val sceneReuse: Boolean = false,
  ) {
    interface TestLifecycleOptions

    data class JUnit4TestLifecycleOptions(
      /**
       * Factory for the JUnit [TestRule] wrapped around each generated test
       * (e.g. a TestWatcher, retry rule, or environment setup). Unlike the
       * Robolectric tester there is no compose rule factory: Compose Desktop's
       * test harness is function-scoped (`runDesktopComposeUiTest`), not rule-based.
       */
      val testRuleFactory: () -> TestRule = { TestRule { base, _ -> base } },
    ) : TestLifecycleOptions

    data class ScanOptions(
      /**
       * The packages to scan for composable previews.
       */
      val packages: List<String>,
      /**
       * Whether to include private previews in the scan.
       */
      val includePrivatePreviews: Boolean = false,
      /**
       * Filter for composable previews by annotation.
       */
      val annotationFilter: AnnotationFilter? = null,
    )
  }

  /**
   * Retrieves the options for the DesktopComposePreviewTester.
   */
  fun options(): Options = defaultOptionsFromPlugin

  /**
   * Retrieves the test parameters: one per preview, or one per
   * `@RoboComposePreviewOptions` manual clock variation of a preview.
   */
  fun testParameters(): List<DesktopPreviewTestParameter>

  /**
   * Performs a test for a single test parameter.
   * Note: This method will not be called on the same instance as [testParameters].
   */
  fun test(testParameter: DesktopPreviewTestParameter)

  /**
   * Performs the tests for a whole shard, which is what lets an implementation capture several
   * previews in one Compose scene.
   *
   * Every capture has to be wrapped in [listener] so that the test runner can report it as its own
   * test even when several of them happened inside one scene. The default runs them one at a time,
   * so an existing tester that only overrides [test] keeps working unchanged.
   */
  fun test(
    testParameters: List<DesktopPreviewTestParameter>,
    listener: DesktopPreviewCaptureListener,
  ) {
    testParameters.forEach { testParameter ->
      listener.aroundCapture(testParameter) { test(testParameter) }
    }
  }

  companion object {
    private var pluginOptions: Options? = null

    /**
     * The options the generated test installs, from its `setupDefaultOptions()`, before any test
     * runs.
     *
     * There is no placeholder to fall back on, because [Options.deviceProfile] has no default:
     * reading this before the plugin has set it is a configuration problem, not something to pick
     * a size and a density for.
     */
    @InternalRoborazziApi
    var defaultOptionsFromPlugin: Options
      get() = checkNotNull(pluginOptions) {
        "Roborazzi: the desktop preview tester has no options yet. They are installed by the " +
          "generated test's setupDefaultOptions(), so either the test was not generated by the " +
          "Roborazzi Gradle plugin, or something read the options before that call ran."
      }
      set(value) {
        pluginOptions = value
      }
  }
}

/**
 * A single desktop preview test case.
 *
 * Deliberately not a data class so fields can be added without breaking binary
 * compatibility (no copy/componentN surface).
 */
@ExperimentalRoborazziApi
class DesktopPreviewTestParameter(
  val preview: ComposablePreview<AndroidPreviewInfo>,
  /**
   * The `@RoboComposePreviewOptions` manual clock variation this test captures,
   * or null for a plain single capture.
   */
  val manualClockOptions: ManualClockOptions? = null,
) {
  /**
   * Used as the JUnit Parameterized test name, so each manual clock variation
   * appears as its own test.
   */
  override fun toString(): String {
    return buildString {
      append(preview)
      if (manualClockOptions != null) {
        append("_TIME_${manualClockOptions.advanceTimeMillis}ms")
      }
    }
  }
}

/**
 * Default implementation of [DesktopComposePreviewTester].
 *
 * Customize the capture behavior by passing a [Capturer]: it receives the raw
 * [ComposeUiTest] scope, so anything possible inside `runDesktopComposeUiTest`
 * (mainClock control, interactions, wrapping the content in a theme) stays possible.
 * If you need to change scanning or the file naming as well, implement
 * [DesktopComposePreviewTester] by delegating to this class and override the
 * corresponding method.
 */
@OptIn(InternalRoborazziApi::class)
@ExperimentalRoborazziApi
class DefaultDesktopComposePreviewTester(
  private val options: DesktopComposePreviewTester.Options =
    DesktopComposePreviewTester.defaultOptionsFromPlugin,
  private val capturer: Capturer = DefaultCapturer(),
) : DesktopComposePreviewTester {

  /**
   * Interface for customizing the capture behavior.
   * The receiver is the [ComposeUiTest] scope of `runDesktopComposeUiTest`.
   */
  @OptIn(ExperimentalTestApi::class)
  fun interface Capturer {
    fun ComposeUiTest.capture(parameter: CaptureParameter)
  }

  /**
   * Parameters for capturing a preview screenshot.
   *
   * [content] is the fully decorated composable to render: the raw [preview] wrapped
   * with the `@Preview` annotation options (requiredSize for widthDp/heightDp, a
   * background for showBackground/backgroundColor, [LocalDensity] for fontScale and
   * [LocalSystemTheme] for the dark uiMode bit). Custom [Capturer] implementations
   * should call `setContent(parameter.content)` (not `parameter.preview`) so these
   * options are honored; [preview] is kept only for naming/reference.
   *
   * [manualClockOptions] is set when the preview is annotated with
   * [RoboComposePreviewOptions] and this capture is one of its time-based variations;
   * the tester has already set `mainClock.autoAdvance = false` in that case.
   */
  data class CaptureParameter(
    val preview: ComposablePreview<AndroidPreviewInfo>,
    val filePath: String,
    val roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
    val manualClockOptions: ManualClockOptions? = null,
    val content: @Composable () -> Unit = { preview() },
  )

  /**
   * Default implementation of [Capturer].
   */
  class DefaultCapturer : Capturer {
    @OptIn(ExperimentalTestApi::class)
    override fun ComposeUiTest.capture(parameter: CaptureParameter) {
      setContent(parameter.content)
      advanceMainClockFor(parameter)
      onRoot().captureRoboImage(
        filePath = parameter.filePath,
        roborazziOptions = parameter.roborazziOptions,
      )
    }
  }

  override fun options(): DesktopComposePreviewTester.Options = options

  override fun testParameters(): List<DesktopPreviewTestParameter> {
    val scanOptions = options().scanOptions
    val scanner = AndroidComposablePreviewScanner()
      .scanPackageTrees(*scanOptions.packages.toTypedArray())
      .let {
        if (scanOptions.includePrivatePreviews) {
          it.includePrivatePreviews()
        } else {
          it
        }
      }
    val previews = when (val filter = scanOptions.annotationFilter) {
      is AnnotationFilter.Exclude -> scanner.excludeIfAnnotatedWithAnyOf(
        *filter.annotations.toAnnotationClasses()
      )

      is AnnotationFilter.Include -> scanner.includeIfAnnotatedWithAnyOf(
        *filter.annotations.toAnnotationClasses()
      )

      null -> scanner
    }.getPreviews()

    // One test parameter per @RoboComposePreviewOptions manual clock variation, so
    // each variation runs (and is reported) as its own test, matching the
    // Robolectric preview tests.
    return previews.flatMap { preview ->
      val manualClockVariations: List<ManualClockOptions?> =
        annotationOptionsFor(preview).manualClockOptions.toList().ifEmpty { listOf(null) }
      manualClockVariations.map { manualClockOptions ->
        DesktopPreviewTestParameter(
          preview = preview,
          manualClockOptions = manualClockOptions,
        )
      }
    }
  }

  override fun test(testParameter: DesktopPreviewTestParameter) {
    captureInItsOwnScene(prepare(testParameter))
  }

  override fun test(
    testParameters: List<DesktopPreviewTestParameter>,
    listener: DesktopPreviewCaptureListener,
  ) {
    if (!options().sceneReuse) {
      testParameters.forEach { testParameter ->
        listener.aroundCapture(testParameter) { test(testParameter) }
      }
      return
    }
    groupDesktopPreviewsByScene(testParameters, options().deviceProfile).forEach { group ->
      val prepared = group.map { it to prepare(it) }
      if (prepared.size > 1 && capturerCanShareAScene()) {
        captureInOneScene(prepared, listener)
      } else {
        prepared.forEach { (testParameter, single) ->
          listener.aroundCapture(testParameter) { captureInItsOwnScene(single) }
        }
      }
    }
  }

  /** A preview resolved down to everything the capture needs, so a group resolves before it runs. */
  private class Prepared(
    val renderSpec: DesktopPreviewRenderSpec,
    val locale: String,
    val captureParameter: CaptureParameter,
  )

  private fun prepare(testParameter: DesktopPreviewTestParameter): Prepared {
    val preview = testParameter.preview
    val previewInfo = preview.previewInfo
    // How large the raster surface is and what a dp is worth on it both follow from the render
    // profile, so they are resolved together before the preview is decorated.
    val deviceProfile = options().deviceProfile
    val renderSpec = DesktopPreviewRenderSpec.resolve(previewInfo, deviceProfile)
    return Prepared(
      renderSpec = renderSpec,
      locale = previewInfo.locale,
      captureParameter = CaptureParameter(
        preview = preview,
        filePath = outputFilePathFor(testParameter),
        manualClockOptions = testParameter.manualClockOptions,
        content = decoratedPreviewContent(preview, renderSpec.density),
      ),
    )
  }

  private fun outputFilePathFor(testParameter: DesktopPreviewTestParameter): String {
    val preview = testParameter.preview
    val manualClockOptions = testParameter.manualClockOptions
    val pathPrefix =
      if (roborazziRecordFilePathStrategy() == RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory) {
        roborazziSystemPropertyOutputDirectory() + File.separator
      } else {
        ""
      }
    // Same naming as the Robolectric preview tests (FQCN + screenshot id +
    // _TIME_Xms variation suffix), so the same preview produces the same file
    // name on Android and desktop.
    val name = roborazziDefaultNamingStrategy().generateOutputName(
      preview.declaringClass,
      createScreenshotIdFor(preview)
    )
    val suffix = if (manualClockOptions != null) {
      "_TIME_${manualClockOptions.advanceTimeMillis}ms"
    } else {
      ""
    }
    val filePath = "$pathPrefix$name$suffix.${provideRoborazziContext().imageExtension}"

    roborazziDebugLog {
      "DefaultDesktopComposePreviewTester.test():\n" +
        "  filePathStrategy: ${roborazziRecordFilePathStrategy()}\n" +
        "  outputDirectory: ${roborazziSystemPropertyOutputDirectory()}\n" +
        "  pathPrefix: \"$pathPrefix\"\n" +
        "  name: \"$name\"\n" +
        "  manualClockOptions: $manualClockOptions\n" +
        "  imageExtension: ${provideRoborazziContext().imageExtension}\n" +
        "  filePath: $filePath"
    }
    return filePath
  }

  @OptIn(ExperimentalTestApi::class)
  private fun captureInItsOwnScene(prepared: Prepared) {
    withLocale(prepared.locale) {
      runDesktopComposeUiTest(
        width = prepared.renderSpec.surfaceWidth,
        height = prepared.renderSpec.surfaceHeight,
      ) {
        if (prepared.captureParameter.manualClockOptions != null) {
          mainClock.autoAdvance = false
        }
        with(capturer) { capture(prepared.captureParameter) }
      }
    }
  }

  /**
   * Captures a whole group in one scene, swapping the content instead of reopening the scene.
   *
   * [key] rebuilds the subtree on every swap, so the previous preview's remembered values are
   * discarded and the coroutines its `LaunchedEffect`s started are cancelled rather than handed to
   * the next preview. What it cannot undo is state a preview keeps outside the composition - a
   * top-level `object`, a singleton, a static counter. That state already drifts between two
   * plain runs in the same JVM, on this runtime and under Robolectric alike, so reuse does not
   * make it worse; it is simply not something Roborazzi can reset.
   */
  @OptIn(ExperimentalTestApi::class)
  private fun captureInOneScene(
    group: List<Pair<DesktopPreviewTestParameter, Prepared>>,
    listener: DesktopPreviewCaptureListener,
  ) {
    val first = group.first().second
    withLocale(first.locale) {
      runDesktopComposeUiTest(
        width = first.renderSpec.surfaceWidth,
        height = first.renderSpec.surfaceHeight,
      ) {
        // Nothing is composed until a preview is selected, so that every preview - the first one
        // included - is composed inside its own `aroundCapture`, the way a scene per preview
        // composes it inside the test the listener wraps.
        var index by mutableStateOf(NOTHING_SELECTED)
        setContent {
          key(index) {
            if (index != NOTHING_SELECTED) group[index].second.captureParameter.content()
          }
        }
        group.forEachIndexed { position, (testParameter, prepared) ->
          // Comparison failures are reported per preview and do not end the group: in verify mode
          // `captureRoboImage` throws on a mismatch, and letting the first mismatch out of here
          // would leave every later preview in this scene uncaptured and unreported.
          listener.aroundCapture(testParameter) {
            index = position
            waitForIdle()
            // A no-op here - a preview with manualClockOptions never joins a shared scene - but
            // kept so this path stays a step-for-step match of DefaultCapturer.capture().
            advanceMainClockFor(prepared.captureParameter)
            onRoot().captureRoboImage(
              filePath = prepared.captureParameter.filePath,
              roborazziOptions = prepared.captureParameter.roborazziOptions,
            )
          }
        }
      }
    }
  }

  /**
   * Whether the configured [Capturer] can be driven by the shared-scene path.
   *
   * [Capturer.capture] owns `setContent`, which a shared scene calls once for the whole group, so
   * there is no correct way to route a custom capturer through it. Rather than silently ignore
   * what the project asked for, the group falls back to a scene per preview.
   */
  private fun capturerCanShareAScene(): Boolean {
    if (capturer is DefaultCapturer) return true
    if (warnedAboutCustomCapturer.compareAndSet(false, true)) {
      roborazziErrorLog(
        "sceneReuse is on, but a custom Capturer (${capturer::class.java.name}) owns setContent, " +
          "so previews are captured one scene each as before. Remove the custom Capturer to get " +
          "the speed-up, or leave sceneReuse off to silence this."
      )
    }
    return false
  }

  /**
   * Runs [block] with the JVM default locale the preview asked for.
   *
   * Locale on desktop is read from `java.util.Locale.getDefault()` (there is no LocalLocale), so
   * it has to be set before composing and restored afterwards. The JVM default locale is
   * process-global, so captures are serialized under a lock to stay correct if tests ever run
   * concurrently in one JVM.
   */
  private fun <R> withLocale(locale: String, block: () -> R): R {
    synchronized(localeCaptureLock) {
      val localeToApply = parseAndroidLocale(locale)
      val previousLocale = Locale.getDefault()
      if (localeToApply != null) {
        Locale.setDefault(localeToApply)
      }
      try {
        return block()
      } finally {
        if (localeToApply != null) {
          Locale.setDefault(previousLocale)
        }
      }
    }
  }

  /**
   * Wraps the raw preview with its `@Preview` annotation options.
   *
   * The `device` option is applied by the caller rather than here: it decides the raster surface as
   * well as the density, and [density] is the density it resolved. A preview with no device under the
   * `Desktop` profile gets density 1, which is what it rendered before device profiles existed.
   */
  @OptIn(InternalComposeUiApi::class)
  private fun decoratedPreviewContent(
    preview: ComposablePreview<AndroidPreviewInfo>,
    density: Float,
  ): @Composable () -> Unit {
    val info = preview.previewInfo
    val widthDp = info.widthDp
    val heightDp = info.heightDp
    val fontScale = info.fontScale
    val showBackground = info.showBackground
    val backgroundColor = info.backgroundColor
    // android.content.res.Configuration is Android-only, so its UI_MODE_NIGHT_MASK
    // (0x30) and UI_MODE_NIGHT_YES (0x20) constants are inlined here.
    val nightMode = (info.uiMode and UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES

    // `requiredSize` rounds dp to the nearest pixel, but Android floors: the window is an integer
    // number of pixels and `Modifier.size` is coerced into it. At density 1 the two agree, so this
    // only ever moves a preview whose dp size is fractional in pixels - 201dp at 2.75 is 552.75px,
    // which Android makes 552 and rounding makes 553. Asking for the floored pixel count back in dp
    // makes `requiredSize` land on it exactly.
    val widthAsAndroid = DesktopPreviewRenderSpec.flooredPx(widthDp, density) / density
    val heightAsAndroid = DesktopPreviewRenderSpec.flooredPx(heightDp, density) / density

    return {
      val sizeModifier = when {
        // widthDp/heightDp > 0 means specified; -1/unset keeps wrap-content behavior.
        widthDp > 0 && heightDp > 0 -> Modifier.requiredSize(widthAsAndroid.dp, heightAsAndroid.dp)
        widthDp > 0 -> Modifier.requiredWidth(widthAsAndroid.dp)
        heightDp > 0 -> Modifier.requiredHeight(heightAsAndroid.dp)
        else -> Modifier
      }
      val backgroundModifier = if (showBackground) {
        // Match the Android semantics: default to white when no color is specified.
        val color = if (backgroundColor != 0L) Color(backgroundColor.toInt()) else Color.White
        Modifier.background(color)
      } else {
        Modifier
      }
      val body: @Composable () -> Unit = {
        if (sizeModifier == Modifier && backgroundModifier == Modifier) {
          preview()
        } else {
          Box(modifier = sizeModifier.then(backgroundModifier)) {
            preview()
          }
        }
      }
      val providedValues = buildList {
        // DeviceConfigurationOverride.FontScale throws on desktop, so drive both the device
        // density and fontScale through LocalDensity instead.
        if (density != 1f || fontScale != 1f) {
          add(LocalDensity provides Density(density, fontScale))
        }
        // Provide the dark theme only when the night bit is set; otherwise leave the
        // default so isSystemInDarkTheme() and resource qualifiers behave normally.
        if (nightMode) add(LocalSystemTheme provides SystemTheme.Dark)
      }
      if (providedValues.isEmpty()) {
        body()
      } else {
        CompositionLocalProvider(*providedValues.toTypedArray()) {
          body()
        }
      }
    }
  }

  private fun annotationOptionsFor(preview: ComposablePreview<AndroidPreviewInfo>): RoboComposePreviewOptions {
    // Look up the annotation via reflection instead of preview.getAnnotation()
    // for the same reason as the Robolectric tester:
    // https://github.com/takahirom/roborazzi/pull/633#discussion_r1946472486
    val declaringClass = loadDeclaringClass(preview.declaringClass) ?: return RoboComposePreviewOptions()
    val candidates = declaringClass.declaredMethods.filter { it.name == preview.methodName }
    val method = when {
      candidates.size <= 1 -> candidates.firstOrNull()
      else ->
        // Disambiguate overloaded previews with the scanner's parameter type info.
        candidates.firstOrNull { candidate ->
          preview.methodParametersType.isNotEmpty() &&
            candidate.parameterTypes.any { preview.methodParametersType.contains(it.simpleName) }
        } ?: candidates.first()
    }
    return method?.getAnnotation(RoboComposePreviewOptions::class.java)
      ?: RoboComposePreviewOptions()
  }

  /**
   * Loads the preview's declaring class. The scanner reports a canonical-style name
   * (`com.example.Outer.Inner`), while `Class.forName` needs the binary name
   * (`com.example.Outer${'$'}Inner`), so retry with `$` separators for nested classes.
   */
  private fun loadDeclaringClass(className: String): Class<*>? {
    var candidate = className
    while (true) {
      try {
        return Class.forName(candidate)
      } catch (e: ClassNotFoundException) {
        val lastDot = candidate.lastIndexOf('.')
        if (lastDot < 0) return null
        candidate = candidate.substring(0, lastDot) + '$' + candidate.substring(lastDot + 1)
      }
    }
  }

  private fun createScreenshotIdFor(preview: ComposablePreview<AndroidPreviewInfo>) =
    AndroidPreviewScreenshotIdBuilder(preview).ignoreClassName().build()

  private fun List<String>.toAnnotationClasses(): Array<Class<out Annotation>> {
    return map { annotationClassName ->
      try {
        val clazz = Class.forName(annotationClassName)
        if (!clazz.isAnnotation) {
          throw IllegalArgumentException("The class $annotationClassName is not an annotation")
        }
        @Suppress("UNCHECKED_CAST")
        clazz as Class<out Annotation>
      } catch (e: ClassNotFoundException) {
        throw IllegalArgumentException("The annotation class $annotationClassName not found", e)
      }
    }.toTypedArray()
  }
}

/**
 * Advances the main clock for the current `@RoboComposePreviewOptions` manual clock
 * variation, if any. Custom [DefaultDesktopComposePreviewTester.Capturer]
 * implementations should call this after `setContent` — otherwise time-suffixed
 * captures would all show the initial state, because the tester disables
 * `mainClock.autoAdvance` for manual clock variations.
 */
@ExperimentalRoborazziApi
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.advanceMainClockFor(parameter: DefaultDesktopComposePreviewTester.CaptureParameter) {
  parameter.manualClockOptions?.let { manualClockOptions ->
    mainClock.advanceTimeBy(manualClockOptions.advanceTimeMillis)
  }
}

/**
 * One warning per test JVM is enough for the custom-capturer fallback in the shared-scene path;
 * every group in the module would hit the same case.
 */
private val warnedAboutCustomCapturer = AtomicBoolean(false)

// The JVM default locale is process-global; see withLocale().
private val localeCaptureLock = Any()

/** The index a shared scene holds before any preview of the group has been selected. */
private const val NOTHING_SELECTED = -1

// Default raster surface size of runDesktopComposeUiTest(width = 1024, height = 768).

// android.content.res.Configuration is Android-only. These mirror its
// UI_MODE_NIGHT_MASK / UI_MODE_NIGHT_YES constant values for use on desktop.
private const val UI_MODE_NIGHT_MASK = 0x30
private const val UI_MODE_NIGHT_YES = 0x20

/**
 * Parses an Android-style `@Preview` locale string into a [Locale], or null when blank.
 * Accepts language only ("ja"), the Android resource region form ("ja-rJP") and the
 * BCP47-ish form ("ja-JP").
 */
internal fun parseAndroidLocale(locale: String): Locale? {
  if (locale.isBlank()) return null
  val parts = locale.split("-", "_")
  val language = parts[0]
  val region = parts.getOrNull(1)?.removePrefix("r")
  return if (region.isNullOrBlank()) {
    Locale(language)
  } else {
    Locale(language, region)
  }
}

@InternalRoborazziApi
fun getDesktopComposePreviewTester(testerQualifiedClassName: String): DesktopComposePreviewTester {
  val customTesterClass = try {
    Class.forName(testerQualifiedClassName)
  } catch (e: ClassNotFoundException) {
    throw IllegalArgumentException("The class $testerQualifiedClassName not found", e)
  }
  if (!DesktopComposePreviewTester::class.java.isAssignableFrom(customTesterClass)) {
    throw IllegalArgumentException("The class $testerQualifiedClassName must implement DesktopComposePreviewTester")
  }
  return customTesterClass.getDeclaredConstructor().newInstance() as DesktopComposePreviewTester
}
