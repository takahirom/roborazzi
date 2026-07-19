# Attach screenshots to Gradle test reports (JUnit Platform)

> **Experimental**  
> The `roborazzi-junit-platform-reporting` module is new and its setup may change.

The `roborazzi-junit-platform-reporting` module attaches Roborazzi's captured
images (golden / actual / compare) to the **standard Gradle `Test` task report** —
the HTML report and the JUnit XML — so you can open a failing test and see its
screenshots inline, with no extra upload step. It works by wrapping JUnit Vintage
in a custom engine that publishes each captured image through JUnit Platform's
`fileEntryPublished()` API, which Gradle 9.4+ renders as test attachments.

## Requirements

* **Gradle 9.4+.** On older Gradle the tests still run and pass normally, but no
  attachments appear — the platform's file-publishing call is a silent no-op.
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
    // Required. See the warning below.
    excludeEngines("junit-vintage")
  }
}
```

> **`excludeEngines("junit-vintage")` is mandatory**  
> This module ships a `roborazzi-vintage` engine that runs your JUnit4 tests
> itself. If you leave the stock `junit-vintage` engine enabled, **both** engines
> discover the same tests and every test runs twice, writing a second, suffixed
> golden image (e.g. `MyTest_2.png`). Because no valid configuration wants both
> engines, the Roborazzi Gradle plugin **fails the build** with an error that
> spells out the fix. Restricting execution to `includeEngines("roborazzi-vintage")`
> instead of excluding the stock engine also works and is not flagged. If you must
> keep the stock engine running for some reason, downgrade the error to a warning
> with `roborazzi.suppress=junitPlatformReporting.doubleExecution` in
> `gradle.properties`.

## What you get

* **HTML report** (`build/reports/tests/<task>/`): a per-test *attachments*
  section with the captured images embedded inline.
* **JUnit XML** (`build/test-results/<task>/`): an `[[ATTACHMENT|<path>]]` marker
  per image. CI systems that parse JUnit XML — such as **GitLab CI** and **Azure
  DevOps** — read these markers and show the screenshots on the test result.

Gradle 9.4+ does not copy the images into the report directory; the attachments
reference the original files under `build/outputs/roborazzi`, so the report stays
in sync with what Roborazzi wrote.

## Troubleshooting

The Roborazzi Gradle plugin detects the common setup mistakes below and reports each
with a message prefixed `Roborazzi JUnit Platform reporting:` that states the problem, its
impact, a copy-pasteable fix, and the diagnostic's stable **id**. If you see one, follow it —
the message already contains the exact change to make.

Each diagnostic can be suppressed by listing its id (comma-separated) in the Roborazzi-wide
`roborazzi.suppress` property. Ids are namespaced by feature. Suppressing a **warning**
silences it entirely; suppressing the **error** (`junitPlatformReporting.doubleExecution`)
downgrades it to a warning rather than silencing it. For example, in `gradle.properties`:

```properties
roborazzi.suppress=junitPlatformReporting.oldGradle,junitPlatformReporting.notJUnitPlatform
```

| Symptom | Id | Severity | Cause / fix |
|---------|----|----------|-------------|
| No attachments in the report | `junitPlatformReporting.oldGradle` | Warning | Confirm Gradle is **9.4 or newer**. Below that, attachment rendering does not exist and the feature is a no-op. |
| Nothing is attached at all | `junitPlatformReporting.notJUnitPlatform` | Warning | The dependency is present but the `Test` task is not on the JUnit Platform. Add the `useJUnitPlatform { ... }` block from [Setup](#setup). |
| Every test runs twice | `junitPlatformReporting.doubleExecution` | **Build error** | The stock `junit-vintage` engine still runs. Add `excludeEngines("junit-vintage")` inside `useJUnitPlatform { ... }` (see Setup), or restrict to `includeEngines("roborazzi-vintage")`. |

## Limitations

* Concurrent in-JVM test execution is **not supported** (the module relies on
  Gradle running tests sequentially within each forked test worker).
* The module is experimental.
