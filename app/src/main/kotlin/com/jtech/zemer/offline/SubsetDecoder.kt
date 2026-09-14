package com.jtech.zemer.offline

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Decodes the gzipped subset shards into a [SubsetCorpus]. Each shard is a bare JSON array (positional
 * rows) or object — no per-shard wrapper (the version lives only in the manifest). The positional
 * layouts and packed-flag bits below are pinned to `zemer-search/index/build-subset.mjs`; a change
 * there must be mirrored here (guarded by [SubsetDecoderTest] against real sample rows).
 */
object SubsetDecoder {

    private val json = Json { ignoreUnknownKeys = true }

    /** Loads the committed snapshot from [store] into a corpus, or null if none / incomplete / corrupt. */
    fun loadCorpus(store: SubsetStore): SubsetCorpus? {
        val manifest = store.localManifest() ?: return null
        return try {
            val artists = ArrayList<SubArtist>()
            val tracks = ArrayList<SubTrack>()
            val albums = ArrayList<SubAlbum>()
            val albumTracks = ArrayList<SubAlbumTrack>()
            val playlists = ArrayList<SubArtistPlaylist>()
            val community = ArrayList<SubCommunity>()
            val communityTracks = ArrayList<SubCommunityTrack>()
            val homeRank = ArrayList<SubHomeRank>()
            val zemerPlaylists = ArrayList<SubZemerPlaylist>()
            val zemerItems = ArrayList<SubZemerItem>()
            val podcastChannels = ArrayList<SubPodcastChannel>()
            val podcasts = ArrayList<SubPodcastShow>()
            val podcastEpisodes = ArrayList<SubPodcastEpisode>()
            var blocked = SubBlocked(emptySet(), emptySet())
            var synonymGroups: List<List<String>> = emptyList()
            var genreCatalog: SubGenreCatalog? = null
            var lyricsFlags: Map<String, Int> = emptyMap()
            var realVideos: Set<String> = emptySet()
            val radioRows = ArrayList<SubRadioRow>()

            for (shard in manifest.shards) {
                val bytes = store.shardBytes(shard.name) ?: return null
                // Belt-and-suspenders over the staged sync: a shard whose bytes don't match the
                // committed manifest (an interrupted legacy sync, disk corruption) must read as
                // "no snapshot", never decode as a silently mixed-version corpus.
                if (subsetShardHash(bytes) != shard.hash) {
                    Timber.w("Subset shard %s hash mismatch on read — snapshot unusable", shard.name)
                    return null
                }
                val text = gunzip(bytes)
                when {
                    shard.name == "artists" -> artists += decodeArtists(text)
                    shard.name.startsWith("albumtracks-") -> albumTracks += decodeAlbumTracks(text)
                    shard.name.startsWith("tracks-") -> tracks += decodeTracks(text)
                    shard.name.startsWith("albums-") -> albums += decodeAlbums(text)
                    shard.name == "playlists" -> playlists += decodeArtistPlaylists(text)
                    shard.name == "community" -> community += decodeCommunity(text)
                    shard.name == "communitytracks" -> communityTracks += decodeCommunityTracks(text)
                    shard.name == "homerank" -> homeRank += decodeHomeRank(text)
                    shard.name == "zemer" -> decodeZemer(text).let { zemerPlaylists += it.first; zemerItems += it.second }
                    shard.name == "podcastchannels" -> podcastChannels += decodePodcastChannels(text)
                    shard.name == "podcasts" -> podcasts += decodePodcastShows(text)
                    shard.name.startsWith("podcastepisodes-") -> podcastEpisodes += decodePodcastEpisodes(text)
                    shard.name == "blocked" -> blocked = decodeBlocked(text)
                    shard.name == "synonyms" -> synonymGroups = decodeSynonymGroups(text)
                    shard.name == "genrecatalog" -> genreCatalog = decodeGenreCatalog(text)
                    shard.name == "lyricsflags" -> lyricsFlags = decodeLyricsFlags(text)
                    shard.name == "realvideos" -> realVideos = decodeRealVideos(text)
                    shard.name.startsWith("radio-") -> radioRows += decodeRadioRows(text)
                    // Unknown shard → ignored for forward compatibility.
                }
            }
            SubsetCorpus(
                artists, tracks, albums, albumTracks, playlists,
                community, communityTracks, homeRank, zemerPlaylists, zemerItems, blocked,
                podcastChannels, podcasts, podcastEpisodes,
                synonymGroups, genreCatalog, lyricsFlags, realVideos, radioRows,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Throwable, not Exception: decoding materializes tens of MB (bytes + gunzipped text +
            // JSON tree), so OutOfMemoryError is a REAL outcome on a low-RAM device — it must degrade
            // to "no snapshot" (the caller rethrows the original network error), never crash the app
            // on the path built to degrade gracefully. The half-built local lists are unreferenced
            // after this frame, so the memory is immediately reclaimable.
            Timber.w(e, "Subset decode failed")
            null
        }
    }

    // --- per-shard decoders (positions pinned to build-subset.mjs) ---

    fun decodeArtists(text: String): List<SubArtist> = rows(text).map { r ->
        val flags = r[3].asInt()
        SubArtist(
            id = r[0].asString(),
            name = r[1].asString(),
            thumbnail = r[2].asStringOrNull(),
            isFemale = flags and 1 != 0,
            isChasid = flags and 2 != 0,
            isKidZone = flags and 4 != 0,
        )
    }

    fun decodeTracks(text: String): List<SubTrack> = rows(text).map { r ->
        val flags = r[3].asInt()
        SubTrack(
            videoId = r[0].asString(),
            title = r[1].asString(),
            artistId = r[2].asString(),
            isVideo = flags and 1 != 0,
            durationSec = r[4].asIntOrNull(),
            playCount = r[5].asLongOrNull(),
            uploadDate = r[6].asStringOrNull(),
        )
    }

    fun decodeAlbums(text: String): List<SubAlbum> = rows(text).map { r ->
        SubAlbum(
            id = r[0].asString(),
            playlistId = r[1].asStringOrNull(),
            title = r[2].asString(),
            artistId = r[3].asString(),
            type = r[4].asString(),
            year = r[5].asIntOrNull(),
            thumbnail = r[6].asStringOrNull(),
            uploadDate = r[7].asStringOrNull(),
        )
    }

    fun decodeAlbumTracks(text: String): List<SubAlbumTrack> = rows(text).map { r ->
        SubAlbumTrack(albumId = r[0].asString(), videoId = r[1].asString(), pos = r[2].asInt())
    }

    fun decodeArtistPlaylists(text: String): List<SubArtistPlaylist> = rows(text).map { r ->
        SubArtistPlaylist(id = r[0].asString(), title = r[1].asString(), artistId = r[2].asString(), thumbnail = r[3].asStringOrNull())
    }

    fun decodeCommunity(text: String): List<SubCommunity> = rows(text).map { r ->
        SubCommunity(
            id = r[0].asString(),
            title = r[1].asString(),
            author = r[2].asStringOrNull(),
            thumbnail = r[3].asStringOrNull(),
            total = r[4].asInt(),
            whitelisted = r[5].asInt(),
            viewCount = r[6].asLongOrNull(),
        )
    }

    fun decodeCommunityTracks(text: String): List<SubCommunityTrack> = rows(text).map { r ->
        SubCommunityTrack(playlistId = r[0].asString(), videoId = r[1].asString(), pos = r[2].asInt(), artistId = r[3].asStringOrNull())
    }

    fun decodeHomeRank(text: String): List<SubHomeRank> = json.parseToJsonElement(text).jsonArray.map { e ->
        val o = e.jsonObject
        SubHomeRank(
            row = o["row"].asString(),
            kind = o["kind"].asString(),
            refId = o["refId"].asString(),
            artistId = o["artistId"].asStringOrNull(),
            pos = o["pos"].asInt(),
            score = o["score"].asDoubleOrNull(),
        )
    }

    fun decodeZemer(text: String): Pair<List<SubZemerPlaylist>, List<SubZemerItem>> {
        val o = json.parseToJsonElement(text).jsonObject
        val playlists = (o["playlists"] as? JsonArray).orEmpty().map { e ->
            val p = e.jsonObject
            SubZemerPlaylist(id = p["id"].asString(), title = p["title"].asString(), pos = p["pos"].asInt(), year = p["year"].asIntOrNull())
        }
        val items = (o["items"] as? JsonArray).orEmpty().map { e ->
            val i = e.jsonObject
            SubZemerItem(playlistId = i["playlistId"].asString(), kind = i["kind"].asString(), refId = i["refId"].asString(), pos = i["pos"].asInt())
        }
        return playlists to items
    }

    // Podcast channel row: [ id(UC), name, thumbnail, flags, showCount, episodeCount ].
    // flags is a bitmask: bit0=isFemale, bit1=isKidZone, bit2=isVerified.
    fun decodePodcastChannels(text: String): List<SubPodcastChannel> = rows(text).map { r ->
        val flags = r[3].asInt()
        SubPodcastChannel(
            id = r[0].asString(),
            name = r[1].asString(),
            thumbnail = r[2].asStringOrNull(),
            isFemale = flags and 1 != 0,
            isKidZone = flags and 2 != 0,
            isVerified = flags and 4 != 0,
            showCount = r[4].asIntOrNull() ?: 0,
            episodeCount = r[5].asIntOrNull() ?: 0,
        )
    }

    // Podcast show row: [ id(MPSP), name, author, channelId(UC), thumbnail, episodeCountText, genres,
    // isKidZone ]. `genres` (col 6) is a comma-separated slug string and `isKidZone` (col 7, 0|1) the
    // per-show kid flag — both appended after the podcast client shipped; getOrNull keeps older
    // snapshots (6- or 7-col rows) decoding cleanly with empty/false defaults.
    fun decodePodcastShows(text: String): List<SubPodcastShow> = rows(text).map { r ->
        SubPodcastShow(
            id = r[0].asString(),
            name = r[1].asString(),
            author = r[2].asStringOrNull(),
            channelId = r[3].asStringOrNull(),
            thumbnail = r[4].asStringOrNull(),
            episodeCountText = r[5].asStringOrNull(),
            genres = r.getOrNull(6).asStringOrNull()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            isKidZone = (r.getOrNull(7).asIntOrNull() ?: 0) != 0,
        )
    }

    // Podcast episode row: [ videoId, showId(MPSP), title, thumbnail, durationSec, publishedAt ].
    fun decodePodcastEpisodes(text: String): List<SubPodcastEpisode> = rows(text).map { r ->
        SubPodcastEpisode(
            videoId = r[0].asString(),
            showId = r[1].asString(),
            title = r[2].asString(),
            thumbnail = r[3].asStringOrNull(),
            durationSec = r[4].asIntOrNull(),
            publishedAt = r[5].asStringOrNull(),
        )
    }

    fun decodeBlocked(text: String): SubBlocked {
        val o = json.parseToJsonElement(text).jsonObject
        fun ids(key: String) = (o[key] as? JsonArray).orEmpty().mapTo(HashSet()) { it.asString() }
        return SubBlocked(global = ids("global"), female = ids("female"))
    }

    // synonyms shard: [[form, form, ...], ...] — the raw groups of zemer-search/data/synonyms.json,
    // compiled on-device exactly like SubsetSynonyms.compile does for the built-in default table.
    fun decodeSynonymGroups(text: String): List<List<String>> =
        json.parseToJsonElement(text).jsonArray.map { group -> group.jsonArray.map { it.asString() } }

    // genrecatalog shard: { genres:[[slug,title,kind]], kinds:[[id],...], podcastGenres:[[slug,title,kind|null]],
    // podcastKinds:[[id,title]] } — kind rows are single-element arrays (just the id); the display title for a
    // music kind is owned app-side (GenreKind), so only the id is read here.
    fun decodeGenreCatalog(text: String): SubGenreCatalog {
        val o = json.parseToJsonElement(text).jsonObject
        fun entries(key: String) = (o[key] as? JsonArray).orEmpty().map { row ->
            val r = row.jsonArray
            SubGenreEntry(id = r[0].asString(), title = r[1].asString(), kind = r.getOrNull(2).asStringOrNull())
        }
        val kinds = (o["kinds"] as? JsonArray).orEmpty().map { it.jsonArray[0].asString() }
        val podcastKinds = (o["podcastKinds"] as? JsonArray).orEmpty().map { row ->
            val r = row.jsonArray
            r[0].asString() to r[1].asString()
        }
        return SubGenreCatalog(genres = entries("genres"), kinds = kinds, podcastGenres = entries("podcastGenres"), podcastKinds = podcastKinds)
    }

    // lyricsflags shard: [[videoId, bits]] — one row per VERIFIED lyrics row of a whitelisted track.
    fun decodeLyricsFlags(text: String): Map<String, Int> =
        rows(text).associate { r -> r[0].asString() to r[1].asInt() }

    // realvideos shard: [videoId] — the motion-classified real filmed videos among isVideo tracks.
    fun decodeRealVideos(text: String): Set<String> =
        json.parseToJsonElement(text).jsonArray.mapTo(HashSet()) { it.asString() }

    // radio-<n> shard: [[videoId, pop|null, [[id,w],...] lib, [[id,w],...] sess]], hash-bucketed by
    // videoId across 4 shard names — decoded per-shard and concatenated by the caller (loadCorpus).
    fun decodeRadioRows(text: String): List<SubRadioRow> = rows(text).map { r ->
        fun neighbours(idx: Int) = (r.getOrNull(idx) as? JsonArray).orEmpty().map { pair ->
            val p = pair.jsonArray
            p[0].asString() to p[1].asDoubleOrNull()!!
        }
        SubRadioRow(videoId = r[0].asString(), pop = r[1].asDoubleOrNull(), lib = neighbours(2), sess = neighbours(3))
    }

    // --- helpers ---

    private fun rows(text: String): List<JsonArray> = json.parseToJsonElement(text).jsonArray.map { it.jsonArray }

    private fun gunzip(bytes: ByteArray): String =
        GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

    private fun JsonElement?.isNull(): Boolean = this == null || this is JsonNull
    private fun JsonElement?.asString(): String = this!!.jsonPrimitive.content
    private fun JsonElement?.asStringOrNull(): String? = if (isNull()) null else this!!.jsonPrimitive.contentOrNull
    private fun JsonElement?.asInt(): Int = this!!.jsonPrimitive.int
    private fun JsonElement?.asIntOrNull(): Int? = if (isNull()) null else this!!.jsonPrimitive.intOrNull
    private fun JsonElement?.asLongOrNull(): Long? = if (isNull()) null else this!!.jsonPrimitive.longOrNull
    private fun JsonElement?.asDoubleOrNull(): Double? = if (isNull()) null else this!!.jsonPrimitive.doubleOrNull
}
