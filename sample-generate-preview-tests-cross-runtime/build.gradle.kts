import com.github.takahirom.roborazzi.DesktopPreviewDeviceProfile
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.application")
  id("io.github.takahirom.roborazzi")
  id("org.jetbrains.compose")
  id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * The same `commonMain` previews captured by both runtimes, so that the Compose Desktop output can
 * be checked against the Robolectric output preview by preview.
 *
 * `separateOutputDirs` is what lets the two generators live in one module: the screenshot names are
 * the same on both sides, so without it the second recording would overwrite the first. With it the
 * images land in `build/outputs/roborazzi/debug/` and `build/outputs/roborazzi/desktop/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
roborazzi {
  separateOutputDirs = true

  generateComposePreviewRobolectricTests {
    enable = true
    packages = listOf("com.github.takahirom.preview.crossruntime")
    // API 35: Android's non-linear font scaling only exists from API 34, so the default sdk 33
    // would scale linearly and hide the very difference the fontScale previews are here to show.
    robolectricConfig = mapOf(
      "sdk" to "[35]",
      "qualifiers" to "RobolectricDeviceQualifiers.Pixel4a",
    )
    // One class per runtime. Sharding splits the previews by a sort of their string form, which
    // cuts through the configuration groups scene reuse depends on.
    generatedTestClassCount = 1
  }

  generateComposePreviewDesktopTests {
    enable = true
    packages = listOf("com.github.takahirom.preview.crossruntime")
    targetName = "desktop"
    generatedTestClassCount = 1
    // The whole point of this module is comparing the two runtimes, so the desktop side has to
    // interpret `device` and render at the device density. The default device matches the
    // Pixel 4a qualifier the Robolectric side is configured with above.
    deviceProfile = DesktopPreviewDeviceProfile.AndroidCompatible
  }
}

repositories {
  mavenCentral()
  google()
}

android {
  namespace = "com.github.takahirom.preview.crossruntime"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    minSdk = 24
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
      }
    }
  }
}

kotlin {
  androidTarget()
  jvm("desktop")

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(compose.components.uiToolingPreview)
        implementation(compose.material3)
        implementation(compose.runtime)
        implementation(compose.ui)
        // @RoboComposePreviewOptions on the animated previews, so both runtimes drive the same
        // clock and land on the same frame.
        implementation(project(":roborazzi-annotations"))
      }
    }

    val androidUnitTest by getting {
      dependencies {
        // replaced by dependency substitution
        implementation("io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support:0.1.0")
        implementation(project(":roborazzi-compose"))
        implementation(project(":roborazzi-annotations"))
        implementation(libs.junit)
        implementation(libs.robolectric)
        implementation(libs.composable.preview.scanner)
        implementation(libs.androidx.compose.ui.test.junit4)
      }
    }

    val androidDebug by creating {
      dependencies {
        implementation(libs.androidx.compose.ui.test.manifest)
      }
    }

    val desktopMain by getting {
      dependencies {
        implementation(compose.desktop.currentOs)
      }
    }

    val desktopTest by getting {
      dependencies {
        implementation(project(":roborazzi-compose-desktop"))
        implementation(project(":roborazzi-compose-desktop-preview-scanner-support"))
        implementation(libs.junit)
        implementation(libs.composable.preview.scanner)
      }
    }
  }
}

// ComposablePreviewScanner publishes JVM 17 bytecode, so the desktop target compiles to 17 while
// the rest of the repository stays on the default target. Scoped to the desktop compilations by
// task name: raising every KotlinCompile in this module would raise the Android ones too, and they
// have to stay in step with the Java compatibility AGP is configured with.
tasks.withType<KotlinCompile>().configureEach {
  if (name.contains("Desktop")) {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
    }
  }
}

// The consumer attribute of the KMP test classpaths is not derived from the compiler options above,
// so raise it explicitly to resolve the JVM-17 artifacts.
afterEvaluate {
  listOf("desktopTestCompileClasspath", "desktopTestRuntimeClasspath").forEach { name ->
    configurations.named(name) {
      attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
    }
  }
}

/**
 * The previews whose dimensions the two runtimes are not expected to agree on yet.
 *
 * Every one of them is a text measurement difference, not a sizing one: the desktop runtime now
 * resolves the same surface and density as Robolectric, so what is left is how wide and tall the
 * two rasterizers believe a laid-out string is.
 *
 * - The two `fontScale = 2f` previews differ because Android applies non-linear font scaling from
 *   API 34 while Compose Desktop scales linearly. Adding that converter removes both entries.
 * - The three others are a few pixels of glyph advance in a wrapped `Button`/`Text`.
 *
 * [compareCrossRuntimeOutputs] fails both when a preview outside this list differs and when one
 * inside it stops differing, so the list cannot rot.
 */
val crossRuntimeKnownDifferences = setOf(
  "DefaultButton",
  "LargeFontParagraph.FONT_2_0f",
  "LargeFontSizes.FONT_2_0f",
  "TabletSpecButton.WIDTH_800DP_HEIGHT_1280DP_DPI_240",
  "TabletSpecText.WIDTH_800DP_HEIGHT_1280DP_DPI_240",
  "LandscapeSpecText.WIDTH_800DP_HEIGHT_1280DP_DPI_240_ORIENTATION_LANDSCAPE",
)

/**
 * Compares what the two runtimes produced for the same preview.
 *
 * Both generators name the file after the declaring class and the screenshot id, so the two output
 * directories pair up by file name. Pixels cannot be compared - Skiko and Robolectric's NATIVE
 * graphics rasterize differently - but image dimensions can, and a preview that is missing from one
 * side is always a defect.
 */
// The recordings overwrite the images they capture, but nothing deletes an image they no longer
// capture. A preview that was renamed, removed, or captured by an earlier `-Proborazzi.renderScale`
// run would still be sitting in the output directory, and the comparison below - which inventories
// the directories - would compare it as if this run had produced it. Clearing first is what makes
// the inventory the run's own.
tasks.withType<Test>().configureEach {
  mustRunAfter("clearRoborazziDebug", "clearRoborazziDesktop")
}

tasks.register("compareCrossRuntimeOutputs") {
  dependsOn("clearRoborazziDebug", "clearRoborazziDesktop")
  dependsOn("recordRoborazziDebug", "recordRoborazziDesktop")
  // The record tasks are finalized by tasks that move the images between the intermediate and the
  // output directory. Without this the comparison reads the directory while it is being rewritten
  // and sees a preview as missing.
  mustRunAfter("finalizeTestRoborazziDebug", "finalizeTestRoborazziDesktop")

  val androidDir = layout.buildDirectory.dir("outputs/roborazzi/debug")
  val desktopDir = layout.buildDirectory.dir("outputs/roborazzi/desktop")
  val reportFile = layout.buildDirectory.file("reports/cross-runtime/dimensions.md")
  val knownDifferences = crossRuntimeKnownDifferences

  // The recording tasks and their finalizers rewrite these directories while the build runs, so
  // snapshotting them as inputs races with the rewrite (the failure mode behind issue #830). This
  // task is cheap and always meant to reflect the run that just happened.
  doNotTrackState("compares directories that the recording tasks rewrite on every run")

  doLast {
    fun imagesIn(dir: java.io.File): Map<String, java.io.File> =
      // .annotated.png is the UI tree dump sidecar rendered over the screenshot, not a preview of
      // its own; it would double every row.
      dir.listFiles { file: java.io.File ->
        file.name.endsWith(".png") && !file.name.endsWith(".annotated.png")
      }
        .orEmpty()
        .associateBy { it.name }

    val android = imagesIn(androidDir.get().asFile)
    val desktop = imagesIn(desktopDir.get().asFile)
    val names = (android.keys + desktop.keys).sorted()
    check(names.isNotEmpty()) {
      "No screenshots to compare. Run recordRoborazziDebug and recordRoborazziDesktop first."
    }

    fun size(file: java.io.File?): String {
      if (file == null) return "missing"
      val image = javax.imageio.ImageIO.read(file)
      return "${image.width}x${image.height}"
    }

    fun shortNameOf(name: String): String = name.substringAfter("PreviewsKt.").removeSuffix(".png")

    val rows = names.map { name ->
      val androidSize = size(android[name])
      val desktopSize = size(desktop[name])
      Triple(name, androidSize, desktopSize)
    }
    val missing = rows.filter { it.second == "missing" || it.third == "missing" }
    val mismatched = rows.filter { it !in missing && it.second != it.third }
    val unexpectedlyDifferent =
      mismatched.filterNot { shortNameOf(it.first) in knownDifferences }
    val unexpectedlyEqual = knownDifferences -
      mismatched.map { shortNameOf(it.first) }.toSet()

    val report = buildString {
      appendLine("# Cross-runtime preview output")
      appendLine()
      appendLine("| preview | robolectric | desktop | |")
      appendLine("|---|---|---|---|")
      rows.forEach { (name, androidSize, desktopSize) ->
        val shortName = shortNameOf(name)
        val mark = when {
          androidSize == desktopSize -> "same"
          shortName in knownDifferences -> "differs (known)"
          else -> "differs"
        }
        appendLine("| $shortName | $androidSize | $desktopSize | $mark |")
      }
      appendLine()
      appendLine("${rows.size} previews, ${mismatched.size} differing, ${missing.size} missing.")
    }
    val output = reportFile.get().asFile
    output.parentFile.mkdirs()
    output.writeText(report)
    logger.lifecycle(report)

    check(missing.isEmpty()) {
      "Previews captured by only one runtime: ${missing.joinToString { it.first }}"
    }
    check(unexpectedlyDifferent.isEmpty()) {
      "Dimensions differ between the runtimes for: " +
        unexpectedlyDifferent.joinToString { "${shortNameOf(it.first)} (${it.second} vs ${it.third})" }
    }
    check(unexpectedlyEqual.isEmpty()) {
      "These previews are listed in crossRuntimeKnownDifferences but the runtimes now agree on " +
        "them: ${unexpectedlyEqual.joinToString()}. Remove them from the list."
    }
  }
}
