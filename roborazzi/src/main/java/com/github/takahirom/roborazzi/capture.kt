package com.github.takahirom.roborazzi

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.JsonWriter
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.espresso.util.HumanReadables
import com.dropbox.differ.ImageComparator
import io.github.takahirom.roborazzi.CompareReportCaptureResult
import io.github.takahirom.roborazzi.RoborazziReportConst
import java.io.File
import java.io.FileWriter
import org.robolectric.annotation.GraphicsMode
import org.robolectric.config.ConfigurationRegistry

internal val colors = listOf(
  0x3F9101,
  0x0E4A8E,
  0xBCBF01,
  0xBC0BA2,
  0x61AA0D,
  0x3D017A,
  0xD6A60A,
  0x7710A3,
  0xA502CE,
  0xeb5a00
)

enum class Visibility {
  Visible, Gone, Invisible;
}

val hasCompose = try {
  Class.forName("androidx.compose.ui.platform.AbstractComposeView")
  true
} catch (e: Exception) {
  false
}

sealed interface RoboComponent {
  class View(
    view: android.view.View,
    roborazziOptions: RoborazziOptions,
  ) : RoboComponent {
    override val width: Int = view.width
    override val height: Int = view.height
    override val image: Bitmap? = if (roborazziOptions.shouldTakeBitmap) {
      view.fetchImage(applyDeviceCrop = roborazziOptions.recordOptions.applyDeviceCrop)
    } else {
      null
    }
    override val rect: Rect = run {
      val rect = Rect()
      view.getGlobalVisibleRect(rect)
      rect
    }
    override val children: List<RoboComponent> = roborazziOptions
      .captureType
      .roboComponentChildVisitor(view, roborazziOptions)

    val id: Int = view.id

    val idResourceName: String? = if (0xFFFFFFFF.toInt() == view.id) {
      null
    } else {
      try {
        view.resources.getResourceName(view.id)
      } catch (e: Exception) {
        null
      }
    }
    override val text: String = HumanReadables.describe(view)
    override val visibility: Visibility = when (view.visibility) {
      android.view.View.VISIBLE -> Visibility.Visible
      android.view.View.GONE -> Visibility.Gone
      else -> Visibility.Invisible
    }
  }

  class Compose(
    node: SemanticsNode,
    roborazziOptions: RoborazziOptions,
  ) : RoboComponent {
    override val width: Int = node.layoutInfo.width
    override val height: Int = node.layoutInfo.height
    override val image: Bitmap? = if (roborazziOptions.shouldTakeBitmap) {
      node.fetchImage(applyDeviceCrop = roborazziOptions.recordOptions.applyDeviceCrop)
    } else {
      null
    }
    override val children: List<RoboComponent> = roborazziOptions
      .captureType
      .roboComponentChildVisitor(node, roborazziOptions)
    override val text: String = node.printToString()
    override val visibility: Visibility = Visibility.Visible
    val testTag: String? = node.config.getOrNull(SemanticsProperties.TestTag)

    override val rect: Rect = run {
      val rect = Rect()
      val boundsInWindow = node.boundsInWindow
      rect.set(boundsInWindow.toAndroidRect())
      rect
    }
  }

  val image: Bitmap?
  val rect: Rect
  val children: List<RoboComponent>
  val text: String
  val visibility: Visibility
  val width: Int
  val height: Int

  fun depth(): Int {
    return (children.maxOfOrNull {
      it.depth()
    } ?: 0) + 1
  }

  fun countOfComponent(): Int {
    return children.sumOf {
      it.countOfComponent()
    } + 1
  }

  companion object {
    internal val defaultChildVisitor: (Any, RoborazziOptions) -> List<RoboComponent> =
      { platformNode: Any, roborazziOptions: RoborazziOptions ->
        when {
          hasCompose && platformNode is androidx.compose.ui.platform.AbstractComposeView -> {
            (platformNode.getChildAt(0) as? ViewRootForTest)?.semanticsOwner?.rootSemanticsNode?.let {
              listOf(Compose(it, roborazziOptions))
            } ?: listOf()
          }

          platformNode is ViewGroup -> {
            (0 until platformNode.childCount).map {
              View(
                platformNode.getChildAt(it), roborazziOptions
              )
            }
          }

          hasCompose && platformNode is SemanticsNode -> {
            platformNode.children.map {
              Compose(
                it, roborazziOptions
              )
            }
          }

          else -> {
            listOf()
          }
        }
      }
  }
}

fun withViewId(@IdRes id: Int): (RoboComponent) -> Boolean {
  return { roboComponent ->
    when (roboComponent) {
      is RoboComponent.Compose -> false
      is RoboComponent.View -> roboComponent.id == id
    }
  }
}

fun withComposeTestTag(testTag: String): (RoboComponent) -> Boolean {
  return { roboComponent ->
    when (roboComponent) {
      is RoboComponent.Compose -> testTag == roboComponent.testTag
      is RoboComponent.View -> false
    }
  }
}

internal fun isNativeGraphicsEnabled() = try {
  Class.forName("org.robolectric.annotation.GraphicsMode")
  ConfigurationRegistry.get(GraphicsMode.Mode::class.java) == GraphicsMode.Mode.NATIVE
} catch (e: ClassNotFoundException) {
  false
}

@Deprecated(
  replaceWith = ReplaceWith("RoborazziOptions", "com.github.takahirom.roborazzi.RoborazziOptions"),
  message = "Please rename to RoborazziOptions. The reason why I'm renaming to RoborazziOptions is that the class is not only for capture but also verify."
)
typealias CaptureOptions = RoborazziOptions

data class RoborazziOptions(
  val captureType: CaptureType = if (isNativeGraphicsEnabled()) CaptureType.Screenshot() else CaptureType.Dump(),
  val compareOptions: CompareOptions = CompareOptions(),
  val recordOptions: RecordOptions = RecordOptions(),
) {
  sealed interface CaptureType {
    class Screenshot : CaptureType

    data class Dump(
      val takeScreenShot: Boolean = isNativeGraphicsEnabled(),
      val basicSize: Int = 600,
      val depthSlideSize: Int = 30,
      val query: ((RoboComponent) -> Boolean)? = null,
    ) : CaptureType
  }

  data class CompareOptions(
    val roborazziCompareReporter: RoborazziCompareReporter = RoborazziCompareReporter(),
    val resultValidator: (result: ImageComparator.ComparisonResult) -> Boolean,
  ) {
    constructor(
      roborazziCompareReporter: RoborazziCompareReporter = RoborazziCompareReporter(),
      /**
       * This value determines the threshold of pixel change at which the diff image is output or not.
       * The value should be between 0 and 1
       */
      changeThreshold: Float = 0.01F,
    ) : this(roborazziCompareReporter, ThresholdValidator(changeThreshold))
  }

  interface RoborazziCompareReporter {
    fun report(compareReportCaptureResult: CompareReportCaptureResult)

    companion object {
      operator fun invoke(): RoborazziCompareReporter {
        return if (roborazziVerifyEnabled()) {
          VerifyRoborazziCompareReporter()
        } else {
          JsonOutputRoborazziCompareReporter()

        }
      }
    }

    class JsonOutputRoborazziCompareReporter : RoborazziCompareReporter {

      init {
        File(RoborazziReportConst.compareReportDirPath).mkdirs()
      }

      override fun report(compareReportCaptureResult: CompareReportCaptureResult) {
        val absolutePath = File(RoborazziReportConst.compareReportDirPath).absolutePath
        val nameWithoutExtension = when (compareReportCaptureResult) {
          is CompareReportCaptureResult.Added -> compareReportCaptureResult.compareFile
          is CompareReportCaptureResult.Changed -> compareReportCaptureResult.goldenFile
          is CompareReportCaptureResult.Unchanged -> compareReportCaptureResult.goldenFile
        }.nameWithoutExtension
        val reportFileName =
          "$absolutePath/${compareReportCaptureResult.timestampNs}_$nameWithoutExtension.json"
        val fileWriter = FileWriter(
          reportFileName,
          true
        )
        JsonWriter(fileWriter).use { writer ->
          compareReportCaptureResult.writeJson(writer)
        }
        fileWriter.close()
      }
    }

    class VerifyRoborazziCompareReporter : RoborazziCompareReporter {
      override fun report(compareReportCaptureResult: CompareReportCaptureResult) {
        when (compareReportCaptureResult) {
          is CompareReportCaptureResult.Added -> throw AssertionError(
            "Roborazzi: ${compareReportCaptureResult.compareFile.absolutePath} is added.\n" +
              "See compare image at ${compareReportCaptureResult.compareFile.absolutePath}"
          )

          is CompareReportCaptureResult.Changed -> throw AssertionError(
            "Roborazzi: ${compareReportCaptureResult.goldenFile.absolutePath} is changed.\n" +
              "See compare image at ${compareReportCaptureResult.compareFile.absolutePath}"
          )

          is CompareReportCaptureResult.Unchanged -> {
          }
        }
      }
    }
  }

  data class RecordOptions(
    val resizeScale: Double = 1.0,
    val applyDeviceCrop: Boolean = false
  )

  internal val shouldTakeBitmap: Boolean = when (captureType) {
    is CaptureType.Dump -> {
      if (captureType.takeScreenShot && !isNativeGraphicsEnabled()) {
        throw IllegalArgumentException("Please update Robolectric Robolectric 4.10 Alpha 1 and Add @GraphicsMode(GraphicsMode.Mode.NATIVE) or use takeScreenShot = false")
      }
      captureType.takeScreenShot
    }

    is CaptureType.Screenshot -> {
      if (!isNativeGraphicsEnabled()) {
        throw IllegalArgumentException("Please update Robolectric Robolectric 4.10 Alpha 1 and Add @GraphicsMode(GraphicsMode.Mode.NATIVE) or use CaptureType.Dump")
      }
      true
    }
  }
}

internal val RoborazziOptions.CaptureType.roboComponentChildVisitor: (Any, RoborazziOptions) -> List<RoboComponent>
  get() {
    return when (this) {
      is RoborazziOptions.CaptureType.Dump -> RoboComponent.defaultChildVisitor
      is RoborazziOptions.CaptureType.Screenshot -> { _, _ -> listOf() }
    }
  }

internal sealed interface QueryResult {
  object Disabled : QueryResult
  data class Enabled(val matched: Boolean) : QueryResult

  companion object {
    fun of(component: RoboComponent, query: ((RoboComponent) -> Boolean)?): QueryResult {
      if (query == null) return Disabled
      return Enabled(query(component))
    }
  }
}

