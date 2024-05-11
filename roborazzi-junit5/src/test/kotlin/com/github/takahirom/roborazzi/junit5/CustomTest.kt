package com.github.takahirom.roborazzi.junit5

import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import java.util.stream.Stream

/**
 * Example of a custom JUnit 5 test template, ensuring that Roborazzi file name generation
 * also works with custom extensions to the Jupiter test model. The idea of [CustomTest]
 * is to simply run any annotated test method twice.
 */
@TestTemplate
@ExtendWith(CustomTestTemplateContextProvider::class)
annotation class CustomTest

private class CustomTestTemplateContextProvider : TestTemplateInvocationContextProvider {
  override fun supportsTestTemplate(context: ExtensionContext?): Boolean {
    return true
  }

  override fun provideTestTemplateInvocationContexts(context: ExtensionContext?): Stream<TestTemplateInvocationContext> {
    return Stream.of(CustomTestTemplateContext(true), CustomTestTemplateContext(false))
  }
}

private class CustomTestTemplateContext(private val isFirst: Boolean) :
  TestTemplateInvocationContext {
  override fun getDisplayName(invocationIndex: Int) = buildString {
    append(super.getDisplayName(invocationIndex))
    append(if (isFirst) " first" else " second")
    append(" invocation")
  }

  override fun getAdditionalExtensions() = listOf(CustomTestParameterResolver(isFirst))
}

private class CustomTestParameterResolver(private val isFirst: Boolean) : ParameterResolver {
  override fun supportsParameter(
    parameterContext: ParameterContext,
    extensionContext: ExtensionContext
  ): Boolean {
    return parameterContext.parameter.type == Boolean::class.java
  }

  override fun resolveParameter(
    parameterContext: ParameterContext?,
    extensionContext: ExtensionContext?
  ): Any {
    return isFirst
  }
}
