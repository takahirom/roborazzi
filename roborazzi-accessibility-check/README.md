# Roborazzi Acessibility Checks

## How to use

### Add dependencies

| Description         | Dependencies                                                                                  |
|---------------------|-----------------------------------------------------------------------------------------------|
| Accessibility Check | `testImplementation("io.github.takahirom.roborazzi:roborazzi-accessibility-check:[version]")` |

### Configure in Junit Rule

```kotlin
  @get:Rule
  val roborazziRule = RoborazziRule(
    composeRule = composeTestRule,
    captureRoot = composeTestRule.onRoot(),
    options = Options(
      accessibilityChecks = ValidateAfterTest(
        checker = ATFAccessibilityChecker(
          preset = AccessibilityCheckPreset.LATEST,
          suppressions = matchesElements(withTestTag("suppress"))
        ),
        failureLevel = CheckLevel.Warning,
      )
    )
  )
```

### Checking Log output

Particularly with `failureLevel = CheckLevel.LogOnly` the output log of each test will including a11y checks.

```text
Error: [AccessibilityViewCheckResult check=AccessibilityHierarchyCheckResult ERROR SpeakableTextPresentCheck 4 [ViewHierarchyElement class=android.view.View bounds=Rect(474, 1074 - 606, 1206)] null num_answers:0 view=null]
Warning: [AccessibilityViewCheckResult check=AccessibilityHierarchyCheckResult WARNING TextContrastCheck 11 [ViewHierarchyElement class=android.widget.TextView text=Something hard to read bounds=Rect(403, 1002 - 678, 1092)] {KEY_BACKGROUND_COLOR=-12303292, KEY_CONTRAST_RATIO=1.02, KEY_FOREGROUND_COLOR=-12369085, KEY_REQUIRED_CONTRAST_RATIO=3.0} num_answers:0 view=null]
```

### LICENSE

```
Copyright 2023 takahirom
Copyright 2019 Square, Inc.
Copyright The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
