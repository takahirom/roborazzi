---
name: roborazzi
description: >-
  Set up and write JVM/Robolectric screenshot tests with Roborazzi for Android
  Views and Jetpack Compose (including Compose Multiplatform / iOS). Use when
  adding screenshot testing to a project, capturing/recording/verifying/comparing
  screenshots, generating tests from @Preview, or doing AI-powered image
  assertion. Covers the Gradle plugin, the capture APIs (captureRoboImage), the
  record/compare/verify tasks, and gradle.properties options.
---

# Roborazzi

Roborazzi makes JVM Android tests visible by capturing screenshots with
Robolectric. It works with Jetpack Compose, Espresso/Views, and Compose
Multiplatform. Tests run on the JVM (`test/`), not on a device.

> The `references/` files in this skill are **generated from
> `docs/topics/*.md`** (single source of truth). To update them, edit the
> matching file under `docs/topics/` and run `./gradlew generateDocs`. Do not
> edit `references/` by hand.

## Decision flow

1. **Is the plugin set up?** Check for `id("io.github.takahirom.roborazzi")` in
   the module `build.gradle(.kts)`. If not → **Set up** below.
2. **Writing a test?** → **Capture a screenshot** below.
3. **Generating tests from `@Preview`?** → **Compose Preview** below.
4. **Running the tests?** → **Record / compare / verify** below.
5. **Asserting image *content* (not just pixels)?** → **AI-powered assertion**.
6. **Compose Multiplatform / iOS?** → `references/compose_multiplatform.md`.
7. **Stuck / edge case?** → `references/faq.md`.

## Set up

Apply the Gradle plugin and add dependencies. Full matrix (plugins vs.
buildscript, Kotlin vs. Groovy DSL) is in `references/build_setup.md`.

```kotlin
// root build.gradle.kts
plugins {
  id("io.github.takahirom.roborazzi") version "[version]" apply false
}

// module build.gradle.kts
plugins {
  id("io.github.takahirom.roborazzi")
}

dependencies {
  testImplementation("io.github.takahirom.roborazzi:roborazzi:[version]")
  // Compose:
  testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:[version]")
  // JUnit rule:
  testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:[version]")
}
```

Use the latest version from Maven Central. Enable Robolectric Native Graphics
and Android resources in unit tests — see `references/gradle_properties_options.md`
(`robolectric.pixelCopyRenderMode=hardware` is recommended).

## Capture a screenshot

Call `captureRoboImage()`. With no path, the file name is derived from the test
method. Full API table (Compose nodes, Espresso views, bitmaps, etc.) is in
`references/how_to_use.md`.

```kotlin
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ManualTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun captureSample() {
    // Compose node
    composeTestRule.onNodeWithTag("AddBoxButton").captureRoboImage()
    // Espresso view
    onView(ViewMatchers.isRoot()).captureRoboImage()
  }
}
```

## Record / compare / verify

Prefer the dedicated Gradle tasks (replace `Debug` with your variant):

| Goal | Task |
|------|------|
| Save baselines | `./gradlew recordRoborazziDebug` |
| Produce `*_compare.png` diff images | `./gradlew compareRoborazziDebug` |
| Fail on any diff (CI) | `./gradlew verifyRoborazziDebug` |
| Verify, re-record if changed | `./gradlew verifyAndRecordRoborazziDebug` |

Equivalents via the unit test task use `-Proborazzi.test.record=true`
(`compare` / `verify`). Outputs default to `build/outputs/roborazzi`; the HTML
report is at `build/reports/roborazzi/index.html`. See `references/build_setup.md`
for output dir customization and `references/gradle_properties_options.md` for
naming/resize/cleanup options.

## Compose Preview

Generate Robolectric screenshot tests from existing `@Preview` composables:

```kotlin
roborazzi {
  generateComposePreviewRobolectricTests {
    enable = true
    packages = listOf("com.example")
  }
}
```

The plugin tells you which extra dependencies/code to add (it won't change
settings silently). Then run `recordRoborazziDebug`. Customization (Robolectric
config, private previews, annotation filters, parallel test classes, custom
`ComposePreviewTester`) is in `references/preview_support.md`.

## AI-powered assertion (experimental)

Assert image *content* with an LLM. Modules: `roborazzi-ai-gemini`,
`roborazzi-ai-openai`, or a manual `AiAssertionModel` for local/other LLMs.
It only runs when images differ (to save cost). **Never hardcode API keys.**

```kotlin
.captureRoboImage(
  roborazziOptions = provideRoborazziContext().options.addedAiAssertions(
    AiAssertionOptions.AiAssertion(
      assertionPrompt = "it should have a PREVIOUS button",
      requiredFulfillmentPercent = 90,
    )
  )
)
```

Full setup (rule-level model config, manual model interface) is in
`references/ai_powered_image_assertion.md`.

## References

- `references/top.md` — overview, why screenshot testing / vs Paparazzi
- `references/try_it_out.md` — quickest path to trying it
- `references/build_setup.md` — plugin/deps matrix, Gradle tasks, output dirs
- `references/how_to_use.md` — full capture API reference
- `references/preview_support.md` — Compose Preview test generation
- `references/ai_powered_image_assertion.md` — AI image assertion
- `references/compose_multiplatform.md` — Compose Multiplatform / iOS
- `references/gradle_properties_options.md` — gradle.properties options
- `references/idea_plugin.md` — IntelliJ/Android Studio plugin
- `references/faq.md` — troubleshooting, running only screenshot tests
