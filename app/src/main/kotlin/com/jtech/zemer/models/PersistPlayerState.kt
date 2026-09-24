package com.jtech.zemer.models

import java.io.Serializable

data class PersistPlayerState(
    val playWhenReady: Boolean,
    val repeatMode: Int,
    val shuffleModeEnabled: Boolean,
    val volume: Float,
    val currentPosition: Long,
    val currentMediaItemIndex: Int,
    val playbackState: Int,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable {
    companion object {
        // Pinned so class edits can't orphan the persisted player state (see MediaMetadata).
        private const val serialVersionUID = 5774712238918091842L
    }
}