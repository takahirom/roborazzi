package com.github.takahirom.roborazzi

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.annotations.RoboComposePreviewOptions
import org.junit.Assert.assertEquals
import org.junit.Test
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
class EffectiveRenderScaleTest {
  @Test fun previewWithoutAnAnnotationKeepsTheConfiguredScale() {
    assertEquals(0.5, preview("noOverride").effectiveRenderScale(0.5), 0.0)
  }

  @Test fun annotatedPreviewOverridesTheConfiguredScale() {
    assertEquals(0.25, preview("withOverride").effectiveRenderScale(0.5), 0.0)
  }

  @Test fun overloadedPreviewsResolveToTheirOwnAnnotation() {
    assertEquals(0.5, preview("overloaded", parameters = "").effectiveRenderScale(1.0), 0.0)
    assertEquals(
      0.25,
      preview("overloaded", parameters = "java.lang.String").effectiveRenderScale(1.0),
      0.0
    )
  }

  @Test fun previewInANestedClassIsResolved() {
    assertEquals(
      0.25,
      preview("nested", declaring = Nested::class.java.canonicalName!!).effectiveRenderScale(1.0),
      0.0
    )
  }

  @Test fun previewOfAnUnknownClassKeepsTheConfiguredScale() {
    assertEquals(0.5, preview("noOverride", declaring = "com.example.Gone").effectiveRenderScale(0.5), 0.0)
  }

  @Composable fun noOverride() = Unit

  @RoboComposePreviewOptions(renderScale = 0.25)
  @Composable fun withOverride() = Unit

  @RoboComposePreviewOptions(renderScale = 0.5)
  @Composable fun overloaded() = Unit

  @RoboComposePreviewOptions(renderScale = 0.25)
  @Composable fun overloaded(label: String) = Unit

  class Nested {
    @RoboComposePreviewOptions(renderScale = 0.25)
    @Composable fun nested() = Unit
  }

  private fun preview(
    method: String,
    parameters: String = "",
    declaring: String = EffectiveRenderScaleTest::class.java.name,
  ) = object : ComposablePreview<AndroidPreviewInfo> {
    override val previewInfo = AndroidPreviewInfo(
      device = "", fontScale = 1f, uiMode = Configuration.UI_MODE_NIGHT_NO,
      widthDp = -1, heightDp = -1,
    )
    override val previewIndex: Int? = null
    override val previewIndexDisplayName: String? = null
    override val otherAnnotationsInfo = null
    override val declaringClass = declaring
    override val methodName = method
    override val methodParametersType = parameters

    @Composable override fun invoke() = Unit
  }
}
