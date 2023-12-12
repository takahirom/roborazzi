# Try it out

Available on Maven Central.

### Add Robolectric

This is an example of adding Robolectric to your project:
https://github.com/takahirom/roborazzi-usage-examples/compare/b697...5c12

This library is dependent on Robolectric. Please see below to add Robolectric.

https://robolectric.org/getting-started/


### Add Roborazzi

This is an example of adding Roborazzi to your project:
https://github.com/takahirom/roborazzi-usage-examples/commit/3a02

To take screenshots, please use Robolectric 4.10 alpha 1 or later and please
add `@GraphicsMode(GraphicsMode.Mode.NATIVE)` to your test class.

```kotlin
@GraphicsMode(GraphicsMode.Mode.NATIVE)
```
