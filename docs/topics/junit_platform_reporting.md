# Attach screenshots to Gradle test reports (JUnit Platform)

> **Experimental**\
> The `roborazzi-junit-platform-reporting` module is new and its setup may change.

The `roborazzi-junit-platform-reporting` module attaches Roborazzi's captured
images (golden / actual / compare) to the **standard Gradle `Test` task report** —
the HTML report and the JUnit XML — so you can open a failing test and see its
screenshots inline. It works by wrapping JUnit Vintage
in a custom engine that publishes each captured image through JUnit Platform's
`fileEntryPublished()` API, which Gradle 9.4+ renders as test attachments.

## Requirements

* **Gradle 9.4+.** Attachment rendering doesn't exist below 9.4, so the plugin
  **fails the build** there (adding the module is an explicit request for
  attachments). If you cannot upgrade yet, downgrade the error to a warning with
  `roborazzi.suppress=junitPlatformReporting.oldGradle` — tests then run normally,
  just without attachments.
* **JUnit4 / Robolectric tests** (the engine wraps JUnit Vintage). This is the
  usual Roborazzi setup on Android/Robolectric.

## Setup

```kotlin
dependencies {
  testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-platform-reporting:[version]")
  // Registers the engine with the Gradle test worker.
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform {
    // Required. See the note below.
    excludeEngines("junit-vintage")
  }
}
```

> **Why exclude `junit-vintage`?**\
> The module's `roborazzi-vintage` engine runs your JUnit4 tests itself; leaving
> the stock engine enabled would run every test twice, so the plugin fails the
> build. `includeEngines("roborazzi-vintage")` works as an alternative.

## What you get

* **HTML report** (`build/reports/tests/<task>/`): a per-test *attachments*
  section with the captured images embedded inline.
* **JUnit XML** (`build/test-results/<task>/`): an `[[ATTACHMENT|<path>]]` marker
  per image. CI systems that parse JUnit XML — such as **GitLab CI** and **Azure
  DevOps** — read these markers and show the screenshots on the test result.

Attachments reference the original files under `build/outputs/roborazzi`; Gradle
does not copy them into the report directory.

## Troubleshooting

The plugin detects the setup mistakes below and **fails the build** with a
self-contained message: problem, impact, copy-pasteable fix, and a stable **id**.
If a check doesn't apply to your situation, list its id (comma-separated) in the
Roborazzi-wide `roborazzi.suppress` property to downgrade the error to a warning:

```properties
roborazzi.suppress=junitPlatformReporting.oldGradle,junitPlatformReporting.notJUnitPlatform
```

| Symptom | Id | Severity | Cause / fix |
|---------|----|----------|-------------|
| No attachments in the report | `junitPlatformReporting.oldGradle` | **Build error** | Gradle must be **9.4 or newer**; below that, attachment rendering does not exist. |
| Nothing is attached at all | `junitPlatformReporting.notJUnitPlatform` | **Build error** | The dependency is present but the `Test` task is not on the JUnit Platform. Add the `useJUnitPlatform { ... }` block from [Setup](#setup). |
| Nothing is attached, but the task **is** on the JUnit Platform | `junitPlatformReporting.engineNotSelected` | **Build error** | Engine filters leave `roborazzi-vintage` out (excluded, or missing from `includeEngines(...)`). Put it back in the selected set. |
| Every test runs twice | `junitPlatformReporting.doubleExecution` | **Build error** | The stock `junit-vintage` engine still runs. Add `excludeEngines("junit-vintage")` inside `useJUnitPlatform { ... }` (see Setup), or restrict to `includeEngines("roborazzi-vintage")`. |

## Limitations

* Concurrent in-JVM test execution is **not supported** (the module relies on
  Gradle running tests sequentially within each forked test worker).
* Vintage parallel execution (`junit.vintage.execution.parallel.enabled=true`) is
  not supported for attachments: when enabled, the module disables image
  publishing and logs a warning, because a single shared per-test sink would
  interleave across parallel tests.
* The module is experimental.
