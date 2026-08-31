package com.github.lightjunction.magicbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparisonTest {
    @Test
    fun onlyReportsStrictlyNewerReleases() {
        assertTrue(isReleaseNewer("v0.1.3", "0.1.2"))
        assertTrue(isReleaseNewer("v1.0.0", "0.99.99"))

        assertFalse(isReleaseNewer("v0.1.2", "0.1.2"))
        assertFalse(isReleaseNewer("v0.1.2", "0.2.0"))
    }

    @Test
    fun respectsPrereleasePrecedence() {
        assertTrue(isReleaseNewer("v0.2.0", "0.2.0-rc.1"))
        assertTrue(isReleaseNewer("v0.2.0-rc.2", "0.2.0-rc.1"))

        assertFalse(isReleaseNewer("v0.1.2", "0.2.0-rc.1"))
        assertFalse(isReleaseNewer("v0.2.0-rc.1", "0.2.0"))
    }

    @Test
    fun ignoresBuildMetadataAndRejectsMalformedVersions() {
        assertFalse(isReleaseNewer("v0.1.2+release", "0.1.2+local"))
        assertFalse(isReleaseNewer("latest", "0.1.2"))
        assertFalse(isReleaseNewer("v0.1.3", "development"))
        assertFalse(isReleaseNewer("v1.0.1", "01.0.0"))
        assertFalse(isReleaseNewer("v1.0.1", "1.0.0-01"))
        assertFalse(isReleaseNewer("v1.0.1", "1.0.0-rc..1"))
        assertFalse(isReleaseNewer("v1.0.1", "1.0.0+build."))
    }
}
