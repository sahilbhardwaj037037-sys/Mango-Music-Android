package com.mangomusic.downloads

import com.mangomusic.domain.Track
import org.junit.Assert.*
import org.junit.Test

class DownloadPolicyTest {
    @Test fun eligibilityRequiresKnownSourceOrExplicitDirectPermission() {
        assertTrue(DownloadPolicy.eligible(Track("youtube:aqz-KE-bpKQ","Movie","Blender","https://www.youtube.com/watch?v=aqz-KE-bpKQ")))
        assertFalse(DownloadPolicy.eligible(Track("link","Track","Artist","https://example.com/a.mp3")))
        assertTrue(DownloadPolicy.eligible(Track("link","Track","Artist","https://example.com/a.mp3",downloadable=true)))
        assertFalse(DownloadPolicy.eligible(Track("link","Track","Artist","http://example.com/a.mp3",downloadable=true)))
    }
    @Test fun progressHandlesUnknownAndOverestimatedLengths() {
        assertNull(DownloadPolicy.percent(10,0));assertEquals(50,DownloadPolicy.percent(5,10));assertEquals(100,DownloadPolicy.percent(12,10));assertEquals(0,DownloadPolicy.percent(-1,10))
    }
    @Test fun reserveAndMaximumSizeAreEnforced() {
        assertFalse(DownloadPolicy.hasSpace(DownloadPolicy.RESERVE_BYTES-1))
        assertFalse(DownloadPolicy.hasSpace(Long.MAX_VALUE,DownloadPolicy.MAX_BYTES+1))
        assertTrue(DownloadPolicy.hasSpace(DownloadPolicy.RESERVE_BYTES+100,100))
    }
    @Test fun keysAreStableAndCannotContainPathTraversal() {
        assertEquals(DownloadPolicy.key("../a"),DownloadPolicy.key("../a"))
        assertTrue(DownloadPolicy.key("../../a?token=secret").matches(Regex("[a-f0-9]{32}")))
        assertNotEquals(DownloadPolicy.key("a"),DownloadPolicy.key("b"))
    }
}
