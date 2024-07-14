package com.github.takahirom.roborazzi

@ExperimentalRoborazziApi
enum class RoborazziTaskType(private val taskName: String) {
  None(""),
  Record("record"),
  Compare("compare"),
  Verify("verify"),
  VerifyAndRecord("verifyAndRecord"),
  CompareAndRecord("compareAndRecord");

  fun isEnabled(): Boolean {
    return this != None
  }

  fun isRecording(): Boolean {
    return this == Record || this == VerifyAndRecord || this == CompareAndRecord
  }

  fun isComparing(): Boolean {
    return this == Compare || this == CompareAndRecord
  }

  fun isVerifying(): Boolean {
    return this == Verify || this == VerifyAndRecord
  }

  fun convertVerifyingToComparing(): RoborazziTaskType {
    return when (this) {
      Verify -> Compare
      VerifyAndRecord -> CompareAndRecord
      else -> this
    }
  }

  fun isVerifyingAndRecording(): Boolean {
    return this == VerifyAndRecord
  }

  companion object {
    fun of(
      isRecording: Boolean,
      isComparing: Boolean,
      isVerifying: Boolean
    ): RoborazziTaskType {
      return when {
        isRecording && isVerifying -> VerifyAndRecord
        isRecording && isComparing -> CompareAndRecord
        isRecording -> Record
        isVerifying -> Verify
        isComparing -> Compare
        else -> None
      }
    }

    fun getOrderOfTaskName(taskName: String): Int {
      return values().indexOfLast { taskName.contains(it.taskName, true) }
    }
  }
}