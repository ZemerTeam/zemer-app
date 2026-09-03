package com.jtech.zemer.lyrics

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import com.jtech.zemer.constants.EnableLrcLibKey
import com.jtech.zemer.constants.EnableYouTubeLyricsKey
import com.jtech.zemer.constants.LyricsProviderOrderKey
import com.jtech.zemer.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression for the chain's pick rule: a YouTube auto-caption transcript (timestamped ASR) used to win over a
 * curated plain Zemer body just for passing `isSynced`, and was then persisted with a provider so it was never
 * re-resolved. Low-trust providers are now a last resort only.
 */
class SyncedFirstPickerTest {
    private fun provider(name: String, low: Boolean = false) = object : LyricsProvider {
        override val name = name
        override val enabledKey: Preferences.Key<Boolean> = booleanPreferencesKey("enable$name")
        override val lowTrust = low
        override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int, album: String?): Result<String> = Result.failure(IllegalStateException("unused"))
    }
    private val zemer = provider("Zemer")
    private val lrclib = provider("LrcLib")
    private val youtube = provider("YouTube Subtitle", low = true)
    private val plain = "line one\nline two"
    private val synced = "[00:01.000] line one\n[00:05.000] line two"

    @Test
    fun `low-trust synced transcript never outranks a trusted plain body`() {
        val picker = SyncedFirstPicker()
        assertNull(picker.offer(zemer, LabeledLyrics("Zemer · shironet", plain)))
        assertNull(picker.offer(youtube, LabeledLyrics("YouTube Subtitle", synced)))
        assertEquals(LyricsHelper.Fetched(plain, "Zemer · shironet"), picker.result())
    }

    @Test
    fun `trusted synced body stops the walk`() {
        val picker = SyncedFirstPicker()
        assertNull(picker.offer(zemer, LabeledLyrics("Zemer · shironet", plain)))
        assertEquals(LyricsHelper.Fetched(synced, "LrcLib"), picker.offer(lrclib, LabeledLyrics("LrcLib", synced)))
    }

    @Test
    fun `first trusted plain body wins over later trusted plain bodies`() {
        val picker = SyncedFirstPicker()
        picker.offer(zemer, LabeledLyrics("Zemer · jyrics", plain))
        picker.offer(lrclib, LabeledLyrics("LrcLib", "other plain"))
        assertEquals("Zemer · jyrics", picker.result().provider)
    }

    @Test
    fun `low-trust body is served only when nothing trusted answered`() {
        val picker = SyncedFirstPicker()
        assertNull(picker.offer(youtube, LabeledLyrics("YouTube Subtitle", synced)))
        assertEquals(LyricsHelper.Fetched(synced, "YouTube Subtitle"), picker.result())
        val later = SyncedFirstPicker()
        later.offer(youtube, LabeledLyrics("YouTube Subtitle", synced))
        later.offer(lrclib, LabeledLyrics("LrcLib", plain))
        assertEquals("LrcLib", later.result().provider)
    }

    @Test
    fun `blank and not-found answers are ignored`() {
        val picker = SyncedFirstPicker()
        assertNull(picker.offer(zemer, LabeledLyrics("Zemer · shironet", "  ")))
        assertNull(picker.offer(lrclib, LabeledLyrics("LrcLib", LYRICS_NOT_FOUND)))
        assertEquals(LyricsHelper.Fetched(LYRICS_NOT_FOUND, null), picker.result())
    }

    /** The chain reads ONE preference snapshot: order + every enable switch come from the same [Preferences]. */
    @Test
    fun `enabled providers come from one snapshot in the user's order`() {
        val prefs = preferencesOf(
            LyricsProviderOrderKey to "LrcLib,Zemer",
            EnableLrcLibKey to false,
            EnableYouTubeLyricsKey to false,
        )
        assertEquals(listOf("Zemer", "SimpMusic", "Musixmatch"), LyricsHelper.enabledProviders(prefs).map { it.name })
        assertEquals(LyricsProviderRegistry.providerNames.size, LyricsHelper.enabledProviders(preferencesOf()).size)
    }
}
