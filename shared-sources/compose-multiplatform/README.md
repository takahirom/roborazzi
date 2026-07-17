# shared-sources/compose-multiplatform

Shared Kotlin sources compiled into **both** `roborazzi-compose-desktop` and
`roborazzi-compose-ios`.

- This is intentionally **NOT a Gradle module**: it is not listed in
  `settings.gradle` and is never published.
- Its sources are compiled directly into each consuming module via
  `kotlin.srcDir("$rootDir/shared-sources/compose-multiplatform/src/commonMain/kotlin")`
  in that module's `build.gradle`. There is a single source copy for the two
  Compose targets (rather than duplicating the logic per platform).
- All declarations here must stay `internal`. They are compiled separately into
  each module, so nothing here contributes to any module's public API.
- Only **multiplatform (JetBrains) compose-ui APIs** may be used. A
  desktop-only (JVM) API would break the iOS (Kotlin/Native) compile, and vice
  versa.
- **Android does not consume these sources.** Android uses the `androidx`
  Compose artifacts (not the JetBrains multiplatform ones), so
  `roborazzi-core`'s `androidMain` keeps its own equivalent extraction; this
  directory serves only the desktop and iOS Compose modules.
