package io.github.takahirom.roborazzi

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.gradle.testkit.runner.BuildResult
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopPreviewGenerateTest {
  @get:Rule
  val testProjectDir = TemporaryFolder()

  @Test
  fun whenRecordRunImagesShouldBeRecorded() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      record()

      checkHasImages()
      checkHasGeneratedTestClass("RoborazziDesktopPreviewParameterizedTests")
    }
  }

  @Test
  fun whenDisablePreviewAndRecordRunImagesShouldNotBeRecorded() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.enable = false

      record()

      checkNoImages()
    }
  }

  @Test
  fun whenMultipleJvmTargetsWithoutTargetNameShouldFail() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.hasSecondJvmTarget = true

      record(BuildType.BuildAndFail) {
        assert(output.contains("This project has multiple Kotlin JVM targets"))
        assert(output.contains("generateComposePreviewDesktopTests.targetName"))
      }
    }
  }

  @Test
  fun whenTargetNameSpecifiedShouldBeRecorded() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.targetName = "desktop"

      record()

      checkHasImages()
    }
  }

  @Test
  fun whenWrongTargetNameShouldFail() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.targetName = "nonexistent"

      record(BuildType.BuildAndFail) {
        assert(output.contains("generateComposePreviewDesktopTests.targetName is set to 'nonexistent'"))
      }
    }
  }

  @Test
  fun whenNotIncludingPreviewScannerSupportDependencyAndRecordShouldBeError() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.includePreviewScannerSupportDependency = false

      record(BuildType.BuildAndFail) {
        assert(output.contains("io.github.takahirom.roborazzi:roborazzi-compose-desktop-preview-scanner-support"))
      }
    }
  }

  @Test
  fun whenIncludePrivatePreviewsAndRecordRunImagesShouldBeRecorded() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.isIncludePrivatePreviews = true

      record()

      checkHasPrivatePreviewImages()
    }
  }

  @Test
  fun whenCustomTesterAndRecordRunImagesShouldBeRecorded() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.useCustomTester = true

      record {
        assert(output.contains("CustomDesktopPreviewTester testParameters() is called"))
        // The custom testRuleFactory rule is wrapped around each generated test.
        assert(output.contains("CustomDesktopPreviewTester JUnit4TestLifecycleOptions starting"))
      }

      checkHasImages()
    }
  }

  @Test
  fun whenMixedWithRobolectricPreviewTestsWithoutSeparateOutputDirsShouldFail() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.enableRobolectricPreviewTests = true

      record(BuildType.BuildAndFail) {
        assert(output.contains("would overwrite each other"))
        assert(output.contains("separateOutputDirs"))
      }
    }
  }

  @Test
  fun whenMixedWithRobolectricPreviewTestsWithSeparateOutputDirsShouldBeRecorded() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.enableRobolectricPreviewTests = true
      buildGradle.separateOutputDirs = true

      record()

      checkHasImages(outputDirSuffix = "desktop/")
    }
  }

  @Test
  fun whenPreviewParameterProviderShouldCaptureEachValue() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      record()

      // One screenshot per PreviewParameterProvider value.
      checkHasImageContaining("PreviewWithParameter_0")
      checkHasImageContaining("PreviewWithParameter_1")
    }
  }

  @Test
  fun whenManualClockOptionsShouldCaptureTimeVariations() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      record()

      checkHasImageContaining("PreviewDelayed_TIME_0ms")
      checkHasImageContaining("PreviewDelayed_TIME_1032ms")
    }
  }

  @Test
  fun whenPreviewAnnotatedWithRoboPreviewExcludeShouldBeSkipped() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      record()

      checkHasImages()
      checkNoImageContaining("PreviewExcluded.png")
    }
  }

  @Test
  fun whenCustomNestedAnnotationFilterShouldEscapeDollarAndExclude() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      // JVM binary name of a nested annotation: contains '$', which the generated
      // Kotlin code must escape to stay compilable.
      buildGradle.annotationFilterExcludeBinaryName =
        "com.github.takahirom.preview.tests.Filters\$CustomExclude"

      record()

      checkHasImages()
      checkNoImageContaining("PreviewExcludedByCustomAnnotation")
    }
  }

  @Test
  fun whenPreviewAnnotationOptionsShouldBeApplied() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      record()

      // widthDp/heightDp: density is 1 on desktop, so 300dp x 150dp -> exactly 300x150 px.
      val fixedSize = imageContaining("PreviewFixedSize")
      assert(fixedSize.width == 300 && fixedSize.height == 150) {
        "Expected PreviewFixedSize to be 300x150 px, but was ${fixedSize.width}x${fixedSize.height}"
      }

      // fontScale: the 2x preview renders taller than its default-scale sibling.
      val fontDefault = imageContaining("PreviewFontScaleDefault")
      val fontLarge = imageContaining("PreviewFontScaleLarge")
      assert(fontLarge.height > fontDefault.height) {
        "Expected PreviewFontScaleLarge (${fontLarge.height}px tall) to be taller than " +
          "PreviewFontScaleDefault (${fontDefault.height}px tall)"
      }

      // showBackground + backgroundColor = 0xFF0000FF: a blue pixel is present.
      val background = imageContaining("PreviewBackgroundBlue")
      assert(hasBluePixel(background)) {
        "Expected PreviewBackgroundBlue to contain a blue background pixel"
      }

      // uiMode dark bit: the dark preview draws its dark-theme background (black), because
      // the night bit sets LocalSystemTheme = Dark, so isSystemInDarkTheme() is true.
      // (We don't compare against PreviewUiModeLight: on a machine whose OS is in dark mode
      // the ambient LocalSystemTheme is already Dark, so the light variant would match.)
      val uiDark = imageContaining("PreviewUiModeDark")
      val darkCenter = uiDark.getRGB(uiDark.width / 2, uiDark.height / 2)
      assert(isDark(darkCenter)) {
        "Expected PreviewUiModeDark center pixel to be dark (night bit applied), " +
          "but was #${Integer.toHexString(darkCenter)}"
      }

      // locale: en and ja variants render different language tags, deterministically
      // regardless of the host machine's default locale.
      val localeEn = imageContaining("PreviewLocaleEn")
      val localeJa = imageContaining("PreviewLocaleJa")
      assert(!imagesEqual(localeEn, localeJa)) {
        "Expected PreviewLocaleJa to differ from PreviewLocaleEn"
      }
    }
  }

  @Test
  fun whenGeneratedTestClassCountIs2ShouldGenerateMultipleTestClasses() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.generatedTestClassCount = 2

      record()

      checkHasImages()
      checkGeneratedTestClassCount(2)
      checkHasGeneratedTestClass("RoborazziDesktopPreviewParameterizedTests0")
      checkHasGeneratedTestClass("RoborazziDesktopPreviewParameterizedTests1")
    }
  }
}

class DesktopPreviewModule(
  val rootProject: RoborazziGradleRootProject,
  val testProjectDir: TemporaryFolder
) {
  companion object {
    val moduleName = "sample-generate-preview-desktop-tests"
  }

  val buildGradle = BuildGradle(testProjectDir)

  class BuildGradle(private val projectFolder: TemporaryFolder) {
    private val PATH = moduleName + "/build.gradle.kts"
    var enable = true
    var targetName: String? = null
    var hasSecondJvmTarget = false
    var includePreviewScannerSupportDependency = true
    var isIncludePrivatePreviews = false
    var useCustomTester = false
    var generatedTestClassCount: Int? = null
    var enableRobolectricPreviewTests = false
    var separateOutputDirs = false
    var annotationFilterExcludeBinaryName: String? = null

    fun write() {
      val file = projectFolder.root.resolve(PATH)
      file.parentFile.mkdirs()

      val previewScannerSupportDependency = if (includePreviewScannerSupportDependency) {
        // replaced by dependency substitution
        """implementation("io.github.takahirom.roborazzi:roborazzi-compose-desktop-preview-scanner-support:0.1.0")"""
      } else {
        ""
      }
      val secondJvmTarget = if (hasSecondJvmTarget) {
        // Two JVM targets need a distinguishing attribute to be resolvable.
        """
            jvm("server") {
                attributes.attribute(Attribute.of("com.github.takahirom.roborazzi.sample.target", String::class.java), "server")
            }
        """.trimIndent()
      } else {
        ""
      }
      val desktopTargetAttribute = if (hasSecondJvmTarget) {
        """
                attributes.attribute(Attribute.of("com.github.takahirom.roborazzi.sample.target", String::class.java), "desktop")
        """.trimIndent()
      } else {
        ""
      }

      val buildGradleText = """
        plugins {
            kotlin("multiplatform")
            id("org.jetbrains.compose")
            id("org.jetbrains.kotlin.plugin.compose")
            id("io.github.takahirom.roborazzi")
        }

        // ComposablePreviewScanner publishes JVM 17 metadata, so the desktop test
        // classpaths must request 17.
        afterEvaluate {
            listOf("desktopTestCompileClasspath", "desktopTestRuntimeClasspath").forEach { name ->
                configurations.named(name) {
                    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
                }
            }
        }
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
        }

        // Gradle 9 fails test tasks that discover zero tests; with the preview
        // generation disabled this module has no tests at all.
        tasks.withType<Test>().configureEach {
            failOnNoDiscoveredTests = false
        }

        kotlin {
            jvm("desktop") {
                $desktopTargetAttribute
            }
            $secondJvmTarget

            sourceSets {
                val commonMain by getting {
                    dependencies {
                        implementation(compose.runtime)
                        implementation(compose.material3)
                        api(compose.components.uiToolingPreview)
                        // replaced by dependency substitution
                        implementation("io.github.takahirom.roborazzi:roborazzi-annotations:0.1.0")
                    }
                }
                val desktopMain by getting {
                    dependencies {
                        implementation(compose.desktop.currentOs)
                    }
                }
                val desktopTest by getting {
                    dependencies {
                        $previewScannerSupportDependency
                        implementation(libs.junit)
                        implementation("io.github.sergio-sastre.ComposablePreviewScanner:android:0.9.1")
                    }
                }
            }
        }

        ${createRoborazziExtension()}

        repositories {
            mavenCentral()
            google()
        }
      """.trimIndent()

      file.writeText(buildGradleText)
    }

    private fun createRoborazziExtension(): String {
      val targetNameExpr = if (targetName != null) {
        """targetName = "$targetName""""
      } else {
        ""
      }
      val includePrivatePreviewsExpr = if (isIncludePrivatePreviews) {
        """includePrivatePreviews = $isIncludePrivatePreviews"""
      } else {
        ""
      }
      val customTesterExpr = if (useCustomTester) {
        """testerQualifiedClassName = "com.github.takahirom.sample.CustomDesktopPreviewTester""""
      } else {
        ""
      }
      val generatedTestClassCountExpr = if (generatedTestClassCount != null) {
        """generatedTestClassCount = $generatedTestClassCount"""
      } else {
        ""
      }
      val annotationFilterExpr = if (annotationFilterExcludeBinaryName != null) {
        // Keep the real '$' out of the written Kotlin DSL string template.
        val ktsSafe = annotationFilterExcludeBinaryName!!.replace("$", "\${'\$'}")
        """annotationFilter = com.github.takahirom.roborazzi.AnnotationFilter.Exclude("$ktsSafe")"""
      } else {
        ""
      }
      val separateOutputDirsExpr = if (separateOutputDirs) {
        """separateOutputDirs = true"""
      } else {
        ""
      }
      val robolectricPreviewTestsExpr = if (enableRobolectricPreviewTests) {
        """
                generateComposePreviewRobolectricTests {
                  enable = true
                  packages = listOf("com.github.takahirom.preview.tests")
                }
        """.trimIndent()
      } else {
        ""
      }
      return """
              roborazzi {
                $separateOutputDirsExpr
                $robolectricPreviewTestsExpr
                generateComposePreviewDesktopTests {
                  enable = $enable
                  packages = listOf("com.github.takahirom.preview.tests")
                  $targetNameExpr
                  $includePrivatePreviewsExpr
                  $customTesterExpr
                  $generatedTestClassCountExpr
                  $annotationFilterExpr
                }
              }
          """.trimIndent()
    }
  }

  fun record(buildType: BuildType = BuildType.Build, checks: BuildResult.() -> Unit = {}) {
    val result = runTask("recordRoborazziDesktop", buildType)
    result.checks()
  }

  private fun runTask(
    task: String,
    buildType: BuildType = BuildType.Build,
    additionalParameters: Array<String> = arrayOf()
  ): BuildResult {
    buildGradle.write()
    val buildResult = rootProject.runTask(
      ":$moduleName:" + task,
      buildType,
      additionalParameters
    )
    return buildResult
  }

  fun checkHasImages(outputDirSuffix: String = "") {
    val images =
      testProjectDir.root.resolve("$moduleName/build/outputs/roborazzi/$outputDirSuffix").listFiles()
    assert(images?.isNotEmpty() == true) {
      "Expected screenshots in build/outputs/roborazzi/$outputDirSuffix, but found: ${images?.toList()}"
    }
  }

  fun checkNoImages() {
    val images = testProjectDir.root.resolve("$moduleName/build/outputs/roborazzi/").listFiles()
    assert(images == null || images.isEmpty()) {
      "Expected no screenshots in build/outputs/roborazzi, but found: ${images?.toList()}"
    }
  }

  fun checkHasImageContaining(nameFragment: String) {
    val images =
      testProjectDir.root.resolve("$moduleName/build/outputs/roborazzi/").listFiles()
        .orEmpty()
        .filter { it.name.contains(nameFragment) }
    assert(images.isNotEmpty()) {
      "Expected screenshots containing '$nameFragment', but found none"
    }
  }

  fun checkNoImageContaining(nameFragment: String) {
    val images =
      testProjectDir.root.resolve("$moduleName/build/outputs/roborazzi/").listFiles()
        .orEmpty()
        .filter { it.name.contains(nameFragment) }
    assert(images.isEmpty()) {
      "Expected no screenshots containing '$nameFragment', but found: $images"
    }
  }

  fun imageContaining(nameFragment: String): BufferedImage {
    val images =
      testProjectDir.root.resolve("$moduleName/build/outputs/roborazzi/").listFiles()
        .orEmpty()
        .filter { it.name.contains(nameFragment) && it.name.endsWith(".png") }
    assert(images.size == 1) {
      "Expected exactly one screenshot containing '$nameFragment', but found: ${images.map { it.name }}"
    }
    return ImageIO.read(images.single())
  }

  fun checkHasPrivatePreviewImages() {
    val privateImages =
      testProjectDir.root.resolve("$moduleName/build/outputs/roborazzi/").listFiles()
        .orEmpty()
        .filter { it.name.contains("PreviewWithPrivate") }
    assert(privateImages.isNotEmpty()) {
      "Expected private preview screenshots, but found none"
    }
  }

  fun checkGeneratedTestClassCount(expectedCount: Int) {
    val generatedDir = testProjectDir.root.resolve(
      "$moduleName/build/generated/roborazzi/preview-screenshot/desktop/com/github/takahirom/roborazzi/"
    )
    val testFiles = generatedDir.listFiles()?.filter { it.name.endsWith(".kt") }.orEmpty()
    assert(testFiles.size == expectedCount) {
      "Expected $expectedCount generated test classes, but found ${testFiles.size}: ${testFiles.map { it.name }}"
    }
  }

  fun checkHasGeneratedTestClass(className: String) {
    val generatedFile = testProjectDir.root.resolve(
      "$moduleName/build/generated/roborazzi/preview-screenshot/desktop/com/github/takahirom/roborazzi/$className.kt"
    )
    assert(generatedFile.exists()) {
      "Expected generated test class $className.kt to exist at ${generatedFile.absolutePath}"
    }
  }
}

private fun hasBluePixel(image: BufferedImage): Boolean {
  for (y in 0 until image.height) {
    for (x in 0 until image.width) {
      val rgb = image.getRGB(x, y)
      val r = (rgb shr 16) and 0xFF
      val g = (rgb shr 8) and 0xFF
      val b = rgb and 0xFF
      if (b > 200 && r < 60 && g < 60) return true
    }
  }
  return false
}

private fun isDark(rgb: Int): Boolean {
  val r = (rgb shr 16) and 0xFF
  val g = (rgb shr 8) and 0xFF
  val b = rgb and 0xFF
  return r < 60 && g < 60 && b < 60
}

private fun imagesEqual(a: BufferedImage, b: BufferedImage): Boolean {
  if (a.width != b.width || a.height != b.height) return false
  for (y in 0 until a.height) {
    for (x in 0 until a.width) {
      if (a.getRGB(x, y) != b.getRGB(x, y)) return false
    }
  }
  return true
}
