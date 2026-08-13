# User playlist sharing (issue #176)

Share a LOCAL playlist as an unguessable server link that opens in-app for the recipient, with
live updates, "Save a copy", and an operator-featured Home row. Server side is the
`search.zemer.io` `/user-playlist` family; contracts live in the handoff docs (the share thread
and `zemer-app-user-playlists-home-row-request.md`).

## The share flow (`ui/menu/ShareUserPlaylist.kt`)

- Share is offered on local playlists (menu + in-screen button). The dialog asks for a MANDATORY
  sharer display name ("Shared by <name>" on the receiver's screen), remembered device-wide in
  `UserPlaylistSharedByKey` and prefilled on every later share; the dialog also discloses that
  great playlists may be featured in the app.
- First share POSTs `/user-playlist` and stores the returned share id + owner token; every later
  share of the same playlist PUTs the SAME id/URL - re-tapping Share never mints a second link. A
  403/404 on update (token rejected / taken down) clears credentials and falls through to a fresh
  mint. `dropped > 0` (non-corpus members) gets an honest toast; 429 is "try again later", never a
  retry loop.
- The share/import work runs on its own surviving scope: the dialog's OK dismisses the
  composition, which would cancel an in-flight request mid-share.

## Credentials are schema-free (`search/ShareCredentialStore`)

One JSON map in DataStore (`playlistId -> shareId/ownerToken/syncedHash/sharedBy`) - deliberately
NO Room migration (an amended same-version schema crashed Room; the identity-hash trap). DataStore
edits are serialized, so credential read-modify-writes cannot interleave.

## Live updates (`search/SharedPlaylistAutoUpdater`)

One Room flow over playlists + ordered members (re-emits on rename and map changes), debounced,
PUTs any playlist whose fingerprint (`sharedPlaylistFingerprint`) differs from the synced hash.
Transient failures wait for the next edit/app start; gone shares clear their credentials; empty
playlists are never pushed. An orphan sweep withdraws credentials whose playlist no longer exists
(deleted playlist, logout wiping synced rows).

## Receiving (`ui/screens/UserPlaylistScreen.kt`)

`user_playlist/{shareId}` route + the `search.zemer.io/user_playlist/<id>` deep link
(assetlinks-verified). Tracks are corpus-validated at create and filtered under the RECEIVER's
content flags + blocked-ids at serve; `blockVideos` gets a client backstop over the per-track
`isVideo`. Plays tag `PlaySource.shared(shareId)`. Unshare (link-off, confirm dialog) 404s the
link everywhere. **Save a copy** (`ImportPlaylistDialog`) awaits the song inserts before mapping
(FK safety) and creates the playlist via `importedPlaylistEntity` - bookmarked + editable, because
the library queries filter `WHERE bookmarkedAt IS NOT NULL` (an unbookmarked import saved
invisibly; unit-tested).

## The featured Home row ("Zemer User Playlists")

`/home-rows` carries an additive `userPlaylists` array - operator-featured shares only (the
feature toggle lives in the tailnet admin dash; featuring IS the moderation gate for the free-text
title/name, and a rename past the approved snapshot auto-unfeatures server-side). The app maps
them to `PlaylistItem` cards (`ZemerFeaturedUserPlaylist.toPlaylistItem`, ids are SHARE ids),
renders the row under Community playlists with a see-all page, and routes every tap to
`user_playlist/<id>` - the generic playlist click/menu paths must never see these ids. Absent or
empty array = hidden row (the standard fail-soft contract). Server floor: entries under 3 visible
tracks are omitted per viewer.

## Rules that must not regress

- Share ids never enter online-playlist code paths (`online_playlist/...`, save-to-library,
  playlist menus).
- The display name is mandatory at the dialog and always persisted; anonymity is not an option.
- Credentials stay schema-free; nothing about sharing touches the Room schema.
- Featured cards are server-screened content - the app adds no re-screening beyond blocked-ids.
