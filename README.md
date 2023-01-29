# Roborazzi

**Make JVM Android integration test visible**

## Example

```kotlin
@Test
fun roboExample() {
    // launch
    launch(MainActivity::class.java)

    // Take a screenshot of root view
    onView(ViewMatchers.isRoot())
      .roboCapture("build/first_screen.png")

    // Take a screenshot of compose
    onView(ViewMatchers.withId(R.id.compose))
      .roboCapture("build/compose.png")

    // move to next page
    onView(ViewMatchers.withId(R.id.button_first))
      .perform(click())

    // Take a screenshot of root view
    onView(ViewMatchers.isRoot())
      .roboCapture("build/second_screen.png")
}
```

<img width="443" alt="image" src="https://user-images.githubusercontent.com/1386930/215309972-8a2098be-91b7-45a5-82a2-647b89e1f6f8.png">
<img width="486" alt="image" src="https://user-images.githubusercontent.com/1386930/215248859-03a4f66e-3c42-42d8-863a-4cfbc3090b3f.png">

[From DroidKaigi 2022 app](https://github.com/DroidKaigi/conference-app-2022)

<img src=https://user-images.githubusercontent.com/1386930/215334118-ae1de2e0-0748-44f3-a735-4cf03b856767.png width=400 />

## Why

Whenever you test with Robolectric, you feel like you are writing tests blindfolded because you cannot see the layout.  
This tool makes the layout visible and provides the necessary information for debugging.

## Why test with JVM instead of testing on Android?

Because when testing on a device, it is easy for the test to fail due to the device environment, animations, etc., and ultimately, if the test fails, it will not be fixed.

## Why not Paparazzi?

Paparazzi is a great tool to see the actual display in the JVM.  
Paparazzi relies on LayoutLib, Android Studio's layout drawing tool, which is incompatible with Robolectric. This is because they both mock the Android framework.  
Without Robolectric, you can't write tests that actually click on components and run them with Hilt tests.

## Download
Stay tuned.  
I would appreciate a star as I am really trying to find out if this tool has enough impact to be released.
