### Experimental feature: iOS support

Roborazzi supports Compose Multiplatform iOS. You can use Roborazzi with Compose iOS as follows:

Gradle settings

```kotlin
plugins {
  kotlin("multiplatform")
  id("org.jetbrains.compose")
  id("io.github.takahirom.roborazzi")
}

kotlin {
  sourceSets {
    ...
    val appleTest by getting {
      dependencies {
        implementation(project("io.github.takahirom.roborazzi:roborazzi-compose-ios:[1.12.0 or higher]"))
        implementation(kotlin("test"))
      }
    }
  ...

```

Test with Roborazzi

```kotlin
class IosTest {
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun test() {
    runComposeUiTest {
      setContent {
        MaterialTheme {
          Column {
            Button(
              modifier = Modifier.alpha(alpha),
              onClick = { }) {
              Text("Hello World")
            }
            Box(
              modifier = Modifier
                .background(Color.Red.copy(alpha = alpha), MaterialTheme.shapes.small)
                .size(100.dp),
            )
          }
        }
      }
      onRoot().captureRoboImage(this, filePath = "ios.png")
      onNodeWithText("Hello World").captureRoboImage(
        this,
        filePath = "ios_button.png"
      )
    }
  }
}
```

Then, you can run the Gradle tasks for iOS Support, just like you do for Android Support.

```
./gradlew recordRoborazzi[SourceSet]
```

```
./gradlew recordRoborazziIosSimulatorArm64
./gradlew compareRoborazziIosSimulatorArm64
./gradlew verifyRoborazziIosSimulatorArm64
...
```

The currently implemented features are as follows:

| Feature | status |
|---|---|
| Record | supported |
| Compare | supported |
| Verify | supported |
| Report | supported |
| Dropbox/Differ comparison | 🆖  |
| dump | 🆖  |
| resizing image | 🆖  |
| context data | 🆖  |
| custom reporter | 🆖  |
| RoborazziRecordFilePathStrategy  | 🆖  |
| ComparisonStyle  | 🆖  |
| resultValidator  | 🆖  |
| resultValidator  | 🆖  |
| applyDeviceCrop | 🆖 |
| pixelBitConfig | 🆖 |


We are migrating JVM implementation to Multiplatform implementation. So, some features are not supported yet.
We are looking for contributors to help us implement these features.

### Experimental feature: Compose Desktop support

Roborazzi supports Compose Desktop. You can use Roborazzi with Compose Desktop as follows:

Gradle settings

```kotlin
plugins {
  kotlin("multiplatform")
  id("org.jetbrains.compose")
  id("io.github.takahirom.roborazzi")
}

kotlin {
  // You can use your source set name
  jvm("desktop")
  sourceSets {
    ...
    val desktopTest by getting {
      dependencies {
        implementation(project("io.github.takahirom.roborazzi:roborazzi-compose-desktop:[1.6.0-alpha-2 or higher]"))
        implementation(kotlin("test"))
      }
    }
    ...

// Roborazzi Desktop support uses Context Receivers
    tasks.withType<KotlinCompile>().configureEach {
      kotlinOptions {
        freeCompilerArgs += "-Xcontext-receivers"
      }
    }
```

Test target Composable function

```kotlin
@Composable
fun App() {
  var text by remember { mutableStateOf("Hello, World!") }

  MaterialTheme {
    Button(
      modifier = Modifier.testTag("button"),
      onClick = {
        text = "Hello, Desktop!"
      }) {
      Text(
        style = MaterialTheme.typography.h2,
        text = text
      )
    }
  }
}
```

Test with Roborazzi

```kotlin
class MainKmpTest {
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun test() = runDesktopComposeUiTest {
    setContent {
      App()
    }
    val roborazziOptions = RoborazziOptions(
      recordOptions = RoborazziOptions.RecordOptions(
        resizeScale = 0.5
      ),
      compareOptions = RoborazziOptions.CompareOptions(
        changeThreshold = 0F
      )
    )
    onRoot().captureRoboImage(roborazziOptions = roborazziOptions)

    onNodeWithTag("button").performClick()

    onRoot().captureRoboImage(roborazziOptions = roborazziOptions)
  }
}
```

Then, you can run the Gradle tasks for Desktop Support, just like you do for Android Support.

```
./gradlew recordRoborazzi[SourceSet]
```

```
./gradlew recordRoborazziDesktop
./gradlew compareRoborazziDesktop
./gradlew verifyRoborazziDesktop
...
```

If you use the Kotlin JVM plugin, the task will be `recordRoborazzi**Jvm**`.

The sample image

![MainJvmTest test](https://github.com/takahirom/roborazzi/assets/1386930/41287c29-26ae-4539-b387-de570ae3f2b3)
![MainJvmTest test_2](https://github.com/takahirom/roborazzi/assets/1386930/2edc828c-6fd8-4a9a-8f3d-b0e7baa85f0d)

