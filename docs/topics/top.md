# Roborazzi

**Make JVM Android Integration Test Visible**

## Roborazzi now supports [Robolectric Native Graphics (RNG)](https://github.com/robolectric/robolectric/releases/tag/robolectric-4.10) and enables screenshot testing.📣

## Why Choose Roborazzi?

### Why is screenshot testing important?

Screenshot testing is key to validate your app's appearance and functionality. It efficiently
detects visual issues and tests the app as users would use it, making it easier to spot problems.
It's quicker than writing many assert statements, ensuring your app looks right and behaves
correctly.

### What are JVM tests and why test with JVM instead of on Android?

JVM tests, also known as local tests, are placed in the test/ directory and are run on a developer's
PC or CI environment. On the other hand, device tests, also known as Instrumentation tests, are
written in the androidTest/ directory and are run on real devices or emulators. Device testing can
result in frequent failures due to the device environment, leading to false negatives. These
failures are often hard to reproduce, making them tough to resolve.

### Paparazzi and Roborazzi: A Comparison

Paparazzi is a great tool for visualizing displays within the JVM. However, it's incompatible with
Robolectric, which also mocks the Android framework.

Roborazzi fills this gap. It integrates with Robolectric, allowing tests to run with Hilt and
interact with components. Essentially, Roborazzi enhances Paparazzi's capabilities, providing a more
efficient and reliable testing process by capturing screenshots with Robolectric.

**Leveraging Roborazzi in Test Architecture: An Example**

<img src="https://github.com/takahirom/roborazzi/assets/1386930/937a96a4-f637-4029-87e1-c1bb94abc8ae" width="320" />


**Integrating Roborazzi into the Architecture: An Example from DroidKaigi 2023 App**

In the DroidKaigi 2023 app, Roborazzi was introduced from the early stages of development as part of the architectural design. This integration allowed the team to verify changes throughout the development process. The specific architectural decisions and how they were implemented can be found [README](https://github.com/DroidKaigi/conference-app-2023#screenshot-testing-with-robolectric-native-graphics-rng-and-roborazzi).
