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
interface ComposePreviewTester<T : Any> {
  /**
   * Retrieves a list of composable previews from the specified packages.
   *
   * @param packages Vararg parameter representing the package names to scan for previews.
   * @return A list of ComposablePreview objects of type T.
   */
  fun previews(vararg packages: String): List<ComposablePreview<T>>

  /**
   * Performs a test on a single composable preview.
   * Note: This method will not be called as the same instance of previews() method.
   *
   * @param preview The ComposablePreview object to be tested.
   */
  fun test(
    preview: ComposablePreview<T>,
  )
}

@InternalRoborazziApi
class AndroidComposePreviewTester : ComposePreviewTester<AndroidPreviewInfo> {
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
    val name = roborazziDefaultNamingStrategy().generateOutputName(
      preview.declaringClass,
      createScreenshotIdFor(preview)
    )
    val filePath = "$pathPrefix$name.png"
    preview.captureRoboImage(filePath)
  }

  private fun createScreenshotIdFor(preview: ComposablePreview<AndroidPreviewInfo>) =
    AndroidPreviewScreenshotIdBuilder(preview)
      .ignoreClassName()
      .build()
}

@InternalRoborazziApi
fun getComposePreviewRobolectricTest(testerQualifiedClassName: String): ComposePreviewTester<Any> {
  val customTesterClass = try {
    Class.forName(testerQualifiedClassName)
  } catch (e: ClassNotFoundException) {
    throw IllegalArgumentException("The class $testerQualifiedClassName not found")
  }
  if (!ComposePreviewTester::class.java.isAssignableFrom(customTesterClass)) {
    throw IllegalArgumentException("The class $testerQualifiedClassName must implement RobolectricPreviewCapturer")
  }
  @Suppress("UNCHECKED_CAST")
  val composePreviewTester =
    customTesterClass.getDeclaredConstructor().newInstance() as ComposePreviewTester<Any>
  return composePreviewTester
}
