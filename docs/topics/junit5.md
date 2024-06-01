# JUnit 5 support

Roborazzi supports the execution of screenshot tests with JUnit 5, 
powered by the combined forces of the [Android JUnit 5](https://github.com/mannodermaus/android-junit5) plugin 
and the [JUnit 5 Robolectric Extension](https://github.com/apter-tech/junit5-robolectric-extension).

### Setup

To get started with Roborazzi for JUnit 5, make sure to set up your project for the new testing framework first 
and add the dependencies for JUnit Jupiter and the Robolectric extension to your project (check the readme files 
of either project linked above to find the latest version). Then, add the `roborazzi-junit5` dependency 
next to the existing Roborazzi dependency. The complete build script setup looks something like this:

```kotlin
// Root moduls's build.gradle.kts:
plugins {
  id("io.github.takahirom.roborazzi") version "$roborazziVersion" apply false
  id("de.mannodermaus.android-junit5") version "$androidJUnit5Version" apply false
  id("tech.apter.junit5.jupiter.robolectric-extension-gradle-plugin") version "$robolectricExtensionVersion" apply false
}
```

```kotlin
// App module's build.gradle.kts:
plugins {
  id("de.mannodermaus.android-junit5")
  id("tech.apter.junit5.jupiter.robolectric-extension-gradle-plugin")
}

dependencies {
  testImplementation("org.robolectric:robolectric:$robolectricVersion")
  testImplementation("io.github.takahirom.roborazzi:$roborazziVersion")
  testImplementation("io.github.takahirom.roborazzi-junit5:$roborazziVersion")
  
  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
}
```

You are now ready to write a JUnit 5 screenshot test with Roborazzi.

### Write a test

JUnit 5 does not have a concept of `@Rule`. Instead, extension points to the test framework have to be registered, 
and Roborazzi is no exception to this. Add the `@ExtendWith` annotation to the test class and insert both the 
extension for Robolectric (from the third-party dependency defined above) and Roborazzi (from `roborazzi-junit5`). 
If you also have JUnit 4 on the classpath, make sure to import the correct `@Test` annotation (`org.junit.jupiter.api.Test` 
instead of `org.junit.Test`):

```kotlin
// MyTest.kt:
@ExtendWith(RobolectricExtension::class, RoborazziExtension::class)
class MyTest {
  @Test
  fun test() {
    // Your ordinary Roborazzi setup here, for example:
    ActivityScenario.launch(MainActivity::class.java)
    onView(isRoot()).captureRoboImage()
  }
}
```

### Automatic Extension Registration

You may tell JUnit 5 to automatically attach the `RoborazziExtension` to applicable test classes, 
minimizing the redundancy of having to add `@ExtendWith(RoborazziExtension::class)` to every class. 
This is done via a process called [Automatic Extension Registration](https://junit.org/junit5/docs/current/user-guide/#extensions-registration-automatic) and must be enabled in the build file. 
Be aware that you still need `ExtendWith` for the `RobolectricExtension`, since it is not eligible for 
automatic registration. Think of it as the JUnit 5 equivalent of `@RunWith(RobolectricTestRunner::class)`:

```kotlin
// App module's build.gradle.kts:
junitPlatform {
  configurationParameter(
    "junit.jupiter.extensions.autodetection.enabled",
    "true"
  )
}
```

```kotlin
// MyTest.kt:
@ExtendWith(RobolectricExtension::class)
class MyTest {
  // ...
}
```
