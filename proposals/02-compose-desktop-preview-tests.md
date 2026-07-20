# Compose Preview Screenshot Tests for Compose Desktop (JVM target)

## Problem

Roborazzi's Compose Preview support (`generateComposePreviewRobolectricTests`) is
Android-only: the generator hooks AGP variants (`AndroidGeneratePreviewTestsConfigurator`)
and the generated tests run under Robolectric. Compose Multiplatform projects with a
`jvm()`/desktop target cannot get preview screenshot tests, even though Roborazzi already
supports desktop screenshots via `roborazzi-compose-desktop`.

Multiplatform common previews are only covered indirectly today: the
`sample-generate-preview-tests-multiplatform` sample scans `commonMain` previews, but
still renders them as Android unit tests under Robolectric.

## Verified feasibility (prototype)

A prototype in `sample-compose-desktop-multiplatform` confirmed that all building blocks
work on the desktop JVM without Robolectric:

- **Scanning**: ComposablePreviewScanner's `:core` and — despite the name — `:android`
  artifacts are pure-JVM jars (deps: core, kotlin-stdlib, kotlin-reflect, classgraph).
  Scanning is classgraph-based FQN matching, so no Android classes are needed on the
  classpath. `AndroidComposablePreviewScanner` finds the multiplatform
  `androidx.compose.ui.tooling.preview.Preview` annotation on the desktop classpath.
  (The deprecated `:common` scanner / `org.jetbrains.compose...Preview` annotation also
  works, but `:common` is removed in ComposablePreviewScanner 0.10.0, so this proposal
  targets the `:android` scanner path only.)
- **Rendering/capture**: `runDesktopComposeUiTest { setContent { preview() } }` +
  `onRoot().captureRoboImage()` works, and previews without size parameters are captured
  wrap-content sized (e.g. 402x24), not window-sized.

### JVM version friction (verified)

ComposablePreviewScanner publishes Gradle module metadata with
`org.gradle.jvm.version: 17` and its jars are genuinely Java 17 bytecode (classfile
major 61). Consumers with `jvmTarget = 11` fail at dependency resolution.

Surveyed ecosystem practice: AndroidX (core 1.18.0, compose-runtime 1.11.4,
activity 1.13.0 — Java 8 bytecode; lifecycle 2.11.0 — Java 11), kotlinx-coroutines, and
Roborazzi's own published artifacts all target low bytecode and do **not** publish the
`org.gradle.jvm.version` attribute. The scanner's 17 requirement appears to be a
toolchain-default artifact rather than a deliberate restriction.

Decisions:
- The repository keeps `javaTarget = 11`. Only the new desktop module and desktop
  samples use 17 (desktop users need 17 for the scanner anyway).
- File an upstream issue asking ComposablePreviewScanner to lower its `jvmTarget`
  (or drop the attribute). Until then the desktop module/samples relax the test
  classpath `TargetJvmVersion` attribute to 17; remove the workaround once upstream
  changes.

## Proposed Implementation

### 1. New tester module: `roborazzi-compose-desktop-preview-scanner-support`

Named as an extension of `roborazzi-compose-desktop` (its actual dependency base), so it
clusters with the platform module in artifact listings; a future iOS module would follow
the same pattern (`roborazzi-compose-ios-...`).

The existing `ComposePreviewTester` API is coupled to Android
(`androidx.compose.ui.test.junit4` rules, `ActivityScenario`), and desktop rendering is
function-scoped (`runDesktopComposeUiTest`) rather than rule-based, so the desktop tester
is a separate interface — no forced commonization. It mirrors the Robolectric tester's
shape (options / testParameters / test, parameterless constructor, instantiated via
reflection by the generator). Test cases are carried by a lightweight
`DesktopPreviewTestParameter` (deliberately not a data class, so fields can be added
without breaking binary compatibility), and manual clock variations expand to one test
parameter each — matching the Robolectric tests' per-variation granularity. JUnit
`TestRule` injection is available via `JUnit4TestLifecycleOptions.testRuleFactory`;
unlike Android there is no compose rule factory, because Compose Desktop has no
rule-based harness:

```kotlin
@ExperimentalRoborazziApi
interface DesktopComposePreviewTester {
  data class Options(
    val testLifecycleOptions: TestLifecycleOptions = JUnit4TestLifecycleOptions(),
    val scanOptions: ScanOptions = ScanOptions(packages = emptyList()),
  ) {
    interface TestLifecycleOptions
    data class JUnit4TestLifecycleOptions(
      val testRuleFactory: () -> TestRule = { TestRule { base, _ -> base } },
    ) : TestLifecycleOptions
  }
  fun options(): Options
  fun testParameters(): List<DesktopPreviewTestParameter>
  fun test(testParameter: DesktopPreviewTestParameter)
}

class DesktopPreviewTestParameter(
  val preview: ComposablePreview<AndroidPreviewInfo>,
  val manualClockOptions: ManualClockOptions? = null,
)
```

`ComposablePreview<AndroidPreviewInfo>` is exposed as-is (no typealias cosmetics): the
"Android" in the name comes from the scanner artifact, but the type is pure JVM and
carries the preview parameters (`widthDp` etc.).

**Customization principle: never dead-end the user.** The default implementation uses
Capturer composition (proposal 01 pattern) and hands the capturer the raw `ComposeUiTest`
scope as receiver, so anything possible inside `runDesktopComposeUiTest` — `mainClock`
control, interactions, wrapping content in a theme — stays possible without forking:

```kotlin
@ExperimentalRoborazziApi
class DefaultDesktopComposePreviewTester(
  private val capturer: Capturer = DefaultCapturer()
) : DesktopComposePreviewTester {

  fun interface Capturer {
    fun ComposeUiTest.capture(parameter: CaptureParameter)
  }

  data class CaptureParameter(
    val preview: ComposablePreview<AndroidPreviewInfo>,
    val filePath: String,            // precomputed default output path (incl. _TIME_Xms suffix)
    val roborazziOptions: RoborazziOptions,
    val manualClockOptions: ManualClockOptions?,  // set for @RoboComposePreviewOptions variations
    val content: @Composable () -> Unit,          // preview wrapped with its @Preview options
  )

  class DefaultCapturer : Capturer {
    override fun ComposeUiTest.capture(parameter: CaptureParameter) {
      // content (not preview) so the @Preview annotation options are honored.
      setContent(parameter.content)
      // Keeps @RoboComposePreviewOptions manual clock variations working; the tester
      // has already disabled mainClock.autoAdvance for them.
      advanceMainClockFor(parameter)
      onRoot().captureRoboImage(parameter.filePath, parameter.roborazziOptions)
    }
  }
}
```

Last-resort escape hatch: implement `DesktopComposePreviewTester` via delegation and
replace `test()` entirely, same as the Robolectric tester.

### Screenshot naming and output

Identical to the Robolectric preview tests: FQCN prefix +
`AndroidPreviewScreenshotIdBuilder(preview).ignoreClassName().build()`, e.g.
`com.example.HomeScreenKt.LoadingPreview.png`. The FQCN prefix prevents collisions
between same-named previews in different classes; the builder suffix disambiguates
multipreview/parameters. The same preview produces the same file name on Android and
desktop, keeping cross-platform comparison obvious.

Output goes to the standard `DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH` with **no**
feature-specific subdirectory. Modules that run both Robolectric and desktop preview
tests would otherwise overwrite each other's images — that is exactly what the existing
experimental `separateOutputDirs` option solves (per task-slug subdirectories), so the
docs point there, and the generator fails with a configuration error when both preview
generators are enabled in one module without `separateOutputDirs = true` (a warning
would let one recording run silently corrupt the other baseline).

### 2. New Gradle extension: `generateComposePreviewDesktopTests`

```kotlin
roborazzi {
  @OptIn(ExperimentalRoborazziApi::class)
  generateComposePreviewDesktopTests {
    enable = true
    packages = listOf("com.example")
    testerQualifiedClassName = "com.example.MyDesktopPreviewTester" // optional
    annotationFilter = ...            // same semantics as the Robolectric generator
    includePrivatePreviews = ...      // same semantics as the Robolectric generator
  }
}
```

Generated tests go into the JVM target's **test compilation only** (never main).

**Target resolution** (KMP JVM target names are arbitrary, e.g. `jvm("desktop")`):
- Exactly one `KotlinJvmTarget` → generate for it, no configuration needed.
- Multiple JVM targets → require an explicit `targetName = "desktop"`; fail with a
  message listing the found targets. No implicit generation into all JVM targets
  (a non-UI `jvm("server")` target would not even compile the generated tests).
- Plain-JVM projects (`org.jetbrains.kotlin.jvm`) are supported (single implicit target).

Usage: `./gradlew recordRoborazziDesktop` / `verifyRoborazziDesktop` (existing per-target
task scheme).

### Where shared logic lives

`roborazzi-core` stays scanner-free: it is on every user's classpath and builds for
`iosArm64()`, where the JVM-only scanner cannot exist. Glue that touches scanner types
(`ComposablePreview`, screenshot-id building, filter resolution) lives in the
scanner-support modules. If duplication with the Android scanner-support module becomes
painful, share sources via the repository's existing `shared-sources/` srcDir pattern
(as `roborazzi-compose-desktop` already does) rather than converting the published
Android library module to KMP.

## Verification

- Promote the prototype in `sample-compose-desktop-multiplatform` to a proper sample and
  regression test: rewritten on the new module and run as a plain unit test in CI, with
  screenshots covered by the repository's artifact-based record/compare workflows
  (StoreScreenshot / CompareScreenshot) rather than committed goldens. No new sample
  module.
- Generator: integration test cases in `include-build/roborazzi-gradle-plugin`
  (alongside `PreviewGenerateTest`).

## Delivery plan (stacked PRs, released together)

1. This proposal document.
2. `roborazzi-compose-desktop-preview-scanner-support` module (tester + capturer +
   naming) + sample promotion + docs (`preview_support.md` Compose Desktop section).
3. `generateComposePreviewDesktopTests` extension: target resolution, annotation
   filters, `includePrivatePreviews`, mixed-module configuration error, integration tests.
4. `@RoboComposePreviewOptions` (`manualClockOptions`) support via desktop `mainClock`.
5. Docs (Compose Desktop section, trade-offs, parity table) and a review-fix pass.
6. `@Preview` annotation options on desktop (widthDp/heightDp, fontScale,
   showBackground/backgroundColor, locale, uiMode dark bit) + fail-fast guidance when
   enabled without a Kotlin JVM target.

Each PR updates docs and runs `./gradlew generateReadme`.

Separate track: upstream issue on ComposablePreviewScanner requesting a lower
`jvmTarget`; drop the classpath-attribute workaround when resolved.

## Non-goals / future work

- **iOS**: runtime scanning is impossible on Kotlin/Native (classgraph is JVM-only).
  iOS would require scanning at build time on the host JVM and code-generating one test
  function per preview (direct static calls). Rendering shares the same pipeline as
  desktop, so this stays feasible, but it is a separate proposal.
- **Preview parameters**: `AndroidPreviewInfo.widthDp`/`heightDp`, `fontScale`,
  `showBackground`/`backgroundColor`, `locale`, and the `uiMode` dark bit are now applied
  on desktop (previews still render wrap-content when no size is specified). `device` stays
  not applicable, as desktop has no device configuration. See the parity table in the docs.
- Commonizing `DesktopComposePreviewTester` with the Android `ComposePreviewTester`:
  revisit only after custom-tester demand materializes on both platforms.
