package com.sagisawa.gbfaccelerator.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersioningTest {

    @Test
    fun isNewer_comparesNumericSegments() {
        assertTrue(Versioning.isNewer("1.1.10", "1.1.9"))
        assertFalse(Versioning.isNewer("1.2.0", "1.10.0"))
        assertTrue(Versioning.isNewer("1.10.0", "1.2.0"))
        assertFalse(Versioning.isNewer("1.1.9", "1.1.10"))
    }

    @Test
    fun isNewer_ignoresPrefixAndTrailingZero() {
        assertFalse(Versioning.isNewer("v1.2.0", "1.2.0"))
        assertFalse(Versioning.isNewer("1.2", "1.2.0"))
        assertTrue(Versioning.isNewer("V1.2.1", "1.2.0"))
    }

    @Test
    fun isNewer_ordersPreReleaseBeforeStable() {
        assertFalse(Versioning.isNewer("1.2.0-rc1", "1.2.0"))
        assertTrue(Versioning.isNewer("1.2.0", "1.2.0-rc1"))
        assertTrue(Versioning.isNewer("1.2.0-rc2", "1.2.0-rc1"))
        assertFalse(Versioning.isNewer("1.2.0-alpha", "1.2.0-beta"))
    }

    @Test
    fun compare_handlesBuildMetadata() {
        assertFalse(Versioning.isNewer("1.2.0+build2", "1.2.0+build1"))
        assertTrue(Versioning.isNewer("1.2.1+build1", "1.2.0+build9"))
    }
}
