# AGENTS.md

Guidance for AI coding agents working on Roborazzi. For build setup, project structure, and how to run tests, read [CONTRIBUTING.md](CONTRIBUTING.md) first.

## Adding or changing public API

Roborazzi is a library. Users compile against it, so anything public is hard to change later. Before you add a public class, function, parameter, or property, go through this checklist.

### 1. Match the existing API

- Naming: options types are `XxxOptions` (`RoborazziOptions.CompareOptions`, `RoboVideoOptions`, `UiTreeDumpOptions`), and capture entry points are extension functions (`captureRoboImage`, `recordRoboVideo`). Look for a similar API and follow it.
- Parameter order follows the existing entry points:
  1. required context with no default (for example `composeRule`)
  2. `filePath: String` with a generated default (`DefaultFileNameGenerator.generateFilePath(...)`), plus a `file: File` overload
  3. feature-specific options (for example `videoOptions: RoboVideoOptions = RoboVideoOptions()`)
  4. `roborazziOptions: RoborazziOptions = provideRoborazziContext().options`
  5. a trailing lambda, if any
- Defaults go on the parameter itself (`= XxxOptions()`), not in a nullable parameter that gets resolved later. Use a nullable type only when `null` has a meaning of its own. For example, `uiTreeDumpOptions = null` means "feature disabled".

### 2. Plan for how the API will change

Ask what is likely to grow and choose a shape that can grow without breaking binary compatibility.

- **New kinds of something** → prefer a type hierarchy over an `enum class`, because an enum can't carry per-case parameters.
  - `sealed interface`: each case can have its own parameters, and the set stays closed. Users can match on it exhaustively, so adding a case breaks their exhaustive `when`, just as it would with an enum. See `CompareOptions.ComparisonStyle` (`Grid(...)` / `Simple`).
  - Open `interface`: both users and future versions can add cases without breaking anyone. See `RoborazziOptions.CaptureType` and `CaptureResultReporter`.
  - If you expect new cases, choose an open `interface`, or a sealed one marked `@ExperimentalRoborazziApi`.
- **New properties** → don't use a `data class` for new public types. Adding a constructor property changes the signatures of the generated `copy()` and adds a new `componentN()`, which breaks callers compiled against the old version. `RoborazziOptions` shows the cost: it now carries `@Deprecated(level = HIDDEN)` constructor and `copy()` bridges only to restore the pre-1.69.0 signatures. Many existing public types are data classes. Don't convert them, because that is itself a breaking change, and don't copy them for new types.
- **New parameters on a function** → adding a parameter with a default still changes the JVM signature. Either keep the old overload as a `@Deprecated(level = DeprecationLevel.HIDDEN)` bridge or put the new setting in an options class.

### 3. Value types: plain class vs. equals/toString

- If the type needs `equals`/`hashCode`/`toString` (compared in tests, used as a map key, shown in messages), write them explicitly, and keep that in mind when adding properties. (We plan to adopt Poko for this. Until then, write them by hand.)
- Otherwise use a plain `class` (for example `UiTreeDumpOptions`).

### 4. Make mistakes hard to make and easy to notice

- Don't put several parameters of the same type next to each other (`Int, Int`, `String, String`) where swapping them still compiles. Use distinct types, or require named arguments in the docs and examples.
- Validate in `init` and fail early with a message that says what was wrong and what value was given. Example from `RoboVideoOptions`: `require(fps in 1..100) { "fps must be in 1..100 but was $fps" }`.
- When a call is invalid because of missing configuration, say how to fix it (see the `checkNotNull` message in `RoborazziOptions.addedAiAssertions`).
- Don't let invalid input quietly fall back to a default.

### 5. When in doubt, mark it `@ExperimentalRoborazziApi`

- New public API whose shape you are not confident about should be annotated with `@ExperimentalRoborazziApi` (defined in `roborazzi-core`, `RequiresOptIn(level = WARNING)`). This is the norm for new features here (`RoboVideoOptions`, `recordRoboVideo`, `UiTreeDumpOptions`, `ComparisonStyle`, `CaptureResultReporter`).
- For a new property on an existing stable class, annotate just that property (`@property:ExperimentalRoborazziApi`, as on `RoborazziOptions.uiTreeDumpOptions`) and keep a stable constructor that doesn't expose it (see the `// Stable parameters` constructors).
- Experimental API still shows up in the `.api` dumps. The annotation tells users it may change. It doesn't exempt the change from review.

### 6. Check the API dump

binary-compatibility-validator runs `apiCheck` in CI for both builds. After changing public API:

```bash
./gradlew apiDump
cd include-build && ./gradlew apiDump && cd ..
```

Commit the updated `api/*.api` files, then read the diff yourself. A passing `apiCheck` only means the dump matches the code. It doesn't mean the change is compatible. Every removed or changed line in a `.api` file is a potential binary break: restore it with a hidden bridge, or call it out explicitly in the PR description.

### 7. Document it

User-facing docs live in `docs/topics/*.md`. `README.md` and `skills/` are generated from them. After editing docs, run `./gradlew generateReadme generateSkill` and commit the generated files as well; CI checks that they are up to date.
