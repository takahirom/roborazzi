package io.github.takahirom.roborazzi

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.HasUnitTest
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.util.Locale

/**
 * Configurator for Android-specific Roborazzi setup.
 * This class is isolated to prevent NoClassDefFoundError when AGP is not on the classpath.
 * It should only be referenced inside withPlugin("com.android.*") blocks.
 */
internal object AndroidRoborazziConfigurator {

  fun configureAndroidApplication(
    project: Project,
    extension: RoborazziExtension,
    configureRoborazziTasks: (variantName: String, testTaskName: String) -> Unit,
    findTestTaskProvider: (testTaskName: String) -> TaskCollection<Test>
  ) {
    val componentsExtension = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
    componentsExtension.configureComponents(
      project = project,
      extension = extension,
      useTestVariantName = false,
      configureRoborazziTasks = configureRoborazziTasks,
      findTestTaskProvider = findTestTaskProvider
    )
    verifyGenerateComposePreviewRobolectricTestsForAndroid(
      project = project,
      androidExtension = project.extensions.getByType(CommonExtension::class.java),
      extension = extension.generateComposePreviewRobolectricTests
    )
  }

  fun configureAndroidLibrary(
    project: Project,
    extension: RoborazziExtension,
    configureRoborazziTasks: (variantName: String, testTaskName: String) -> Unit,
    findTestTaskProvider: (testTaskName: String) -> TaskCollection<Test>
  ) {
    val componentsExtension = project.extensions.getByType(LibraryAndroidComponentsExtension::class.java)
    componentsExtension.configureComponents(
      project = project,
      extension = extension,
      useTestVariantName = false,
      configureRoborazziTasks = configureRoborazziTasks,
      findTestTaskProvider = findTestTaskProvider
    )
    verifyGenerateComposePreviewRobolectricTestsForAndroid(
      project = project,
      androidExtension = project.extensions.getByType(CommonExtension::class.java),
      extension = extension.generateComposePreviewRobolectricTests
    )
  }

  fun configureKmpAndroidLibrary(
    project: Project,
    extension: RoborazziExtension,
    configureRoborazziTasks: (variantName: String, testTaskName: String) -> Unit,
    findTestTaskProvider: (testTaskName: String) -> TaskCollection<Test>
  ) {
    // Since AGP 8.10+, AndroidComponentsExtension can be used with com.android.kotlin.multiplatform.library
    // Note: This plugin uses a single-variant architecture (no build types or product flavors)
    val componentsExtension = project.extensions.getByType(AndroidComponentsExtension::class.java)
    componentsExtension.configureComponents(
      project = project,
      extension = extension,
      useTestVariantName = true,
      configureRoborazziTasks = configureRoborazziTasks,
      findTestTaskProvider = findTestTaskProvider
    )

    // Get KotlinMultiplatformAndroidLibraryTarget from KotlinMultiplatformExtension
    val kotlinMppExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    val androidTarget = kotlinMppExtension.targets
      .withType(KotlinMultiplatformAndroidLibraryTarget::class.java)
      .singleOrNull()
    if (androidTarget != null) {
      // Configure Compose preview tests for each variant
      componentsExtension.onVariants { variant ->
        // AGP 9.0: unitTest is now on HasUnitTest interface, not Variant
        val unitTest = (variant as? HasUnitTest)?.unitTest ?: return@onVariants
        val testVariantSlug = unitTest.name.capitalizeUS()
        val testTaskName = "test$testVariantSlug"
        val testTaskProvider = findTestTaskProvider(testTaskName)
        verifyGenerateComposePreviewRobolectricTestsForKmp(
          project = project,
          kmpTarget = androidTarget,
          extension = extension.generateComposePreviewRobolectricTests,
          testTaskProvider = testTaskProvider
        )
      }
    }
  }

  private fun AndroidComponentsExtension<*, *, *>.configureComponents(
    project: Project,
    extension: RoborazziExtension,
    useTestVariantName: Boolean,
    configureRoborazziTasks: (variantName: String, testTaskName: String) -> Unit,
    findTestTaskProvider: (testTaskName: String) -> TaskCollection<Test>
  ) {
    onVariants { variant ->
      // AGP 9.0: unitTest is now on HasUnitTest interface, not Variant
      val unitTest = (variant as? HasUnitTest)?.unitTest ?: return@onVariants
      val testVariantSlug = unitTest.name.capitalizeUS()
      val testTaskName = "test$testVariantSlug"
      generateComposePreviewRobolectricTestsIfNeeded(
        project = project,
        variant = variant,
        extension = extension.generateComposePreviewRobolectricTests,
        testTaskProvider = findTestTaskProvider(testTaskName)
      )

      // e.g. testDebugUnitTest -> recordRoborazziDebug
      // For KMP library, use test variant name (e.g., AndroidHostTest) instead of source set name (e.g., AndroidMain)
      val roborazziVariantName = if (useTestVariantName) unitTest.name else variant.name
      configureRoborazziTasks(roborazziVariantName, testTaskName)
    }
  }

  private fun String.capitalizeUS() =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}
