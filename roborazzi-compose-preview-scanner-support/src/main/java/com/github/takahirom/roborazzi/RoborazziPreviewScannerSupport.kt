package com.github.takahirom.roborazzi

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.github.takahirom.roborazzi.annotations.ManualClockOptions
import com.github.takahirom.roborazzi.annotations.RoboComposePreviewOptions
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.robolectric.RuntimeEnvironment.setQualifiers
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.android.device.domain.RobolectricDeviceQualifierBuilder
import sergio.sastre.composable.preview.scanner.android.screenshotid.AndroidPreviewScreenshotIdBuilder
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

@ExperimentalRoborazziApi
fun ComposablePreview<AndroidPreviewInfo>.captureRoboImage(
  filePath: String,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  roborazziComposeOptions: RoborazziComposeOptions = this.toRoborazziComposeOptions(),
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  val composablePreview = this
  captureRoboImage(filePath, roborazziOptions, roborazziComposeOptions) {
    composablePreview()
  }
}

@ExperimentalRoborazziApi
fun ComposablePreview<AndroidPreviewInfo>.toRoborazziComposeOptions(): RoborazziComposeOptions {
  return RoborazziComposeOptions {
    size(
      widthDp = previewInfo.widthDp, heightDp = previewInfo.heightDp
    )
    background(
      showBackground = previewInfo.showBackground, backgroundColor = previewInfo.backgroundColor
    )
    locale(previewInfo.locale)
    uiMode(previewInfo.uiMode)
    previewDevice(previewInfo.device)
    fontScale(previewInfo.fontScale)

    /*
    We don't specify `inspectionMode` by default.
    The default value for `inspectionMode` in Compose is `false`.
    This is to maintain higher fidelity in tests.
    If you encounter issues integrating the library, you can set `inspectionMode` to `true`.

    inspectionMode(true)
     */
  }
}


@Suppress("UnusedReceiverParameter")
@Deprecated(
  message = "Use previewInfo.toRoborazziComposeOptions().configured(scenario, composeContent) or ComposablePreview<AndroidPreviewInfo>.captureRoboImage() instead",
  replaceWith = ReplaceWith("previewInfo.toRoborazziComposeOptions().configured(scenario, composeContent)"),
  level = DeprecationLevel.ERROR
)
fun ComposablePreview<AndroidPreviewInfo>.applyToRobolectricConfiguration() {
  throw UnsupportedOperationException("Use previewInfo.toRoborazziComposeOptions().configured(scenario, composeContent) or ComposablePreview<AndroidPreviewInfo>.captureRoboImage() instead")
}

@ExperimentalRoborazziApi
fun RoborazziComposeOptions.Builder.previewDevice(previewDevice: String): RoborazziComposeOptions.Builder {
  return addOption(RoborazziComposePreviewDeviceOption(previewDevice))
}

@ExperimentalRoborazziApi
data class RoborazziComposePreviewDeviceOption(private val previewDevice: String) :
  RoborazziComposeSetupOption {
  override fun configure() {
    if (previewDevice.isNotBlank()) {
      // Requires `io.github.sergio-sastre.ComposablePreviewScanner:android:0.4.0` or later
      RobolectricDeviceQualifierBuilder.build(previewDevice)?.run {
        setQualifiers(this)
      }
    }
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeOptions.Builder.composeTestRule(composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<out androidx.activity.ComponentActivity>, *>): RoborazziComposeOptions.Builder {
  return addOption(RoborazziComposeTestRuleOption(composeTestRule))
}

@ExperimentalRoborazziApi
data class RoborazziComposeTestRuleOption(private val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<out ComponentActivity>, *>) :
  RoborazziComposeActivityScenarioCreatorOption {
  override fun createScenario(chain: () -> ActivityScenario<out ComponentActivity>): ActivityScenario<out ComponentActivity> {
    return composeTestRule.activityRule.scenario
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeOptions.Builder.manualAdvance(
  composeTestRule: ComposeTestRule,
  advanceTimeMillis: Long
): RoborazziComposeOptions.Builder {
  return addOption(RoborazziComposeManualAdvancePreviewOption(composeTestRule, advanceTimeMillis))
}

@ExperimentalRoborazziApi
data class RoborazziComposeManualAdvancePreviewOption(
  private val composeTestRule: ComposeTestRule,
  private val advanceTimeMillis: Long
) :
  RoborazziComposeSetupOption, RoborazziComposeCaptureOption {
  override fun configure() {
    composeTestRule.mainClock.autoAdvance = false
  }

  override fun beforeCapture() {
    composeTestRule.mainClock.advanceTimeBy(advanceTimeMillis)
  }

  override fun afterCapture() {
    composeTestRule.mainClock.autoAdvance = true
  }
}


/**
 * ComposePreviewTester is an interface that allows you to define a custom test for a Composable preview.
 * The class that implements this interface should have a parameterless constructor.
 * You can set the custom tester class name in the roborazzi.generateComposePreviewRobolectricTests.testerQualifiedClassName property.
 * A new instance of the tester class is created for each test execution and preview generation.
 */
@ExperimentalRoborazziApi
interface ComposePreviewTester<T : Any> {
  data class Options(
    val testLifecycleOptions: TestLifecycleOptions = JUnit4TestLifecycleOptions(),
    val scanOptions: ScanOptions = ScanOptions(emptyList()),
  ) {
    interface TestLifecycleOptions

    @Suppress("UNCHECKED_CAST")
    data class JUnit4TestLifecycleOptions(
      val composeRuleFactory: () -> AndroidComposeTestRule<ActivityScenarioRule<out ComponentActivity>, *> = { createAndroidComposeRule<RoborazziActivity>() as AndroidComposeTestRule<ActivityScenarioRule<out ComponentActivity>, *> },
      /**
       * The TestRule factory to be used for the generated tests.
       * You can use this to add custom behavior to the generated tests.
       */
      // Used from generated tests
      @Suppress("unused") val testRuleFactory: (AndroidComposeTestRule<ActivityScenarioRule<out ComponentActivity>, *>) -> TestRule = object :
          (AndroidComposeTestRule<ActivityScenarioRule<out ComponentActivity>, *>) -> TestRule {
        override fun invoke(composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<out ComponentActivity>, *>): TestRule {
          return RuleChain.outerRule(
            RuleChain
              .outerRule(object : TestWatcher() {
                override fun starting(description: org.junit.runner.Description?) {
                  super.starting(description)
                  registerRoborazziActivityToRobolectricIfNeeded()
                }
              })
              .around(composeTestRule)
          )
        }
      }
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
    )
  }

  /**
   * Retrieves the options for the ComposePreviewTester.
   *
   * @return The Options object.
   */
  fun options(): Options = defaultOptionsFromPlugin

  /**
   * Retrieves a list of composable previews from the specified packages.
   *
   * @return A list of ComposablePreview objects of type T.
   */
  fun testParameters(): List<TestParameter<T>>

  sealed class TestParameter<T> {
    open class JUnit4TestParameter<T>(
      open val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<out ComponentActivity>, *>,
      open val preview: ComposablePreview<T>
    ) : TestParameter<T>() {
      data class AndroidPreviewJUnit4TestParameter(
        override val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<out ComponentActivity>, *>,
        override val preview: ComposablePreview<AndroidPreviewInfo>,
        val composeRoboComposePreviewOptionVariation: RoboComposePreviewOptionVariation = RoboComposePreviewOptionVariation()
      ) : JUnit4TestParameter<AndroidPreviewInfo>(composeTestRule, preview) {
        override fun toString(): String {
          return "JUnit4TestParameter(preview=$preview)"
        }
      }
    }
  }

  /**
   * Performs a test on a single composable preview.
   * Note: This method will not be called as the same instance of previews() method.
   *
   * @param preview The ComposablePreview object to be tested.
   */
  fun test(testParameter: TestParameter<T>)

  companion object {
    // Should be replaced with the actual default options from the plugin.
    @InternalRoborazziApi
    var defaultOptionsFromPlugin = Options()
  }
}

@ExperimentalRoborazziApi
class AndroidComposePreviewTester : ComposePreviewTester<AndroidPreviewInfo> {
  override fun testParameters(): List<ComposePreviewTester.TestParameter<AndroidPreviewInfo>> {
    val options = options()
    val junit4TestLifecycleOptions =
      options.testLifecycleOptions as ComposePreviewTester.Options.JUnit4TestLifecycleOptions
    return AndroidComposablePreviewScanner().scanPackageTrees(*options.scanOptions.packages.toTypedArray())
      .includeAnnotationInfoForAllOf(RoboComposePreviewOptions::class.java).let {
        if (options.scanOptions.includePrivatePreviews) {
          it.includePrivatePreviews()
        } else {
          it
        }
      }.getPreviews()
      .flatMap { preview ->
        // FIXME
        // This cause IllegalArgumentException
        // Please refer to https://github.com/takahirom/roborazzi/pull/633#discussion_r1946472486
//    val options: RoboComposePreviewOptions =
//      (preview.getAnnotation<RoboComposePreviewOptions>() ?: RoboComposePreviewOptions())
        val annotationOptions = Class.forName(preview.declaringClass).declaredMethods
          .firstOrNull { it.name == preview.methodName }
          ?.getAnnotation<RoboComposePreviewOptions>(RoboComposePreviewOptions::class.java)
          ?: RoboComposePreviewOptions()
        annotationOptions.variations()
          .map { optionVariation ->
            ComposePreviewTester.TestParameter.JUnit4TestParameter.AndroidPreviewJUnit4TestParameter(
              composeTestRule = junit4TestLifecycleOptions.composeRuleFactory(),
              preview = preview,
              composeRoboComposePreviewOptionVariation = optionVariation
            )
          }
      }
  }

  override fun test(testParameter: ComposePreviewTester.TestParameter<AndroidPreviewInfo>) {
    if (testParameter !is ComposePreviewTester.TestParameter.JUnit4TestParameter.AndroidPreviewJUnit4TestParameter) {
      throw IllegalArgumentException("Currently only JUnit4TestParameter is supported")
    }
    val junit4TestParameter: ComposePreviewTester.TestParameter.JUnit4TestParameter<*> =
      testParameter
    val preview = junit4TestParameter.preview
    val pathPrefix =
      if (roborazziRecordFilePathStrategy() == RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory) {
        roborazziSystemPropertyOutputDirectory() + java.io.File.separator
      } else {
        ""
      }
    // In AndroidComposePreviewTester, the preview is ComposablePreview<AndroidPreviewInfo>
    @Suppress("UNCHECKED_CAST") val name = roborazziDefaultNamingStrategy().generateOutputName(
      preview.declaringClass,
      createScreenshotIdFor(preview as ComposablePreview<AndroidPreviewInfo>)
    )

    val optionVariation: RoboComposePreviewOptionVariation =
      testParameter.composeRoboComposePreviewOptionVariation
    val filePath =
      "$pathPrefix$name${optionVariation.nameWithPrefix()}.${provideRoborazziContext().imageExtension}"
    preview.captureRoboImage(
      filePath = filePath,
      roborazziComposeOptions = preview.toRoborazziComposeOptions().builder()
        .apply {
          @Suppress("UNCHECKED_CAST")
          composeTestRule(junit4TestParameter.composeTestRule)
          optionVariation.manualClockOptions?.let {
            manualAdvance(
              junit4TestParameter.composeTestRule,
              optionVariation.manualClockOptions.advanceTimeMillis
            )
          }
          optionVariation.localInspectionMode?.let { mode ->
            inspectionMode(mode)
          }
        }
        .build()
    )
  }

  private fun createScreenshotIdFor(preview: ComposablePreview<AndroidPreviewInfo>) =
    AndroidPreviewScreenshotIdBuilder(preview).ignoreClassName().build()
}

@ExperimentalRoborazziApi
class RoboComposePreviewOptionVariation(
  val manualClockOptions: ManualClockOptions? = null,
  val localInspectionMode: Boolean? = null
) {
  fun nameWithPrefix(): String {
    return buildString {
      if (manualClockOptions != null) {
        append("_TIME_${manualClockOptions.advanceTimeMillis}ms")
      }
      if (localInspectionMode != null) {
        append("_INSPECTION_MODE")
      }
    }
  }
}


internal fun RoboComposePreviewOptions.variations(): List<RoboComposePreviewOptionVariation> {
  val clockVariations = if (manualClockOptions.isEmpty()) {
    listOf(null)
  } else {
    manualClockOptions.toList()
  }

  val inspectionModes = if (localInspectionModes.isEmpty()) {
    listOf(null)
  } else {
    localInspectionModes.toList()
  }

  return clockVariations.flatMap { clockOption ->
    inspectionModes.map { inspectionMode ->
      RoboComposePreviewOptionVariation(
        manualClockOptions = clockOption,
        localInspectionMode = inspectionMode
      )
    }
  }
}

@InternalRoborazziApi
fun getComposePreviewTester(testerQualifiedClassName: String): ComposePreviewTester<Any> {
  val customTesterClass = try {
    Class.forName(testerQualifiedClassName)
  } catch (e: ClassNotFoundException) {
    throw IllegalArgumentException("The class $testerQualifiedClassName not found")
  }
  if (!ComposePreviewTester::class.java.isAssignableFrom(customTesterClass)) {
    throw IllegalArgumentException("The class $testerQualifiedClassName must implement RobolectricPreviewCapturer")
  }
  @Suppress("UNCHECKED_CAST") val composePreviewTester =
    customTesterClass.getDeclaredConstructor().newInstance() as ComposePreviewTester<Any>
  return composePreviewTester
}
