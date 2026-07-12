---
name: roborazzi
description: Use when working with Roborazzi screenshot tests on Android/JVM — setting up the Roborazzi Gradle plugin, recording/comparing/verifying screenshots (record, compare, verify tasks), writing tests with captureRoboImage or RoborazziRule, Compose Preview screenshot testing (ComposablePreviewScanner), Compose Multiplatform (iOS/desktop) screenshots, AI-powered image assertions, Roborazzi Gradle properties (roborazzi.*), or troubleshooting Roborazzi/Robolectric screenshot test failures.
---

# Roborazzi

Roborazzi is a screenshot testing library for Android that runs on the JVM using
[Robolectric Native Graphics](https://github.com/robolectric/robolectric/releases/tag/robolectric-4.10),
so screenshot tests run as JVM unit tests without a device or emulator. It works with plain
Android views, Jetpack Compose (including Compose Previews), and Compose Multiplatform.

The typical workflow: record baseline images with `./gradlew recordRoborazziDebug`, then compare or
verify against them with `compareRoborazziDebug` / `verifyRoborazziDebug` (task names vary by
variant; see the build setup reference).

## Reference index

The full documentation is available in the `references/` directory of this skill. Read the file
that matches the task at hand:

| File | Read when |
|---|---|
| [references/top.md](references/top.md) | You need an overview of what Roborazzi is and why to use it over device tests or Paparazzi. |
| [references/try_it_out.md](references/try_it_out.md) | You want a minimal quick start or a sample project to try Roborazzi. |
| [references/build_setup.md](references/build_setup.md) | Setting up the Gradle plugin and dependencies, or running/understanding the `record`/`compare`/`verify` Gradle tasks and their outputs. |
| [references/how_to_use.md](references/how_to_use.md) | Writing tests: `captureRoboImage` for views/Compose/Espresso, `RoborazziRule` and its options, output paths and file naming, capturing GIFs/videos, RoborazziOptions (thresholds, image comparators, dump mode). |
| [references/preview_support.md](references/preview_support.md) | Generating screenshot tests from `@Preview` composables with ComposablePreviewScanner, including setup and customization of preview tests. |
| [references/compose_multiplatform.md](references/compose_multiplatform.md) | Screenshot testing Compose Multiplatform targets (iOS, desktop/JVM), including feature support per platform. |
| [references/ai_powered_image_assertion.md](references/ai_powered_image_assertion.md) | Asserting screenshot content with AI models (OpenAI, Gemini) via roborazzi-ai modules. |
| [references/gradle_properties_options.md](references/gradle_properties_options.md) | Configuring `roborazzi.*` Gradle properties: task behavior, output directory, file-name strategy, comparison report settings. |
| [references/idea_plugin.md](references/idea_plugin.md) | Using the Roborazzi IntelliJ IDEA / Android Studio plugin to view screenshots in the IDE. |
| [references/faq.md](references/faq.md) | Troubleshooting: common errors, CI setup questions, Robolectric configuration issues, and other frequently asked questions. |

Files under `references/` are generated verbatim from the repository's `docs/topics/` directory by
the `generateSkill` Gradle task — do not edit them here.
