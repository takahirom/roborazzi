package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.DefaultFileNameGenerator.DefaultNamingStrategy
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalRoborazziApi::class)
class DefaultFileNameGeneratorTest {

  private val className = "com.example.myapp.ui.SomeClass"
  private val methodName = "method"

  @Test
  fun testPackageDirAndClassAndMethod() {
    assertEquals(
      "com.example.myapp.ui/SomeClass.method",
      DefaultNamingStrategy.TestPackageDirAndClassAndMethod
        .generateOutputName(className, methodName)
    )
  }

  @Test
  fun testNestedPackageDirAndClassAndMethod() {
    assertEquals(
      "com/example/myapp/ui/SomeClass.method",
      DefaultNamingStrategy.TestNestedPackageDirAndClassAndMethod
        .generateOutputName(className, methodName)
    )
  }

  @Test
  fun testPackageDirAndClassAndMethodWithoutPackage() {
    assertEquals(
      "SomeClass.method",
      DefaultNamingStrategy.TestPackageDirAndClassAndMethod
        .generateOutputName("SomeClass", methodName)
    )
  }

  @Test
  fun testNestedPackageDirAndClassAndMethodWithoutPackage() {
    assertEquals(
      "SomeClass.method",
      DefaultNamingStrategy.TestNestedPackageDirAndClassAndMethod
        .generateOutputName("SomeClass", methodName)
    )
  }

  @Test
  fun fromOptionNameResolvesNewStrategies() {
    assertEquals(
      DefaultNamingStrategy.TestPackageDirAndClassAndMethod,
      DefaultNamingStrategy.fromOptionName("testPackageDirAndClassAndMethod")
    )
    assertEquals(
      DefaultNamingStrategy.TestNestedPackageDirAndClassAndMethod,
      DefaultNamingStrategy.fromOptionName("testNestedPackageDirAndClassAndMethod")
    )
  }
}
