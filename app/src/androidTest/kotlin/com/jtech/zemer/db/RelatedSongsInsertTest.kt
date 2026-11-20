package com.jtech.zemer.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jtech.zemer.db.entities.RelatedSongMap
import com.jtech.zemer.db.entities.SongEntity
import com.jtech.zemer.models.MediaMetadata
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Related songs are stored only while their song's row exists (a FOREIGN KEY crash from MusicService.recoverSong). */
class RelatedSongsInsertTest {
    private lateinit var db: MusicDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = MusicDatabase(
            Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).allowMainThreadQueries().build(),
        )
    }

    @After
    fun tearDown() = db.close()

    private fun related(id: String) = MediaMetadata(id = id, title = id, artists = emptyList(), duration = -1)

    @Test(expected = SQLiteConstraintException::class)
    fun theCrashIgnoreDoesNotCover() {
        db.insert(SongEntity(id = "r1", title = "r1"))
        db.insert(RelatedSongMap(songId = "gone", relatedSongId = "r1"))
    }

    @Test
    fun aDeletedSongGetsNoRelatedRowsAndNoCrash() {
        db.insertRelatedSongs("gone", listOf(related("r1")))
        assertFalse(db.hasRelatedSongs("gone"))
    }

    @Test
    fun anExistingSongGetsItsRelatedSongs() {
        db.insert(SongEntity(id = "here", title = "here"))
        db.insertRelatedSongs("here", listOf(related("r1"), related("r2")))
        assertTrue(db.hasRelatedSongs("here"))
        assertNotNull(db.getSongByIdBlocking("r2"))
    }
}
