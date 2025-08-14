package io.github.takahirom.roborazzi

import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class VersionComparisonTest {

    @Test
    fun `version comparison basic cases`() {
        // Test basic numeric comparisons
        assertTrue("0.6.9 < 0.7.0", isVersionLessThan("0.6.9", "0.7.0"))
        assertFalse("0.7 == 0.7.0", isVersionLessThan("0.7", "0.7.0")) // 0.7.0 and 0.7 are equivalent (0.7 becomes 0.7.0)
        assertFalse("0.7.0 == 0.7.0", isVersionLessThan("0.7.0", "0.7.0"))
        assertFalse("0.7.1 > 0.7.0", isVersionLessThan("0.7.1", "0.7.0"))
    }

    @Test
    fun `version comparison pre-release cases`() {
        // Pre-release versions should be less than stable versions
        assertTrue("0.7.0-alpha01 < 0.7.0", isVersionLessThan("0.7.0-alpha01", "0.7.0"))
        assertTrue("0.7.0-beta < 0.7.0", isVersionLessThan("0.7.0-beta", "0.7.0"))
        assertTrue("0.7.0-rc1 < 0.7.0", isVersionLessThan("0.7.0-rc1", "0.7.0"))
        
        // Stable versions should not be less than pre-release versions
        assertFalse("0.7.0 !< 0.7.0-alpha", isVersionLessThan("0.7.0", "0.7.0-alpha"))
        
        // Pre-release comparison (lexicographic)
        assertTrue("0.7.0-alpha < 0.7.0-beta", isVersionLessThan("0.7.0-alpha", "0.7.0-beta"))
        assertTrue("0.7.0-alpha01 < 0.7.0-alpha02", isVersionLessThan("0.7.0-alpha01", "0.7.0-alpha02"))
    }

    @Test
    fun `version comparison edge cases`() {
        // Different length numeric parts
        assertTrue("0.6 < 0.7.0", isVersionLessThan("0.6", "0.7.0"))
        assertTrue("0.7.0 < 0.7.0.1", isVersionLessThan("0.7.0", "0.7.0.1"))
        
        // Same pre-release qualifiers
        assertFalse("0.7.0-alpha == 0.7.0-alpha", isVersionLessThan("0.7.0-alpha", "0.7.0-alpha"))
        
        // Invalid version parts should be treated as 0, so 0.7.0 == 0.7.0
        assertFalse("0.7.invalid == 0.7.0", isVersionLessThan("0.7.invalid", "0.7.0"))
    }

    @Test
    fun `parseVersion correctly handles different formats`() {
        val (nums1, qualifier1) = parseVersion("0.7.0")
        assertTrue("Stable version parsing", nums1 == listOf(0, 7, 0) && qualifier1 == null)
        
        val (nums2, qualifier2) = parseVersion("0.7.0-alpha01")
        assertTrue("Pre-release version parsing", nums2 == listOf(0, 7, 0) && qualifier2 == "alpha01")
        
        val (nums3, qualifier3) = parseVersion("1.2.3-BETA")
        assertTrue("Case insensitive qualifier", nums3 == listOf(1, 2, 3) && qualifier3 == "beta")
    }
}