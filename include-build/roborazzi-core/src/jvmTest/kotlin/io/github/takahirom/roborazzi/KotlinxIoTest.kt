package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.KotlinxIo
import com.github.takahirom.roborazzi.absolutePath
import com.github.takahirom.roborazzi.nameWithoutExtension
import com.github.takahirom.roborazzi.relativeTo
import kotlinx.io.files.Path
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KotlinxIoTest {

  @get:Rule
  val tmpDir:TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

  @Test
  fun testAbsolutePath() {
    val path = Path("/Users/roborazzi/file.txt")

    assertEquals("/Users/roborazzi/file.txt", path.absolutePath)
  }

  @Test
  fun testNameWithoutExtension() {
    val path = "/Users/roborazzi/file.txt"

    assertEquals("file", path.nameWithoutExtension)
  }

  @Test
  fun testRelativeToSamePath() {
    val path = "/Users/roborazzi/file.txt"

    val actualPath = Path(path).relativeTo(Path(path)).toString()

    assertEquals("", actualPath)
  }

  @Test
  fun testRelativeToDifferentPath() {
    val base = "/Users/roborazzi"
    val path = "/Users/roborazzi/docs/file.txt"

    val actualPath = Path(path).relativeTo(Path(base)).toString()

    assertEquals("docs/file.txt", actualPath)
  }

  @Test
  fun testRelativeToDifferentBase() {
    val base = "/Users/roborazzi/docs"
    val path = "/Users/roborazzi/music/file.mp3"

    val actualPath = Path(path).relativeTo(Path(base)).toString()

    assertEquals("../music/file.mp3", actualPath)
  }

  @Test
  fun testReadText() {
    val testFile = tmpDir.newFile("kotlinx_io_write_test.txt")
    val expectedReadText = "Sample text for KotlinxIo"
    testFile.writeText(expectedReadText)

    val actualReadText = KotlinxIo.readText(testFile.absolutePath)

    assertEquals(expectedReadText, actualReadText)
  }

  @Test
  fun testWriteText() {
    val expectedWriteText = "Write a new text to the file"
    val file = tmpDir.newFile("kotlinx_io_write_test.txt")

    val path = file.absolutePath
    KotlinxIo.writeText(path, expectedWriteText)

    val actualReadText = KotlinxIo.readText(path)

    assertEquals(expectedWriteText, actualReadText)
  }

}