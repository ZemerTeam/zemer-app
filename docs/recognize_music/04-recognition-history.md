# 4 · Recognition history (Room v33)

Every successful recognition is recorded so the user can revisit/replay it. History stores **only
the resolved, whitelisted `SongItem`** — never raw Shazam metadata — so it can't leak either.

## Schema — `recognition_history` (DB version 33)

`db/entities/RecognitionHistoryEntity.kt`:

```kotlin
@Entity(tableName = "recognition_history")
data class RecognitionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,          // YouTube videoId (whitelisted) — used to play it
    val title: String,
    val artist: String,          // joined artist names
    val thumbnailUrl: String?,
    val recognizedAt: LocalDateTime = LocalDateTime.now(),
)
```

### Migration

Added in **`MusicDatabase` version 32 → 33** as a **purely additive `@AutoMigration(from = 32, to = 33)`**
(a brand-new table; no changes to existing tables). The entity is registered in the `@Database`
`entities` list and the schema is exported to `app/schemas/.../33.json`. This is the safe kind of
schema change (CLAUDE.md flags schema changes as high-risk and requiring human sign-off — this one
was explicitly requested and is additive-only).

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
clears both whitelist gates (`recordHistory`). It **de-duplicates by song** (delete-then-insert) so
the list is "most recently recognized, no repeats", newest first. It is wrapped in `runCatching` so a
history failure never breaks recognition. Because it lives in the shared resolver, **both** the popup
and the widget record history through the same path.

## UI

- `viewmodels/RecognitionHistoryViewModel.kt` — exposes `history` (a `stateIn` of the DAO flow) and
  `delete(entry)` / `clearAll()`.
- `ui/screens/recognition/RecognitionHistoryScreen.kt` — a `LazyColumn` of rows (thumbnail + title +
  artist), each tappable to **play** (`YouTubeQueue(WatchEndpoint(videoId), database = database)`),
  with a per-row **remove** and a top-bar **clear all** behind a `DefaultDialog` confirm. Rows carry
  the app's `focusBorder()` for D-pad, and the list uses `LocalPlayerAwareWindowInsets`.
- Reached via the **history icon in the popup header** (deep link `…/recognition_history`) and the
  `recognition_history` nav route.
