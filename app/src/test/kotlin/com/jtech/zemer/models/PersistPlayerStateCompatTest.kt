package com.jtech.zemer.models

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass

/**
 * Pins the on-disk shape of [PersistPlayerState], the snapshot MusicService Java-serializes to
 * `persistent_player_state.data` and reads back to restore repeat/shuffle/volume and the resume
 * position.
 *
 * [PersistQueueCompatTest] guards the queue graph; this guards the player-state file, which is the
 * one that actually carries the resume position. The failure it exists to catch is SILENT: with the
 * serialVersionUID pinned, renaming a field does not throw. Java serialization matches stream fields
 * to class fields by NAME, so a renamed `currentPosition` deserializes as 0 and every updating user
 * resumes at the start of the track - with no exception for MusicService's `runCatching` to report.
 * A retype or a class/package rename fails louder (InvalidClassException / ClassNotFoundException),
 * which the restore swallows into a total loss of the saved queue position.
 *
 * So these names and types are the persisted wire format, not a naming preference. They are equally
 * fixed at the other end: all seven non-timestamp fields are verbatim androidx.media3 `Player`
 * property names, which is what the snapshot is a field-for-field capture of.
 */
class PersistPlayerStateCompatTest {

    /** Field name -> JVM type code, exactly as written into the serialized stream descriptor. */
    private val persistedFields = mapOf(
        "playWhenReady" to 'Z',
        "repeatMode" to 'I',
        "shuffleModeEnabled" to 'Z',
        "volume" to 'F',
        "currentPosition" to 'J',
        "currentMediaItemIndex" to 'I',
        "playbackState" to 'I',
        "timestamp" to 'J',
    )

    @Test
    fun `serialized descriptor is pinned to the shipped shape`() {
        val descriptor = requireNotNull(ObjectStreamClass.lookup(PersistPlayerState::class.java)) {
            "PersistPlayerState must stay Serializable - MusicService reads it with ObjectInputStream"
        }

        assertEquals(
            "class name is written into the stream; renaming it orphans every saved player state",
            "com.jtech.zemer.models.PersistPlayerState",
            descriptor.name,
        )
        assertEquals(
            "unpinning the serialVersionUID breaks restore for every updating user",
            5774712238918091842L,
            descriptor.serialVersionUID,
        )
        assertEquals(
            "a renamed or retyped field silently loses that value on restore",
            persistedFields,
            descriptor.fields.associate { it.name to it.typeCode },
        )
    }

    @Test
    fun `round trip preserves every field`() {
        val state = PersistPlayerState(
            playWhenReady = true,
            repeatMode = 2,
            shuffleModeEnabled = true,
            volume = 0.8f,
            currentPosition = 42_000L,
            currentMediaItemIndex = 3,
            playbackState = 3,
            timestamp = 1_700_000_000_000L,
        )

        val bytes = ByteArrayOutputStream().also { out ->
            ObjectOutputStream(out).use { it.writeObject(state) }
        }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use {
            it.readObject() as PersistPlayerState
        }

        assertEquals(state, restored)
    }
}
