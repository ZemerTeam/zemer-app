package com.jtech.zemer.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jtech.zemer.constants.MyTopFilter
import com.jtech.zemer.db.entities.ArtistEntity
import com.jtech.zemer.db.entities.ArtistWhitelistEntity
import com.jtech.zemer.db.entities.Event
import com.jtech.zemer.db.entities.SongArtistMap
import com.jtech.zemer.db.entities.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/** The Stats screen's ranking SQL against a real in-memory Room database. */
class StatsQueriesTest {
    private lateinit var db: MusicDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = MusicDatabase(
            Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).allowMainThreadQueries().build(),
        )
        db.insert(ArtistEntity(id = "UCa", name = "Allowed"))
        db.insert(ArtistEntity(id = "UCb", name = "Also allowed"))
        db.insert(ArtistEntity(id = "UCx", name = "Not whitelisted"))
        db.insertWhitelist(listOf(ArtistWhitelistEntity("UCa", "Allowed"), ArtistWhitelistEntity("UCb", "Also allowed")))
        song("often", "UCa")
        song("long", "UCb")
        song("duet", "UCa", "UCb")
        song("outside", "UCx")
        song("episode", "UCa", isEpisode = true)
    }

    @After
    fun tearDown() = db.close()

    private fun song(id: String, vararg artists: String, isEpisode: Boolean = false) {
        db.insert(SongEntity(id = id, title = id, isEpisode = isEpisode))
        artists.forEachIndexed { i, artist -> db.insert(SongArtistMap(id, artist, i)) }
    }

    private fun play(songId: String, ms: Long, daysAgo: Long = 1) =
        db.insert(Event(songId = songId, timestamp = LocalDateTime.now().minusDays(daysAgo), playTime = ms))

    @Test
    fun songsRankByPlaysThenTimeAndStayWhitelistedMusicInTheWindow() = runBlocking {
        repeat(3) { play("often", 60_000) }
        play("long", 600_000)
        play("duet", 60_000)
        play("outside", 60_000)
        play("episode", 60_000)
        play("long", 60_000, daysAgo = 30) // before the week window

        val songs = db.songPlayStats(MyTopFilter.WEEK.toTimeMillis()).first()

        assertEquals(listOf("often", "long", "duet"), songs.map { it.song.id })
        assertEquals(listOf(3, 1, 1), songs.map { it.plays })
        assertEquals(180_000L, songs.first().timeListened)
        assertEquals(listOf("UCa"), songs.first().song.artists.map { it.id })
    }

    @Test
    fun artistsCountEveryCreditedPlayAndExcludeTheUnwhitelisted() = runBlocking {
        repeat(3) { play("often", 60_000) }
        play("duet", 60_000)
        play("long", 600_000)
        play("outside", 60_000)
        play("episode", 60_000)

        val artists = db.artistPlayStats(MyTopFilter.WEEK.toTimeMillis()).first()

        assertEquals(listOf("UCa", "UCb"), artists.map { it.artist.id })
        assertEquals(listOf(4, 2), artists.map { it.plays })
        assertEquals(listOf(240_000L, 660_000L), artists.map { it.timeListened })
    }

    @Test
    fun listeningTimeBeyondIntRangeDoesNotOverflow() = runBlocking {
        val hour = 3_600_000L
        repeat(700) { play("often", hour, daysAgo = 2) } // 700 h > Int.MAX_VALUE ms

        assertEquals(700 * hour, db.artistPlayStats(0).first().single().timeListened)
        assertEquals(700 * hour, db.songPlayStats(0).first().single().timeListened)
    }

    @Test
    fun tiedRowsHaveAStableOrder() = runBlocking {
        listOf("long", "often").forEach { play(it, 60_000) }

        assertEquals(listOf("long", "often"), db.songPlayStats(0).first().map { it.song.id })
    }
}
