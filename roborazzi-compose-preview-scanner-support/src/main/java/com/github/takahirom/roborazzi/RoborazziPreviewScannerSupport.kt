package com.github.takahirom.roborazzi

import org.robolectric.RuntimeEnvironment
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.android.screenshotid.AndroidPreviewScreenshotIdBuilder
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

@ExperimentalRoborazziApi
fun ComposablePreview<AndroidPreviewInfo>.captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath("png"),
  roborazziOptions: RoborazziOptions = RoborazziOptions(),
) {
  val composablePreview = this
  composablePreview.applyToRobolectricConfiguration()
  captureRoboImage(filePath = filePath, roborazziOptions = roborazziOptions) {
    composablePreview()
  }
}

@ExperimentalRoborazziApi
interface RobolectricPreviewTest {
  fun previews(vararg packages: String): List<ComposablePreview<AndroidPreviewInfo>>

  fun test(
    preview: ComposablePreview<AndroidPreviewInfo>,
  )
}

@InternalRoborazziApi
class DefaultRobolectricPreviewTest : RobolectricPreviewTest {
  override fun previews(vararg packages: String): List<ComposablePreview<AndroidPreviewInfo>> {
    return AndroidComposablePreviewScanner()
      .scanPackageTrees(*packages)
      .getPreviews()
  }

  override fun test(preview: ComposablePreview<AndroidPreviewInfo>) {
    val pathPrefix =
      if (roborazziRecordFilePathStrategy() == RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory) {
        roborazziSystemPropertyOutputDirectory() + java.io.File.separator
      } else {
        ""
      }
    val filePath = pathPrefix + createScreenshotIdFor(preview) + ".png"
    preview.captureRoboImage(filePath)
  }

  private fun createScreenshotIdFor(preview: ComposablePreview<AndroidPreviewInfo>) =
    AndroidPreviewScreenshotIdBuilder(preview)
      .ignoreClassName()
      .build()
}

@InternalRoborazziApi
fun getRobolectricPreviewTest(robolectricCapturerClass: String): RobolectricPreviewTest {
  val capturerClass = Class.forName(robolectricCapturerClass)
  if (!RobolectricPreviewTest::class.java.isAssignableFrom(capturerClass)) {
    throw IllegalArgumentException("The class $robolectricCapturerClass must implement RobolectricPreviewCapturer")
  }
  val capturer = capturerClass.getDeclaredConstructor().newInstance() as RobolectricPreviewTest
  return capturer
}
