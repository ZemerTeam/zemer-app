package com.jtech.zemer.utils

/**
 * JNI bridge for native metadata embedding using Bento4 library.
 * Supports embedding cover art, title, artist, album, and year into M4A/MP4 files.
 * All text is stored as UTF-8 (supports Hebrew, Arabic, and all Unicode).
 */
object CoverArtNative {

    init {
        System.loadLibrary("coverart")
    }

    /**
     * Embed metadata into an M4A/MP4 file.
     * @param inputPath Path to the input audio file
     * @param outputPath Path for the output file with embedded metadata
     * @param artworkData JPEG or PNG image data (can be null to skip artwork)
     * @param title Song title (can be null)
     * @param artist Artist name (can be null)
     * @param album Album name (can be null)
     * @param year Release year as string (can be null)
     * @return true if successful, false otherwise
     */
    @JvmStatic
    external fun embedMetadata(
        inputPath: String,
        outputPath: String,
        artworkData: ByteArray?,
        title: String?,
        artist: String?,
        album: String?,
        year: String?
    ): Boolean

    /**
     * Defragment a DASH/fragmented MP4 file to standard MP4.
     * @param inputPath Path to the fragmented input file
     * @param outputPath Path for the defragmented output file
     * @return true if successful, false otherwise
     */
    @JvmStatic
    external fun defragmentFile(
        inputPath: String,
        outputPath: String
    ): Boolean
}
