package io.github.takahirom.roborazzi

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

data class VersionComparisonTestCase(
    val current: String,
    val required: String,
    val expected: Boolean,
    val description: String
)

@RunWith(Parameterized::class)
class VersionComparisonTest(private val testCase: VersionComparisonTestCase) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<VersionComparisonTestCase>> {
            return listOf(
                VersionComparisonTestCase("0.6.9", "0.7.0", true, "0.6.9 < 0.7.0"),
                VersionComparisonTestCase("0.7", "0.7.0", false, "0.7 == 0.7.0"),
                VersionComparisonTestCase("0.7.0", "0.7.0", false, "0.7.0 == 0.7.0"),
                VersionComparisonTestCase("0.7.1", "0.7.0", false, "0.7.1 > 0.7.0"),
                VersionComparisonTestCase("0.7.0-alpha01", "0.7.0", true, "0.7.0-alpha01 < 0.7.0"),
                VersionComparisonTestCase("0.7.0-beta", "0.7.0", true, "0.7.0-beta < 0.7.0"),
                VersionComparisonTestCase("0.7.0-rc1", "0.7.0", true, "0.7.0-rc1 < 0.7.0"),
                VersionComparisonTestCase("0.7.0", "0.7.0-alpha", false, "0.7.0 !< 0.7.0-alpha"),
                VersionComparisonTestCase("0.7.0-alpha", "0.7.0-beta", true, "0.7.0-alpha < 0.7.0-beta"),
                VersionComparisonTestCase("0.7.0-alpha01", "0.7.0-alpha02", true, "0.7.0-alpha01 < 0.7.0-alpha02"),
                VersionComparisonTestCase("0.6", "0.7.0", true, "0.6 < 0.7.0"),
                VersionComparisonTestCase("0.7.0", "0.7.0.1", true, "0.7.0 < 0.7.0.1"),
                VersionComparisonTestCase("0.7.0-alpha", "0.7.0-alpha", false, "0.7.0-alpha == 0.7.0-alpha"),
                VersionComparisonTestCase("0.7.invalid", "0.7.0", false, "0.7.invalid == 0.7.0")
            ).map { arrayOf(it) }
        }
    }

    @Test
    fun `version comparison`() {
        assertEquals(
            "${testCase.description} failed", 
            testCase.expected, 
            isVersionLessThan(testCase.current, testCase.required)
        )
    }
}

// Separate test class for parseVersion function testing
class ParseVersionTest {
    
    @Test
    fun `parseVersion correctly handles stable version`() {
        val (nums, qualifier) = parseVersion("0.7.0")
        assertEquals("Stable version numeric parts", listOf(0, 7, 0), nums)
        assertNull("Stable version qualifier", qualifier)
    }
    
    @Test
    fun `parseVersion correctly handles pre-release version`() {
        val (nums, qualifier) = parseVersion("0.7.0-alpha01")
        assertEquals("Pre-release version numeric parts", listOf(0, 7, 0), nums)
        assertEquals("Pre-release version qualifier", "alpha01", qualifier)
    }
    
    @Test
    fun `parseVersion handles case insensitive qualifier`() {
        val (nums, qualifier) = parseVersion("1.2.3-BETA")
        assertEquals("Case insensitive version numeric parts", listOf(1, 2, 3), nums)
        assertEquals("Case insensitive version qualifier", "beta", qualifier)
    }
}