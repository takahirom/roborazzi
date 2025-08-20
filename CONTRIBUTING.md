# Contributing to Roborazzi

Thank you for your interest in contributing to Roborazzi! We welcome all contributions.

## Prerequisites

Before contributing, ensure you have:
- JDK 17 or later
- Android SDK (API level 35 for latest)
- Git

## Project Structure

* **include-build**: Contains the Gradle plugin and core modules for local development
  - `roborazzi-gradle-plugin`: Gradle plugin that creates record/verify/compare tasks
  - `roborazzi-core`: Core screenshot capture and comparison functionality
* **Other modules**:
  - `roborazzi`: Main library module for Android screenshot testing with Robolectric
  - `roborazzi-compose`: Jetpack Compose support for screenshot testing of Composables
  - `roborazzi-junit-rule`: JUnit rule support for simplified test setup
  - `sample-android`: Sample Android app demonstrating usage and for testing changes

## Testing

All tests should pass before submitting a pull request. Run tests in the following order:

### 1. Screenshot Tests

```bash
# First, record baseline screenshots
./gradlew recordRoborazziDebug

# Then, verify screenshots match the baseline
./gradlew verifyRoborazziDebug

# Or, compare and generate diff images
./gradlew compareRoborazziDebug
```

**Success indicator**: BUILD SUCCESSFUL

### 2. Boxed Tests
Tests that run in an isolated environment to ensure compatibility across different Gradle and AGP versions.
Located in: `sample-android/src/test/java/com/github/takahirom/roborazzi/sample/boxed/`

These tests verify:
- Screenshot file paths are correct
- Image comparison and diff generation work properly
- Changes in screenshots are detected accurately

Run with:
```bash
./gradlew :sample-android:test
```

**Success indicator**: All tests pass

### 3. Integration Tests
Tests for the Gradle plugin functionality with real Gradle projects:

```bash
# Run from project root
cd include-build && ./gradlew roborazzi-gradle-plugin:integrationTest && cd ..

# Or run specific tests
cd include-build && ./gradlew roborazzi-gradle-plugin:integrationTest --tests "*RoborazziGradleProjectTest.record" && cd ..
```

**Success indicator**: Integration tests pass

## Contribution Workflow

1. Fork the repository
2. Create a branch for your changes
3. Make your changes
4. Run relevant tests for your changes
5. Commit your changes with a clear message
6. Push to your fork
7. Create a pull request with a description of your changes

## Troubleshooting

- If tests fail, check the generated screenshots in `build/outputs/roborazzi/`
- For integration test failures, check the test reports in `include-build/roborazzi-gradle-plugin/build/reports/`
- Ensure you're using the correct JDK version (17+)