package io.github.takahirom.roborazzi

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface CaptureResult {
  val timestampNs: Long
  val compareFile: String?
  val actualFile: String?
  val goldenFile: String?
  val contextData: Map<String, Any>

  val reportFile: String
    get() = when (val result = this) {
      is Added -> result.actualFile
      is Changed -> result.compareFile
      is Recorded -> result.goldenFile
      is Unchanged -> result.goldenFile
    }

  @Serializable
  @SerialName("recorded")
  data class Recorded(
    @SerialName("golden_file_path")
    override val goldenFile: String,
    @SerialName("timestamp")
    override val timestampNs: Long,
    @SerialName("context_data")
    override val contextData: Map<String, @Contextual Any>
  ) : CaptureResult {

    override val actualFile: String?
      get() = null
    override val compareFile: String?
      get() = null
  }

  @Serializable
  @SerialName("added")
  data class Added(
    @SerialName("compare_file_path")
    override val compareFile: String,
    @SerialName("actual_file_path")
    override val actualFile: String,
    @SerialName("golden_file_path")
    override val goldenFile: String,
    @SerialName("timestamp")
    override val timestampNs: Long,
    @SerialName("context_data")
    override val contextData: Map<String, @Contextual Any>
  ) : CaptureResult {
  }

  @Serializable
  @SerialName("changed")
  data class Changed(
    @SerialName("compare_file_path")
    override val compareFile: String,
    @SerialName("golden_file_path")
    override val goldenFile: String,
    @SerialName("actual_file_path")
    override val actualFile: String,
    @SerialName("timestamp")
    override val timestampNs: Long,
    @SerialName("context_data")
    override val contextData: Map<String, @Contextual Any>
  ) : CaptureResult {
  }

  @Serializable
  @SerialName("unchanged")
  data class Unchanged(
    @SerialName("golden_file_path")
    override val goldenFile: String,
    @SerialName("timestamp")
    override val timestampNs: Long,
    @SerialName("context_data")
    override val contextData: Map<String, @Contextual Any>
  ) : CaptureResult {
    override val actualFile: String?
      get() = null
    override val compareFile: String?
      get() = null
  }
}
