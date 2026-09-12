package com.xniperbuilds.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the quality selector.
 *
 * Every assertion here stands for a defect that was actually measured on a real
 * download, where the chip a person tapped and the picture they got had drifted
 * apart. The same selector lives in the desktop build and has its own test there
 * for the same reason: it is quiet when it breaks. A download still arrives, still
 * carries the label that was asked for, and only looks worse — so nothing short of
 * a test catches it before a user does.
 *
 * These are pure functions with no Android types in them, so they run on the JVM.
 */
class FormatSelectorTest {

    private val chips = listOf("2160", "1080", "720", "480", "360")
    private val names = listOf("best", "max")
    private val codecs = listOf("any", "h264", "vp9", "av1")

    // ---- VIDEO_FORMAT -------------------------------------------------------

    @Test
    fun `the selector never filters on size`() {
        // Filtering on height handed a vertical clip a 608x1080 stream: a format that
        // is 1080 TALL and barely 600 wide, which satisfies "height at most 1080"
        // perfectly and is nothing like what the person tapping 1080p wanted.
        // Filtering on width would do the identical damage to a landscape video.
        // The size belongs in the sort, where it is read as the smallest dimension.
        assertFalse(VIDEO_FORMAT.contains("height"))
        assertFalse(VIDEO_FORMAT.contains("width"))
        assertFalse(VIDEO_FORMAT.contains("<="))
    }

    @Test
    fun `the selector is never the bare muxed one`() {
        // "best" on its own means the best stream that already carries sound, and on
        // YouTube that is a 360p file — the Best chip returned 360p from a 4K video.
        assertFalse(VIDEO_FORMAT == "best")
        assertFalse(VIDEO_FORMAT == "b")
    }

    @Test
    fun `the selector does not ask for video-only streams`() {
        // bestvideo matches video-ONLY formats, so a site publishing nothing but
        // streams that already carry sound matched none of them and fell through.
        assertFalse(VIDEO_FORMAT.contains("bestvideo"))
        assertFalse(VIDEO_FORMAT.contains("bestaudio"))
    }

    @Test
    fun `the selector still falls back to a muxed stream`() {
        // A site with no separate audio track at all must still download.
        assertEquals("bv*+ba/b", VIDEO_FORMAT)
    }

    // ---- videoSort ----------------------------------------------------------

    @Test
    fun `the size the user tapped leads the sort`() {
        for (c in codecs) {
            for (q in chips) {
                assertTrue("$c/$q does not lead with the cap: ${videoSort(c, q)}",
                    videoSort(c, q).startsWith("res:$q,"))
            }
        }
    }

    @Test
    fun `a name never becomes a cap`() {
        // Anything non-numeric has to come out as plain res, or the cap is built from
        // a word and every download breaks outright.
        for (c in codecs) {
            for (n in names + listOf("", "hd", "4k", "Best")) {
                val s = videoSort(c, n)
                assertFalse("'$n' built a cap: $s", s.contains("res:$n"))
                assertTrue("'$n' is not plain res: $s", s.startsWith("res,"))
            }
        }
    }

    @Test
    fun `nothing outranks the size`() {
        // Fields passed in outrank the engine's own defaults, so anything placed above
        // the size lets that thing beat it: 4K stays on the shelf while 1080p arrives.
        for (c in codecs) {
            for (q in chips + names) {
                assertTrue("$c/$q ranks something above size: ${videoSort(c, q)}",
                    videoSort(c, q).startsWith("res"))
            }
        }
    }

    @Test
    fun `protocol is preferred before bitrate`() {
        // YouTube also publishes a fragmented copy at a fatter bitrate. It is slower
        // and the progress bar crawls on it, so a direct stream has to win anyway.
        for (c in codecs) {
            val s = videoSort(c, "1080")
            assertTrue("'$c' has no proto: $s", s.contains("proto"))
            assertTrue("'$c' ranks bitrate above protocol: $s", s.indexOf("proto") < s.indexOf("br"))
        }
    }

    @Test
    fun `codec is a tie-break under size and protocol`() {
        for (c in codecs) {
            val s = videoSort(c, "2160")
            assertTrue("'$c' ranks codec above size: $s", s.indexOf("res") < s.indexOf("vcodec:"))
            assertTrue("'$c' ranks codec above protocol: $s", s.indexOf("proto") < s.indexOf("vcodec:"))
        }
    }

    @Test
    fun `any prefers the codec every phone decodes in hardware`() {
        // Left alone the engine reaches for AV1, which most phones have no hardware
        // decoder for and which YouTube ships at about a third of the bitrate of the
        // same frame in h264.
        assertTrue(videoSort("any", "1080").contains("vcodec:h264"))
        assertTrue(videoSort("", "1080").contains("vcodec:h264"))
        assertTrue(videoSort("h264", "1080").contains("vcodec:h264"))
        assertTrue(videoSort("vp9", "1080").contains("vcodec:vp9"))
        assertTrue(videoSort("av1", "1080").contains("vcodec:av01"))
    }

    @Test
    fun `audio is preferred as aac`() {
        // opus inside an mp4 is unusual, and several Android players hand back a video
        // with no sound at all.
        for (c in codecs) {
            assertTrue("'$c' does not prefer aac: ${videoSort(c, "1080")}",
                videoSort(c, "1080").contains("acodec:aac"))
        }
    }

    @Test
    fun `bitrate decides last`() {
        // Same size, same codec: take the fatter stream.
        for (c in codecs) {
            assertTrue("'$c' does not end on bitrate: ${videoSort(c, "1080")}",
                videoSort(c, "1080").endsWith(",br"))
        }
    }

    @Test
    fun `a sort is always sent`() {
        // Sending nothing on "any" is what let the engine pick the thinnest copy at
        // the chosen size.
        for (c in codecs + listOf("", "something-new")) {
            for (q in chips + names) {
                assertTrue("'$c'/'$q' sent no sort", videoSort(c, q).isNotBlank())
            }
        }
    }
}
