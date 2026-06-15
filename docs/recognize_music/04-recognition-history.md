# 4 · Recognition history (Room v33)

Every successful recognition is recorded so the user can revisit/replay it. History stores the
resolved, whitelisted `SongItem` (never raw Shazam metadata) **plus the resolved artists' ids**, and
is re-checked against the *current* whitelist before it is shown — so a song whose artist was later
removed from the whitelist can't be replayed (see [02](02-whitelist-guarantee.md)).

## Schema — `recognition_history` (DB version 33)

`db/entities/RecognitionHistoryEntity.kt`:

```kotlin
@Entity(
    tableName = "recognition_history",
    indices = [Index("songId"), Index("recognizedAt")],   // de-dup delete + list ordering
)
data class RecognitionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,          // YouTube videoId (whitelisted) — used to play it
    val title: String,
    val artist: String,          // joined artist names (display)
    val thumbnailUrl: String?,
    val artistIds: String = "",  // comma-joined artist browse ids — re-checked against the whitelist
    val recognizedAt: LocalDateTime = LocalDateTime.now(),
)
```

### Migration

Added in **`MusicDatabase` version 32 → 33** as a **purely additive `@AutoMigration(from = 32, to = 33)`**
(a brand-new table; no changes to existing tables). `artistIds` and the two indices are part of that
same fresh-table definition — the table is *born* at v33, so there is no separate column-add step and
**no version beyond 33**. The entity is registered in the `@Database` `entities` list and the schema
is exported to `app/schemas/.../33.json`. This is the safe kind of schema change (CLAUDE.md flags
schema changes as high-risk and requiring human sign-off — this one was explicitly requested and is
additive-only).

## DAO (`db/DatabaseDao.kt`)

```kotlin
@Query("SELECT * FROM recognition_history ORDER BY recognizedAt DESC")
fun recognitionHistory(): Flow<List<RecognitionHistoryEntity>>

@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertRecognitionHistory(entity: RecognitionHistoryEntity): Long

@Query("DELETE FROM recognition_history WHERE songId = :songId")
suspend fun deleteRecognitionHistoryBySong(songId: String)

@Delete
suspend fun deleteRecognitionHistory(entity: RecognitionHistoryEntity)

@Query("DELETE FROM recognition_history")
suspend fun clearRecognitionHistory()
```

## When it's written

Inside the shared bridge `RecognitionResolver.resolveWhitelisted(...)`, immediately after the result
clears both whitelist gates (`recordHistory`). It stores the resolved song's `artistIds`
(`RecognitionHistoryFilter.joinIds`) for the replay re-check, and **de-duplicates by song**
(delete-then-insert) so the list is "most recently recognized, no repeats", newest first. It is
wrapped in `runCatching` so a history failure never breaks recognition. Because it lives in the shared
resolver, **both** the popup and the widget record history through the same path.

## UI

- `viewmodels/RecognitionHistoryViewModel.kt` — exposes `history` and `delete(entry)` / `clearAll()`.
  `history` is the DAO flow **combined with the live whitelist flow** and filtered through
  `RecognitionHistoryFilter.isAllowed` (pure, unit-tested in `RecognitionHistoryFilterTest`), so an
  entry whose artists are no longer whitelisted is dropped the moment the whitelist changes — the one
  place history is exposed, which is what makes replay leak-proof.
- `ui/screens/recognition/RecognitionHistoryScreen.kt` — a `LazyColumn` of rows (thumbnail + title +
  artist), each tappable to **play** (`YouTubeQueue(WatchEndpoint(videoId), database = database)`),
  with a per-row **remove** and a top-bar **clear all** behind a `DefaultDialog` confirm. Rows carry
  the app's `focusBorder()` for D-pad, and the list uses `LocalPlayerAwareWindowInsets`.
- Reached via the **history icon in the popup header** (deep link `…/recognition_history`) and the
  `recognition_history` nav route.
