# AGENTS.md

Guidance for AI coding agents working on Roborazzi. For build setup, project structure, and how to run tests, read [CONTRIBUTING.md](CONTRIBUTING.md) first.

## Adding or changing public API

Roborazzi is a library, so anything public is hard to change later. That includes Gradle properties, the Gradle DSL, and IDs shown in messages, not only Kotlin API. Before adding public API, check each point below. In the PR description, say how the change handles each one.

1. **Fits in with its neighbours.** Look at the properties, parameters, and functions next to where you are adding it. Check that its name, parameter order, and default look like theirs and nothing stands out. When the same feature also appears in the Gradle DSL, `gradle.properties`, Kotlin API, or docs, use the same name in every place. Name it after what users see there, not after where the code runs (plugin, library, test JVM), which may change.
2. **Has a home to grow in.** Put a new setting in the options class for its area, not directly on `RoborazziOptions`. If there is no such class, consider adding one: a blur setting, for example, would go in a rendering options class, so the next rendering setting has a place too.
3. **Can change without breaking callers.** Don't use a `data class` for new public types: adding a property changes its constructor and `copy()`, which breaks binary compatibility. If a type needs `equals`/`hashCode`/`toString`, annotate a plain class with `@Poko` (Roborazzi's own marker in `roborazzi-core`). If new kinds are likely, prefer an `interface` over an `enum class`.
4. **Hard to misuse.** Avoid adjacent parameters of the same type that still compile when swapped. Validate in `init` and fail early with a message that includes the bad value.
5. **Marked `@ExperimentalRoborazziApi` when in doubt.** This is the norm for new features here.

Then run `./gradlew apiDump` (and `cd include-build && ./gradlew apiDump`), commit the `api/*.api` files, and read the diff. Every removed or changed line is a potential binary break. Restore it with a `@Deprecated(level = DeprecationLevel.HIDDEN)` bridge, or call it out in the PR.

## Global state

Don't add mutable state to an `object`, a companion object, or a top-level `var`. Global state is shared by every test in the JVM, so it is hard to tell who set it, when it is reset, and what leaks into the next test. An `object` that holds only constants and functions is fine.

When one part of Roborazzi needs to tell another about the current test, pass it as a parameter or a return value. Use `RoborazziContext` for settings that a JUnit rule overrides per test. If there is no other way, such as when the value has to cross class loaders, explain why in the PR and reset the state at the end of each test.

## Docs

User-facing docs live in `docs/topics/*.md`. `README.md` and `skills/` are generated from them. After editing docs, run `./gradlew generateReadme generateSkill` and commit the generated files too; CI checks that they are up to date.
