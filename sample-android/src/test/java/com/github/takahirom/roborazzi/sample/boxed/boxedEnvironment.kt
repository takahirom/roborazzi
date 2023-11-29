package com.github.takahirom.roborazzi.sample.boxed

import com.github.takahirom.roborazzi.RoborazziContext
import com.github.takahirom.roborazzi.RoborazziContextImpl
import com.github.takahirom.roborazzi.provideRoborazziContext

fun boxedEnvironment(block: () -> Unit) {
  val originalProperties = System.getProperties().filter { it.key.toString().startsWith("roborazzi") }.toList()
  originalProperties.forEach { System.clearProperty(it.first.toString()) }
  val context = provideRoborazziContext()
  RoborazziContext = RoborazziContextImpl()
  block()
  RoborazziContext = context
  originalProperties.forEach { System.setProperty(it.first.toString(), it.second.toString()) }
}