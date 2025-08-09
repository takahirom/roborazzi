# Make AndroidComposePreviewTester Customizable

## Problem
Issue #703 requests the ability to set `changeThreshold` for generated Compose preview tests. Currently, `AndroidComposePreviewTester` doesn't allow customization of the capture behavior.

## Proposed Implementation
We propose a composition-based solution using a `Capturer` interface:

```kotlin
@ExperimentalRoborazziApi
class AndroidComposePreviewTester(
  private val capturer: Capturer = DefaultCapturer()
) : ComposePreviewTester<AndroidPreviewJUnit4TestParameter> {
  
  fun interface Capturer {
    fun capture(parameter: CaptureParameter)
  }
  
  data class CaptureParameter(
    val preview: ComposablePreview<AndroidPreviewInfo>,
    val filePath: String,
    val roborazziComposeOptions: RoborazziComposeOptions,
    val roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  )
  
  class DefaultCapturer : Capturer {
    override fun capture(parameter: CaptureParameter) {
      parameter.preview.captureRoboImage(
        filePath = parameter.filePath,
        roborazziOptions = parameter.roborazziOptions,
        roborazziComposeOptions = parameter.roborazziComposeOptions
      )
    }
  }
}
```

## Usage Examples

### 1. Lambda-based customization (Simple)
```kotlin
class CustomTester : AndroidComposePreviewTester(
  capturer = { parameter ->
    val customOptions = parameter.roborazziOptions.copy(
      compareOptions = parameter.roborazziOptions.compareOptions.copy(
        imageComparator = SimpleImageComparator(maxDistance = 0.01f)
      )
    )
    parameter.preview.captureRoboImage(
      filePath = parameter.filePath,
      roborazziOptions = customOptions,
      roborazziComposeOptions = parameter.roborazziComposeOptions
    )
  }
)
```

### 2. Class-based implementation (Reusable)
```kotlin
class ChangeThresholdCapturer(private val threshold: Float) : Capturer {
  override fun capture(parameter: CaptureParameter) {
    val customOptions = parameter.roborazziOptions.copy(
      compareOptions = parameter.roborazziOptions.compareOptions.copy(
        imageComparator = SimpleImageComparator(maxDistance = threshold)
      )
    )
    parameter.preview.captureRoboImage(
      filePath = parameter.filePath,
      roborazziOptions = customOptions,
      roborazziComposeOptions = parameter.roborazziComposeOptions
    )
  }
}

class CustomTester : AndroidComposePreviewTester(
  capturer = ChangeThresholdCapturer(0.01f)
)
```

### 3. Decorating default behavior
```kotlin
class CustomCapturer(
  private val defaultCapturer: Capturer = AndroidComposePreviewTester.DefaultCapturer()
) : Capturer {
  override fun capture(parameter: CaptureParameter) {
    // Pre-processing
    println("Capturing: ${parameter.filePath}")
    
    // Call default implementation
    defaultCapturer.capture(parameter)
    
    // Post-processing
    println("Captured: ${parameter.filePath}")
  }
}

class CustomTester : AndroidComposePreviewTester(
  capturer = CustomCapturer()
)
```

## Design Decisions

### Why Composition over Inheritance?
Based on Effective Java principles, we chose composition to avoid:
- **Fragile base class problem**: Changes to parent class could break subclasses
- **Self-use issues**: Unclear which methods call which other methods internally
- **Constructor chain complexity**: Difficult to ensure proper initialization
- **newInstance() constraint**: The plugin uses reflection to instantiate testers, limiting inheritance options

### Why Not Include defaultCapture in CaptureParameter?

We considered adding `defaultCapture: (CaptureParameter) -> Unit` to `CaptureParameter`:

**Pros:**
- Easy access to default behavior from custom capturers
- No need to manually compose DefaultCapturer

**Cons:**
- **Mixing concerns**: Parameters should be data, not behavior
- **Circular dependency**: DefaultCapturer depends on CaptureParameter, which would contain DefaultCapturer's logic
- **Testing complexity**: Harder to mock and test when data and behavior are mixed
- **Conceptual confusion**: CaptureParameter would be both input and contain its own processing logic

**Decision**: Keep CaptureParameter as pure data. Users who need default behavior can compose it themselves:
```kotlin
class CustomCapturer(
  private val defaultCapturer: Capturer = AndroidComposePreviewTester.DefaultCapturer()
) : Capturer {
  override fun capture(parameter: CaptureParameter) {
    // Use defaultCapturer as needed
  }
}
```

### Alternative Approaches Considered

1. **Skeletal Implementation (AbstractCollection style)**
   - Would require open class with protected methods
   - More complex due to self-use documentation requirements
   - Not ideal with newInstance() constraint

2. **Interceptor Pattern (OkHttp/JUnit style)**
   - Powerful but overly complex for this use case
   - Would require chain management and ordering logic
   - Too heavy for a "minor API" feature

3. **Builder Pattern**
   - Good for configuration but doesn't solve behavior customization
   - Would still need hooks for capture logic

4. **Multiple Interfaces**
   - Could separate concerns further but adds complexity
   - More boilerplate for users

### API Extensibility

The `CaptureParameter` data class can be extended with new fields while maintaining backward compatibility:
```kotlin
data class CaptureParameter(
  val preview: ComposablePreview<AndroidPreviewInfo>,
  val filePath: String,
  val roborazziComposeOptions: RoborazziComposeOptions,
  val roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  // Future fields can be added with default values
  val newField: String = "default"
)
```

## Benefits

- **Addresses Issue #703**: Allows changeThreshold customization
- **Backward compatible**: Default constructor maintains existing behavior
- **Follows best practices**: Avoids inheritance issues from Effective Java
- **Simple API**: Single interface with clear responsibility
- **Flexible**: Supports both lambda and class-based implementations
- **Testable**: Easy to mock and test
- **Maintainable**: Clear separation between data and behavior

## Migration

No changes required for existing users:
```kotlin
// This continues to work
class MyTester : AndroidComposePreviewTester()
```

For customization, users create a custom Capturer:
```kotlin
class MyTester : AndroidComposePreviewTester(
  capturer = { parameter -> /* custom logic */ }
)
```

## Conclusion

The composition-based approach with `Capturer` interface provides a clean, simple solution for customizing AndroidComposePreviewTester. It addresses the immediate need (Issue #703) while maintaining backward compatibility and following established design principles.