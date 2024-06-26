# Build setup

Roborazzi is available on maven central.

This plugin simply creates Gradle tasks record, verify, compare and passes the configuration to the
test.

**build.gradle.kts**
<table>
<tr><td>plugins</td><td>buildscript</td></tr>
<tr><td>

Define plugin in root build.gradle.kts

```kotlin
plugins {
  ...
  id("io.github.takahirom.roborazzi") version "[version]" apply false
}
```

Apply plugin in module build.gradle.kts

```kotlin
plugins {
  ...
  id("io.github.takahirom.roborazzi")
}
```

</td><td>

root build.gradle.kts

```kotlin
buildscript {
  dependencies {
    ...
    classpath("io.github.takahirom.roborazzi:roborazzi-gradle-plugin:[version]")
  }
}
```

module build.gradle.kts

```kotlin
plugins {
    ...
    id("io.github.takahirom.roborazzi")
}
```

</td></tr>

</table>

<details>
<summary>build.gradle version</summary>
<table>
<tr><td>plugins</td><td>buildscript</td></tr>
<tr><td>

Define plugin in root build.gradle

```groovy
plugins {
  ...
  id "io.github.takahirom.roborazzi" version "[version]" apply false
}
```

Apply plugin in module build.gradle

```groovy
plugins {
  ...
  id 'io.github.takahirom.roborazzi'
}
```

</td><td>

root build.gradle

```groovy
buildscript {
  dependencies {
    ...
    classpath "io.github.takahirom.roborazzi:roborazzi-gradle-plugin:[version]"
  }
}
```

module build.gradle

```groovy
apply plugin: "io.github.takahirom.roborazzi"
```

</td></tr>

</table>
</details>

Roborazzi can then be activated in multiple ways:
1. By adding a specific property into the module's `gradle.properties` file, to enable the default mode Roborazzi operates in, e.g. verification.
2. By configuring the `roborazzi.taskType` property in the module's build file or an build convention plugin, again, to enable the default mode Roborazzi operates in.
3. By calling one of the specific tasks created by the Roborazzi plugin; this overrides any previously configured defaults.

The following table lists the specific configuration options in detail:

<table>
<tr>
<td> Use Roborazzi task </td> <td> Use default unit test task </td> <td> Description </td>
</tr>
<tr>
<td>

`./gradlew recordRoborazziDebug`


</td><td> 

`./gradlew testDebugUnitTest` after adding `roborazzi.test.record=true` to your gradle.properties file.

or

`./gradlew testDebugUnitTest -Proborazzi.test.record=true`

or

`./gradlew testDebugUnitTest` after adding `roborazzi { taskType.set(RoborazziTaskType.Record) }` to your module's Gradle build file or build convention plugin.

</td><td> 

Record a screenshot  
Default output directory is `build/outputs/roborazzi`  
You can check a report under `build/reports/roborazzi/index.html`

</td>
</tr>
<tr>
<td>

`./gradlew compareRoborazziDebug`

</td><td> 


`./gradlew testDebugUnitTest` after adding `roborazzi.test.compare=true` to your gradle.properties file.

or

`./gradlew testDebugUnitTest -Proborazzi.test.compare=true`

or

`./gradlew testDebugUnitTest` after adding `roborazzi { taskType.set(RoborazziTaskType.Compare) }` to your module's Gradle build file or build convention plugin.

</td><td>

Review changes made to an image. This action will
compare the current image with the saved one, generating a comparison image labeled
as `[original]_compare.png`. It also produces a JSON file containing the diff information, which can
be found under `build/test-results/roborazzi`.

</td>
</tr>
<tr>
<td>

`./gradlew verifyRoborazziDebug`

</td><td> 


`./gradlew testDebugUnitTest` after adding `roborazzi.test.verify=true` to your gradle.properties file.

or

`./gradlew testDebugUnitTest -Proborazzi.test.verify=true`

or

`./gradlew testDebugUnitTest` after adding `roborazzi { taskType.set(RoborazziTaskType.Verify) }` to your module's Gradle build file or build convention plugin.

</td><td>

Validate changes made to an image. If there is any difference between the current image and the
saved one, the test will fail.

</td>
</tr>
<tr>
<td>

`./gradlew verifyAndRecordRoborazziDebug`

</td><td> 


`./gradlew testDebugUnitTest` after adding  `roborazzi.test.verify=true` and `roborazzi.test.record=true` to your gradle.properties file.

or

`./gradlew testDebugUnitTest -Proborazzi.test.verify=true -Proborazzi.test.record=true`

or

`./gradlew testDebugUnitTest` after adding `roborazzi { taskType.set(RoborazziTaskType.VerifyAndRecord) }` to your module's Gradle build file or build convention plugin.

</td><td>

This task will first verify the images and, if differences are detected, it will record a new
baseline.

</td>
</tr>

</table>

The comparison image, saved as `[original]_compare.png`, is shown below:

![image](https://github.com/takahirom/roborazzi/assets/1386930/722090ff-77c4-4a04-a0e3-8ce562ffa6be)

You can check the test report in `build/reports/roborazzi/index.html`

<img width="400" alt="image" src="https://github.com/takahirom/roborazzi/assets/1386930/7834a436-1927-438d-8656-61f583ae3f48" />


This
uses [JetNew from Compose Samples](https://github.com/android/compose-samples/tree/main/JetNews).
You can check the pull request introducing Roborazzi to the
compose-samples [here](https://github.com/takahirom/compose-samples/pull/1/files).

### Add dependencies

| Description     | Dependencies                                                                         |
|-----------------|--------------------------------------------------------------------------------------|
| Core functions  | `testImplementation("io.github.takahirom.roborazzi:roborazzi:[version]")`            |
| Jetpack Compose | `testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:[version]")`    |
| JUnit rules     | `testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:[version]")` |
