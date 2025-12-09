package com.github.takahirom.roborazzi.sample.boxed

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziContext
import com.github.takahirom.roborazzi.RoborazziContextImpl
import com.github.takahirom.roborazzi.provideRoborazziContext

@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
fun boxedEnvironment(block: () -> Unit) {
  val originalProperties = System.getProperties().filter { it.key.toString().startsWith("roborazzi") }.toList()
  originalProperties.forEach {
    if (it.first.toString() == "roborazzi.test.result.dir") return@forEach
    System.clearProperty(it.first.toString())
  }
  val context = provideRoborazziContext()
  RoborazziContext = RoborazziContextImpl()
  try {
    block()
  } finally {
    RoborazziContext = context
    originalProperties.forEach { System.setProperty(it.first.toString(), it.second.toString()) }
  }
}

fun setupRoborazziSystemProperty(
  record: Boolean = false,
  compare: Boolean = false,
  verify: Boolean = false,
) {
  System.setProperty(
    "roborazzi.test.record",
    record.toString()
  )
  System.setProperty(
    "roborazzi.test.compare",
    compare.toString()
  )
  System.setProperty(
    "roborazzi.test.verify",
    verify.toString()
  )
}