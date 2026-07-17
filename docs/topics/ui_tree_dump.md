# UI tree dump (JSON)

While [Dump mode](https://takahirom.github.io/roborazzi/how-to-use.html#dump-mode)
renders the UI tree *into an image* for humans, **UI tree dump** writes a
machine-readable JSON **sidecar** next to the screenshot it describes, for tools
and AI agents to read. When enabled, capturing `MyTest.png` also writes
`MyTest.uitree.json` (the Compose semantics + View hierarchy of the current run)
and, by default, an annotated `MyTest.annotated.png` (see
[Annotated image](#annotated-image-set-of-mark) below).

The sidecar is written on record and compare/verify, next to the image the task
writes: `MyTest.uitree.json` on record, and `MyTest_actual.uitree.json` in the
compare output directory on compare/verify (an unchanged verify still writes it).

The sidecar is **informational only**: it never participates in image diffing and
never fails verification. Bitmap-based `captureRoboImage(Bitmap...)` captures
(which have no component tree) do not produce one.

Supported on Android/Robolectric, Compose Desktop, and Compose iOS, with identical
output.

## Enabling it

Via the Gradle property (no code change):

```shell
./gradlew recordRoborazziDebug -Proborazzi.dumpUiTree=true
```

Or per capture via `RoborazziOptions`:

```kotlin
onView(ViewMatchers.isRoot())
  .captureRoboImage(
    roborazziOptions = RoborazziOptions(
      uiTreeDumpOptions = UiTreeDumpOptions()
    )
  )
```

## Format

```json
{ "schemaVersion": 1, "capture": { "imageWidth": 220, "imageHeight": 100, "scale": 1.0 }, "root":
 { "type": "view", "className": "androidx.compose.ui.platform.ComposeView", "bounds": [0, 0, 220, 100], "children": [
  { "n": 1, "type": "compose", "testTag": "login_button", "bounds": [16, 24, 204, 72], "properties": { "Role": "Button", "Text": "Login" }, "actions": ["OnClick"], "flags": ["MergeDescendants"] },
  { "n": 2, "type": "compose", "bounds": [16, 80, 204, 96], "properties": { "Text": "Forgot password?" } } ] }
}
```

The format is deliberately grep-first: one node per line with all of its scalar
attributes, so a single `grep` finds a node and its coordinates.

* `bounds` is `[left, top, right, bottom]` in **RAW (unscaled) window pixel**
  coordinates; the root `capture` object carries the output `imageWidth`/
  `imageHeight` and `scale`. Map to image pixels with
  `image = (raw − root.bounds origin) × capture.scale` — the root origin is
  `0, 0` for full-screen captures.
* Empty/default fields are omitted (no empty maps/lists, no `visibility` when the
  node is visible).
* `n` is a sequential (1-based, pre-order) number on *annotatable* nodes only —
  visible nodes with a test tag, a `Text`/`ContentDescription`, or an action; a
  numbered `MergeDescendants` node's descendants are skipped.
* Output is **deterministic**: the same UI produces byte-identical JSON (no
  timestamps, no hashes).

## Annotated image (Set-of-Mark)

"Set-of-Mark" refers to a prompting technique for vision-language models:
overlaying numbered marks on image regions lets a model refer to a region
unambiguously by its number ([Yang et al., 2023](https://arxiv.org/abs/2310.11441)).

<img width="440" alt="Annotated Set-of-Mark image" src="https://github.com/user-attachments/assets/208593c3-cd2b-4c0a-ae5b-e7cf6cd0260e" />

Written by default next to the screenshot — a copy of the output with every
numbered node drawn as a labelled box — under the same naming as the sidecar:
`MyTest.annotated.png` on record, `MyTest_actual.annotated.png` on compare/verify
(an unchanged verify annotates the identical golden).

Each box's number is the same `n` as in the JSON, so an agent can point at
"element #3" and look up `"n": 3` for its exact bounds, properties, and actions.
The annotated image is a **display artifact of the current run only** — never
compared, never failing a capture, never treated as a golden. Opt out with
`UiTreeDumpOptions(annotateImage = false)`.

## Primary use case: letting an AI agent prove its UI fixes

AI agents are bad at judging small layout changes from screenshots — ask one to
"add margin above the button" and it will often claim "fixed" when nothing moved.
The deterministic UI tree gives it exact coordinates to check instead, so the
agent can read the button's bounds before and after and prove the change actually
landed.

1. Record and read the node line:

   ```shell
   ./gradlew recordRoborazziDebug -Proborazzi.dumpUiTree=true
   grep login_button build/outputs/roborazzi/MyTest.uitree.json
   #  { "n": 1, "testTag": "login_button", "bounds": [16, 24, 204, 72], ... }
   ```

   `bounds` is `[left, top, right, bottom]`, so the top edge is at `24`.

2. Make the change, keeping that `before` value in context.

3. Re-record (it overwrites the sidecar in place) and grep the same line:

   ```text
   #  { "n": 1, "testTag": "login_button", "bounds": [16, 32, 204, 80], ... }
   ```

   Top moved `24 → 32` — the button provably sits 8px lower. (To prove a *gap*,
   also compare the sibling above: its `bottom` vs this `top`.) Because the output
   is deterministic, any change in the numbers is a real layout change, not noise.
