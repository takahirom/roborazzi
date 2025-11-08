# Sample Android Multiplatform

Demonstrates Roborazzi usage with the Android Kotlin Multiplatform Library plugin (`com.android.kotlin.multiplatform.library`).

## Requirements

- AGP 8.12.1 or higher (fixes ClassNotFoundException: android.app.Application)
- Roborazzi Gradle Plugin with KMP library support

## Running Tests

```bash
./gradlew :sample-android-multiplatform:recordRoborazziAndroidMain
```

This will run the tests and generate screenshot files in `build/outputs/roborazzi/`.
