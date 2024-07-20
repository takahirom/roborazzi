package com.github.takahirom.roborazzi.idea.preview

enum class RoborazziTaskNames(private val taskName: String) {
  None(""),
  Record("record"),
  Compare("compare"),
  Verify("verify"),
  VerifyAndRecord("verifyAndRecord"),
  CompareAndRecord("compareAndRecord");

  companion object {
    fun getOrderOfTaskName(taskName: String): Int {
      return values().indexOfLast { taskName.contains(it.taskName, true) }
    }
  }
}