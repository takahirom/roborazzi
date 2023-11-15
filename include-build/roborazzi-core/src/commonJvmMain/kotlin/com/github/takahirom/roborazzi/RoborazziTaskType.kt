package com.github.takahirom.roborazzi

@ExperimentalRoborazziApi
enum class RoborazziTaskType {
  None,
  Record,
  Compare,
  Verify,
  VerifyAndRecord, ;

  fun isEnabled(): Boolean {
    return this != None
  }

  fun isRecording(): Boolean {
    return this == Record || this == VerifyAndRecord
  }

  fun isComparing(): Boolean {
    return this == Compare
  }

  fun isVerifying(): Boolean {
    return this == Verify || this == VerifyAndRecord
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
        isRecording -> Record
        isComparing -> Compare
        isVerifying -> Verify
        else -> None
      }
    }
  }
}