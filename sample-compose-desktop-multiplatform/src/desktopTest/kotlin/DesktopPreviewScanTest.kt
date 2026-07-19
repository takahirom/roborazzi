import com.github.takahirom.roborazzi.DefaultDesktopComposePreviewTester
import com.github.takahirom.roborazzi.DesktopComposePreviewTester
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalRoborazziApi::class)
class DesktopPreviewScanTest {
  @Test
  fun scanAndCapturePreviews() {
    val tester = DefaultDesktopComposePreviewTester(
      options = DesktopComposePreviewTester.Options(
        scanOptions = DesktopComposePreviewTester.Options.ScanOptions(
          packages = listOf("com.github.takahirom.sample.previews"),
        ),
      ),
    )
    val previews = tester.previews()
    assertTrue(previews.isNotEmpty(), "No previews found by DesktopComposePreviewTester")
    previews.forEach { preview ->
      tester.test(preview)
    }
  }
}
