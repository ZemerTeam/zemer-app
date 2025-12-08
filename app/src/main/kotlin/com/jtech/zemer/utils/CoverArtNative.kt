package com.jtech.zemer.utils

/**
 * JNI bridge for native cover art embedding using Bento4 library.
 * This provides low-level MP4/M4A manipulation for embedding artwork.
 */
object CoverArtNative {

    init {
        System.loadLibrary("coverart")
    }

    /**
     * Embed cover art into an M4A/MP4 file.
     * @param inputPath Path to the input audio file
     * @param outputPath Path for the output file with embedded artwork
     * @param artworkData JPEG or PNG image data
     * @return true if successful, false otherwise
     */
    @JvmStatic
    external fun embedCoverArt(
        inputPath: String,
        outputPath: String,
        artworkData: ByteArray
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
