package com.jtech.zemer.recognition

import com.jtech.zemer.db.entities.RecognitionHistoryEntity
import com.jtech.zemer.models.MediaMetadata

/**
 * A history entry as the playable [MediaMetadata] seeding its radio replay. Two rules the hand-built
 * inline version got wrong (and that persist through `MusicService.recoverSong`):
 *
 * - `duration` is the **-1 unknown sentinel**, never 0 — `recoverSong` treats 0 as a real length,
 *   skips the playerResponse fetch, and the song shows "0:00" everywhere forever (its repair branch
 *   only fires on -1).
 * - Artists carry their real browse ids from [RecognitionHistoryEntity.artistIds] (stored precisely
 *   for replay) so the persisted artist rows and the now-playing artist tap work. The display
 *   string joins names with ", " and [RecognitionHistoryFilter.joinIds] drops null/blank
 *   ids, so the two lists can disagree — ids are paired only when the counts line up (a lone name
 *   takes the first id); on a mismatch the names keep null ids rather than mis-attribute a channel.
 */
fun RecognitionHistoryEntity.toMediaMetadata(): MediaMetadata {
    val names = artist.split(", ").filter { it.isNotBlank() }
    val ids = artistIds.split(RecognitionHistoryFilter.ID_SEPARATOR).filter { it.isNotBlank() }
    val artists = when {
        names.size == ids.size -> names.zip(ids) { name, id -> MediaMetadata.Artist(id = id, name = name) }
        names.size == 1 && ids.isNotEmpty() -> listOf(MediaMetadata.Artist(id = ids.first(), name = names.single()))
        else -> names.map { MediaMetadata.Artist(id = null, name = it) }
    }
    return MediaMetadata(
        id = songId,
        title = title,
        artists = artists,
        duration = -1,
        thumbnailUrl = thumbnailUrl,
    )
}
