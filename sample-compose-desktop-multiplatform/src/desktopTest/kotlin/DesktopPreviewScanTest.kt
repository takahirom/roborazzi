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
    val testParameters = tester.testParameters()
    assertTrue(testParameters.isNotEmpty(), "No previews found by DesktopComposePreviewTester")
    testParameters.forEach { testParameter ->
      tester.test(testParameter)
    }
  }
}
