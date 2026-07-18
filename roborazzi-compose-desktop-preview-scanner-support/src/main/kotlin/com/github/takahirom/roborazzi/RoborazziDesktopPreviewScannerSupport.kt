package com.github.takahirom.roborazzi

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.github.takahirom.roborazzi.annotations.ManualClockOptions
import com.github.takahirom.roborazzi.annotations.RoboComposePreviewOptions
import io.github.takahirom.roborazzi.captureRoboImage
import java.io.File
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
    val scanOptions: ScanOptions = ScanOptions(packages = emptyList()),
  ) {
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
   * Retrieves a list of composable previews from the packages in [Options.ScanOptions].
   */
  fun previews(): List<ComposablePreview<AndroidPreviewInfo>>

  /**
   * Performs a test on a single composable preview.
   * Note: This method will not be called on the same instance as [previews].
   */
  fun test(preview: ComposablePreview<AndroidPreviewInfo>)

  companion object {
    // Should be replaced with the actual default options from the plugin.
    @InternalRoborazziApi
    var defaultOptionsFromPlugin = Options()
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
   * [manualClockOptions] is set when the preview is annotated with
   * [RoboComposePreviewOptions] and this capture is one of its time-based variations;
   * the tester has already set `mainClock.autoAdvance = false` in that case.
   */
  data class CaptureParameter(
    val preview: ComposablePreview<AndroidPreviewInfo>,
    val filePath: String,
    val roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
    val manualClockOptions: ManualClockOptions? = null,
  )

  /**
   * Default implementation of [Capturer].
   */
  class DefaultCapturer : Capturer {
    @OptIn(ExperimentalTestApi::class)
    override fun ComposeUiTest.capture(parameter: CaptureParameter) {
      setContent { parameter.preview() }
      parameter.manualClockOptions?.let { manualClockOptions ->
        mainClock.advanceTimeBy(manualClockOptions.advanceTimeMillis)
      }
      onRoot().captureRoboImage(
        filePath = parameter.filePath,
        roborazziOptions = parameter.roborazziOptions,
      )
    }
  }

  override fun options(): DesktopComposePreviewTester.Options = options

  override fun previews(): List<ComposablePreview<AndroidPreviewInfo>> {
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
    return when (val filter = scanOptions.annotationFilter) {
      is AnnotationFilter.Exclude -> scanner.excludeIfAnnotatedWithAnyOf(
        *filter.annotations.toAnnotationClasses()
      )

      is AnnotationFilter.Include -> scanner.includeIfAnnotatedWithAnyOf(
        *filter.annotations.toAnnotationClasses()
      )

      null -> scanner
    }.getPreviews()
  }

  @OptIn(ExperimentalTestApi::class)
  override fun test(preview: ComposablePreview<AndroidPreviewInfo>) {
    val pathPrefix =
      if (roborazziRecordFilePathStrategy() == RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory) {
        roborazziSystemPropertyOutputDirectory() + File.separator
      } else {
        ""
      }
    // Same naming as the Robolectric preview tests (FQCN + screenshot id), so the
    // same preview produces the same file name on Android and desktop.
    val name = roborazziDefaultNamingStrategy().generateOutputName(
      preview.declaringClass,
      createScreenshotIdFor(preview)
    )

    // One capture per @RoboComposePreviewOptions manual clock variation, with the
    // same _TIME_Xms file name suffix as the Robolectric preview tests. Previews
    // without the annotation produce a single capture without a suffix.
    val manualClockVariations: List<ManualClockOptions?> =
      annotationOptionsFor(preview).manualClockOptions.toList().ifEmpty { listOf(null) }

    manualClockVariations.forEach { manualClockOptions ->
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

      val parameter = CaptureParameter(
        preview = preview,
        filePath = filePath,
        manualClockOptions = manualClockOptions,
      )
      runDesktopComposeUiTest {
        if (manualClockOptions != null) {
          mainClock.autoAdvance = false
        }
        with(capturer) { capture(parameter) }
      }
    }
  }

  private fun annotationOptionsFor(preview: ComposablePreview<AndroidPreviewInfo>): RoboComposePreviewOptions {
    // Look up the annotation via reflection instead of preview.getAnnotation()
    // for the same reason as the Robolectric tester:
    // https://github.com/takahirom/roborazzi/pull/633#discussion_r1946472486
    return Class.forName(preview.declaringClass).declaredMethods
      .firstOrNull { it.name == preview.methodName }
      ?.getAnnotation(RoboComposePreviewOptions::class.java)
      ?: RoboComposePreviewOptions()
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
