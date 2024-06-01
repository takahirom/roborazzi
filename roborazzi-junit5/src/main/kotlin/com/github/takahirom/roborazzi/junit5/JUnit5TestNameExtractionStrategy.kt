package com.github.takahirom.roborazzi.junit5

import com.github.takahirom.roborazzi.TestNameExtractionStrategy
import org.junit.jupiter.api.TestInfo

/**
 * Implementation of [TestNameExtractionStrategy] for JUnit 5 tests using Roborazzi.
 * Since JUnit 5's dynamic test method names cannot be detected reliably via the default strategy,
 * utilize the built-in extension model to track the class and method of the currently executed test.
 *
 * This class is executed from the Robolectric main thread using its sandboxed class loader,
 * which is why it has to jump through several hoops to obtain the static knowledge
 * stored inside [CurrentTestInfo]. We need to utilize reflection to access its getter method
 * to prevent accidentally creating a second object and missing the actual value.
 */
internal class JUnit5TestNameExtractionStrategy : TestNameExtractionStrategy {
  private val getCurrentTestInfo by lazy { createCurrentTestInfoGetterWithReflection() }

  override fun extract(): Pair<String, String>? {
    return getCurrentTestInfo()?.let { info ->
      info.testClass.get().name to info.testMethod.get().name
    }
  }

  private fun createCurrentTestInfoGetterWithReflection(): () -> TestInfo? {
    // Ensure usage of the system class loader here,
    // which is also used by RoborazziExtension
    val cl = ClassLoader.getSystemClassLoader()
    val cls = cl.loadClass(CurrentTestInfo::class.java.name)
    val instance = cls.getDeclaredField("INSTANCE").get(null)
    val method = cls.getDeclaredMethod("get")

    return { method.invoke(instance) as? TestInfo }
  }
}
