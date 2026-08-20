package com.jtech.zemer.playback.sabr

/**
 * Pure video-rung selection for a dual-track SABR session — which video-only adaptive format to pin via
 * `preferredVideoFormatId` (field 17). Extracted so the choice is JVM-tested without the innertube models.
 *
 * The ladder deliberately prefers **avc1** (broadly decodable on Android) over vp9/av01 at the same
 * resolution, matching the DIRECT quality ladder's decode-safety bias, then higher bitrate. The target is
 * a max height in pixels; the best rung AT OR BELOW it wins, and if every rung is taller than the target
 * the SMALLEST one is used (never null when any video rung exists) so playback still starts.
 */
internal object SabrVideoQuality {

    /** A candidate video-only rung (audio formats are excluded before this). */
    class Rung(val itag: Int, val height: Int, val mimeType: String, val bitrate: Int, val contentLength: Long)

    private fun isAvc1(m: String) = m.contains("avc1")

    /** Pick the video rung to pin for [maxHeightPx]. Returns null only when [rungs] is empty. */
    fun select(rungs: List<Rung>, maxHeightPx: Int): Rung? {
        if (rungs.isEmpty()) return null
        val order = compareByDescending<Rung> { it.height }
            .thenByDescending { isAvc1(it.mimeType) }
            .thenByDescending { it.bitrate }
        val atOrBelow = rungs.filter { it.height in 1..maxHeightPx }.sortedWith(order)
        if (atOrBelow.isNotEmpty()) return atOrBelow.first()
        // Every rung is taller than the target: fall to the smallest height (then avc1, then lower bitrate).
        return rungs.sortedWith(compareBy<Rung> { it.height }.thenByDescending { isAvc1(it.mimeType) }.thenBy { it.bitrate }).first()
    }
}
