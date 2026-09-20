package io.github.takahirom.roborazzi

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
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

      // A test task restored from the build cache prints nothing, so this assertion needs a real run.
      record(additionalParameters = arrayOf("--no-build-cache")) {
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
  fun whenEnabledOnAndroidOnlyProjectShouldFailWithGuidance() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.useAndroidOnlyProject = true

      record(BuildType.BuildAndFail) {
        assert(output.contains("no JVM target to generate desktop preview tests for"))
        assert(output.contains("jvm(\"desktop\")"))
        assert(output.contains("use generateComposePreviewRobolectricTests instead"))
      }
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

private const val PROFILE = "com.github.takahirom.roborazzi.DesktopPreviewDeviceProfile"

/**
 * A task restored from the build cache neither forks a test JVM nor prints anything, so these
 * tests, which observe what the test JVM received, have to render for real.
 */
internal val NO_BUILD_CACHE = arrayOf("--no-build-cache")

/**
 * The device profile is carried from the Gradle build to the forked test JVM as an encoded system
 * property, so these tests assert what the test JVM actually received, not only that the task ran.
 * The custom tester prints `options().deviceProfile`, which is the value a tester really sees.
 */
class DesktopPreviewDeviceProfileTest {
  @get:Rule
  val testProjectDir = TemporaryFolder()

  @Test
  fun whenNoDeviceProfileIsConfiguredTheBuildFails() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.useCustomTester = true
      buildGradle.deviceProfile = null

      record(BuildType.BuildAndFail, additionalParameters = NO_BUILD_CACHE) {
        assert(output.contains("has no device profile for")) {
          "Expected configuration to fail when no profile is set, but got:\n$output"
        }
        // The message has to be enough to fix the build without opening the documentation.
        assert(output.contains("DesktopPreviewDeviceProfile.Desktop")) {
          "Expected the failure to offer the Desktop preset, but got:\n$output"
        }
        assert(output.contains("DesktopPreviewDeviceProfile.Pixel4a")) {
          "Expected the failure to offer the Pixel4a preset, but got:\n$output"
        }
      }
    }
  }

  @Test
  fun whenTheDesktopProfileIsConfiguredItReachesTheTestJvm() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.useCustomTester = true
      buildGradle.deviceProfile = "$PROFILE.Desktop"

      record(additionalParameters = NO_BUILD_CACHE) {
        assert(output.contains("deviceProfile defaultDevice=[null]")) {
          "Expected the Desktop profile to reach the test JVM"
        }
      }
    }
  }

  @Test
  fun whenDeviceProfileIsConfiguredItReachesTheTestJvm() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.useCustomTester = true
      buildGradle.deviceProfile = "$PROFILE.Pixel4a"

      record(additionalParameters = NO_BUILD_CACHE) {
        assert(output.contains("deviceProfile defaultDevice=[spec:width=393dp,height=851dp,dpi=440]")) {
          "Expected the configured profile to reach the test JVM"
        }
      }
      checkHasImages()
    }
  }

  @Test
  fun whenDeviceNameContainsASpaceItSurvivesTheCommandLine() {
    // Device names such as the ones behind @Preview(device = Devices.PIXEL_4A) contain spaces, and
    // the profile travels on a forked JVM's command line.
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.useCustomTester = true
      buildGradle.deviceProfile = """$PROFILE.Desktop.copy(defaultDevice = "name:Pixel 4a")"""

      record(additionalParameters = NO_BUILD_CACHE) {
        assert(output.contains("deviceProfile defaultDevice=[name:Pixel 4a]")) {
          "Expected the device name to survive the command line unchanged"
        }
      }
    }
  }

  @Test
  fun whenAnExtraTestRunHasAProfileBothRunsRecordTheSamePreviews() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.separateOutputDirs = true
      buildGradle.extraTestRuns = listOf("androidCompat")
      buildGradle.deviceProfileByTestRun = mapOf("androidCompat" to "$PROFILE.Pixel4a")

      record(additionalParameters = NO_BUILD_CACHE)
      recordVariant("DesktopAndroidCompat", additionalParameters = NO_BUILD_CACHE)

      // Each run gets its own output directory, so the second profile adds baselines instead of
      // overwriting the first one's.
      checkHasImages("desktop")
      checkHasImages("desktopAndroidCompat")
      // An extra test run reuses the target's test compilation, so it must find the same generated
      // tests. A run that discovered none would still be a green build.
      val defaultRunTests = executedTestCount("desktopTest")
      val extraRunTests = executedTestCount("desktopAndroidCompatTest")
      assert(defaultRunTests > 0) { "The default test run executed no tests" }
      assert(defaultRunTests == extraRunTests) {
        "Expected the extra test run to execute the same $defaultRunTests tests, but it executed $extraRunTests"
      }
      // Both directories holding images is not evidence that the extra run used its own profile -
      // it would hold them either way. The default run has no profile and so renders at the pinned
      // density where 1dp is 1px, while Pixel4a renders on a 440dpi screen, so every
      // preview has to come out 2.75x larger there. Comparing at ">= 2x" leaves room for the
      // rounding of a text's measured size without leaving room for the profile being ignored.
      val defaultSizes = recordedImageSizes("desktop")
      val extraSizes = recordedImageSizes("desktopAndroidCompat")
      assert(defaultSizes.keys == extraSizes.keys) {
        "The two runs recorded different previews. Only default: " +
          "${defaultSizes.keys - extraSizes.keys}; only extra: ${extraSizes.keys - defaultSizes.keys}"
      }
      val notDenser = defaultSizes.filterNot { (name, size) ->
        val extra = extraSizes.getValue(name)
        extra.first >= size.first * 2 && extra.second >= size.second * 2
      }
      assert(notDenser.isEmpty()) {
        "These previews came out the same size in both runs, so the extra run did not render at " +
          "its profile's density: " +
          notDenser.keys.joinToString { "$it (${defaultSizes[it]} vs ${extraSizes[it]})" }
      }
    }
  }

  @Test
  fun whenAnExtraTestRunUsesTheDesktopProfileItsDefaultDeviceStaysNull() {
    // An axis left at its default must arrive as "unset", not as an empty string: the encoding
    // travels through argv, where a marker value could be mangled rather than rejected.
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.useCustomTester = true
      buildGradle.separateOutputDirs = true
      buildGradle.extraTestRuns = listOf("explicitDesktop")
      buildGradle.deviceProfileByTestRun = mapOf("explicitDesktop" to "$PROFILE.Desktop")

      recordVariant("DesktopExplicitDesktop", additionalParameters = NO_BUILD_CACHE) {
        assert(output.contains("deviceProfile defaultDevice=[null]")) {
          "Expected an explicitly configured Desktop profile to arrive with no default device"
        }
      }
    }
  }

  @Test
  fun whenTheProfileChangesTheTestTaskRunsAgain() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      record(additionalParameters = NO_BUILD_CACHE)
      record(additionalParameters = NO_BUILD_CACHE) {
        assert(task(":${DesktopPreviewModule.moduleName}:desktopTest")?.outcome == TaskOutcome.UP_TO_DATE) {
          "Expected the unchanged build to be up to date, but was ${task(":${DesktopPreviewModule.moduleName}:desktopTest")?.outcome}"
        }
      }

      buildGradle.deviceProfile = "$PROFILE.Pixel4a"

      record(additionalParameters = NO_BUILD_CACHE) {
        assert(task(":${DesktopPreviewModule.moduleName}:desktopTest")?.outcome == TaskOutcome.SUCCESS) {
          "Expected a profile change to re-render, but the test task was " +
            "${task(":${DesktopPreviewModule.moduleName}:desktopTest")?.outcome}"
        }
      }
    }
  }

  @Test
  fun whenDeviceProfileByTestRunIsUsedWithoutSeparateOutputDirsItFails() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.extraTestRuns = listOf("androidCompat")
      buildGradle.deviceProfileByTestRun = mapOf("androidCompat" to "$PROFILE.Pixel4a")

      record(BuildType.BuildAndFail) {
        assert(output.contains("deviceProfileByTestRun needs"))
        assert(output.contains("separateOutputDirs = true"))
      }
    }
  }

  @Test
  fun whenATestRunDoesNotRunTheTestCompilationItNeedsNoProfile() {
    // The generated preview tests live in the target's `test` compilation. A run pointed at
    // another compilation cannot run them, so demanding a profile for it would fail a build that
    // has nothing to render.
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.extraTestRunsOnMainCompilation = listOf("bench")

      record(additionalParameters = NO_BUILD_CACHE)
      checkHasImages()
    }
  }

  @Test
  fun whenDeviceProfileByTestRunNamesAnUnknownTestRunItFails() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.separateOutputDirs = true
      buildGradle.deviceProfileByTestRun = mapOf("androidCompat" to "$PROFILE.Pixel4a")

      record(BuildType.BuildAndFail) {
        assert(output.contains("deviceProfileByTestRun names the test run(s) [androidCompat]"))
        assert(output.contains("testRuns.create"))
      }
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
    var useAndroidOnlyProject = false

    /** Whether previews that need the same scene are captured without reopening it. */
    var sceneReuse = false

    /** Extra Kotlin test runs to create on the desktop target, e.g. "androidCompat". */
    var extraTestRuns: List<String> = emptyList()

    /**
     * Extra test runs whose execution source is the target's `main` compilation instead of `test`.
     *
     * A run can be pointed at any compilation, and one that does not run the `test` compilation
     * never runs a generated preview test.
     */
    var extraTestRunsOnMainCompilation: List<String> = emptyList()

    /**
     * Kotlin expression for the profile of the target's default test run, or null to leave it out.
     *
     * Defaults to the pre-profile behaviour rather than to null: the plugin requires a profile, so
     * leaving it out is a configuration failure, which is its own test rather than the backdrop to
     * every other one.
     */
    var deviceProfile: String? = "$PROFILE.Desktop"

    /** Kotlin expressions for the profiles of extra test runs, keyed by test run name. */
    var deviceProfileByTestRun: Map<String, String> = emptyMap()

    fun write() {
      val file = projectFolder.root.resolve(PATH)
      file.parentFile.mkdirs()

      if (useAndroidOnlyProject) {
        // A plain Android project (no Kotlin Multiplatform / Kotlin JVM plugin) with
        // the desktop preview generator enabled: must fail fast with guidance.
        file.writeText(
          """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
                id("io.github.takahirom.roborazzi")
            }

            android {
                namespace = "com.github.takahirom.preview.tests"
                compileSdk = libs.versions.compileSdk.get().toInt()
                defaultConfig {
                    minSdk = 24
                }
            }

            roborazzi {
              generateComposePreviewDesktopTests {
                enable = true
                packages = listOf("com.github.takahirom.preview.tests")
              }
            }

            repositories {
                mavenCentral()
                google()
            }
          """.trimIndent()
        )
        return
      }

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
      val extraTestRunsExpr = (
        extraTestRuns.map { """testRuns.create("$it")""" } +
          extraTestRunsOnMainCompilation.map {
            """testRuns.create("$it") { setExecutionSourceFrom(compilations.getByName("main")) }"""
          }
        ).joinToString("\n                ")
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
                $extraTestRunsExpr
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
      val deviceProfileExpr = deviceProfile?.let { """deviceProfile = $it""" } ?: ""
      val sceneReuseExpr = if (sceneReuse) """sceneReuse = true""" else ""
      val deviceProfileByTestRunExpr =
        deviceProfileByTestRun.entries.joinToString("\n                  ") {
          """deviceProfileByTestRun.put("${it.key}", ${it.value})"""
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
                  $deviceProfileExpr
                  $sceneReuseExpr
                  $deviceProfileByTestRunExpr
                }
              }
          """.trimIndent()
    }
  }

  fun record(
    buildType: BuildType = BuildType.Build,
    additionalParameters: Array<String> = arrayOf(),
    checks: BuildResult.() -> Unit = {},
  ) {
    val result = runTask("recordRoborazziDesktop", buildType, additionalParameters)
    result.checks()
  }

  fun verify(
    buildType: BuildType = BuildType.Build,
    additionalParameters: Array<String> = arrayOf(),
    checks: BuildResult.() -> Unit = {},
  ) {
    val result = runTask("verifyRoborazziDesktop", buildType, additionalParameters)
    result.checks()
  }

  /**
   * Records a named variant, e.g. "DesktopAndroidCompat" for the "androidCompat" test run of the
   * "desktop" target.
   */
  fun recordVariant(
    variantName: String,
    buildType: BuildType = BuildType.Build,
    additionalParameters: Array<String> = arrayOf(),
    checks: BuildResult.() -> Unit = {},
  ) {
    val result = runTask("recordRoborazzi$variantName", buildType, additionalParameters)
    result.checks()
  }

  /**
   * The number of test cases the given test task actually executed.
   *
   * The fixture sets `failOnNoDiscoveredTests = false`, so a test run whose compilation carried no
   * generated tests would otherwise look like a pass.
   */
  fun executedTestCount(testTaskName: String): Int {
    val resultsDir = testProjectDir.root.resolve("$moduleName/build/test-results/$testTaskName")
    val xmlFiles = resultsDir.listFiles()?.filter { it.name.endsWith(".xml") }.orEmpty()
    assert(xmlFiles.isNotEmpty()) {
      "Expected JUnit XML results in ${resultsDir.absolutePath}, but found none"
    }
    return xmlFiles.sumOf { file ->
      Regex("tests=\"(\\d+)\"").findAll(file.readText()).sumOf { it.groupValues[1].toInt() }
    }
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

  /** The size of every recorded screenshot, by file name, for asserting on what a profile did. */
  fun recordedImageSizes(outputDirSuffix: String = ""): Map<String, Pair<Int, Int>> =
    testProjectDir.root.resolve("$moduleName/build/outputs/roborazzi/$outputDirSuffix")
      .listFiles()
      .orEmpty()
      .filter { it.name.endsWith(".png") }
      .associate { file ->
        val image = ImageIO.read(file)
        file.name to (image.width to image.height)
      }

  /** The source the plugin generated, so a test can assert which runner it selected. */
  fun generatedTestClassText(className: String): String =
    testProjectDir.root.resolve(
      "$moduleName/build/generated/roborazzi/preview-screenshot/desktop/com/github/takahirom/roborazzi/$className.kt"
    ).readText()

  /** Every recorded screenshot, by file name, so two runs can be compared byte for byte. */
  fun recordedImageBytes(outputDirSuffix: String = ""): Map<String, ByteArray> =
    testProjectDir.root.resolve("$moduleName/build/outputs/roborazzi/$outputDirSuffix")
      .listFiles()
      .orEmpty()
      .filter { it.name.endsWith(".png") }
      .associate { it.name to it.readBytes() }

  /**
   * Deletes what an earlier recording in this project left behind.
   *
   * Nothing in the build removes an image the next run no longer captures, so a test that records
   * twice and compares the two would be comparing the second run against the union of both, and a
   * preview that stopped being captured would look like it was still there. The intermediate
   * directory goes with it: it is what an empty output directory is restored from.
   */
  fun clearRecordedImages() {
    listOf("build/outputs/roborazzi", "build/intermediates/roborazzi").forEach { path ->
      testProjectDir.root.resolve("$moduleName/$path").deleteRecursively()
    }
  }

  /** Replaces a recorded screenshot with a differently-sized one, to make its verify fail. */
  fun corruptRecordedImage(nameFragment: String) {
    val image = testProjectDir.root.resolve("$moduleName/build/outputs/roborazzi/")
      .listFiles()
      .orEmpty()
      .single { it.name.contains(nameFragment) && it.name.endsWith(".png") }
    ImageIO.write(BufferedImage(7, 7, BufferedImage.TYPE_INT_ARGB), "png", image)
  }

  /** The names JUnit reported, which is what `--tests` filters and report diffs are written against. */
  fun reportedTestCaseNames(testTaskName: String = "desktopTest"): Set<String> =
    junitXmlFiles(testTaskName)
      .flatMap { file ->
        Regex("<testcase name=\"([^\"]+)\"").findAll(file.readText()).map { it.groupValues[1] }
      }
      .toSet()

  /**
   * The same names, kept apart by the report they came from, so a test can ask which class ran
   * which preview rather than only which previews ran.
   */
  fun reportedTestCaseNamesByReport(testTaskName: String = "desktopTest"): Map<String, Set<String>> =
    junitXmlFiles(testTaskName).associate { file ->
      file.name to Regex("<testcase name=\"([^\"]+)\"")
        .findAll(file.readText())
        .map { it.groupValues[1] }
        .toSet()
    }

  /** The subset of [reportedTestCaseNames] that JUnit reported as failed. */
  fun failedTestCaseNames(testTaskName: String = "desktopTest"): Set<String> =
    junitXmlFiles(testTaskName)
      .flatMap { file ->
        // A passing testcase is written self-closing; a failed one opens a tag with a
        // <failure> child, so the last character before '>' tells them apart.
        Regex("<testcase name=\"([^\"]+)\"[^>]*[^/]>")
          .findAll(file.readText())
          .map { it.groupValues[1] }
      }
      .toSet()

  private fun junitXmlFiles(testTaskName: String): List<java.io.File> {
    val resultsDir = testProjectDir.root.resolve("$moduleName/build/test-results/$testTaskName")
    val xmlFiles = resultsDir.listFiles()?.filter { it.name.endsWith(".xml") }.orEmpty()
    assert(xmlFiles.isNotEmpty()) {
      "Expected JUnit XML results in ${resultsDir.absolutePath}, but found none"
    }
    return xmlFiles
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
