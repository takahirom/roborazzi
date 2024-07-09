package com.github.takahirom.roborazzi

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
fun getRobolectricPreviewTest(customTestQualifiedClassName: String): RobolectricPreviewTest {
  val customTestClass = try {
    Class.forName(customTestQualifiedClassName)
  } catch (e: ClassNotFoundException) {
    throw IllegalArgumentException("The class $customTestQualifiedClassName not found")
  }
  if (!RobolectricPreviewTest::class.java.isAssignableFrom(customTestClass)) {
    throw IllegalArgumentException("The class $customTestQualifiedClassName must implement RobolectricPreviewCapturer")
  }
  val robolectricPreviewTest =
    customTestClass.getDeclaredConstructor().newInstance() as RobolectricPreviewTest
  return robolectricPreviewTest
}
