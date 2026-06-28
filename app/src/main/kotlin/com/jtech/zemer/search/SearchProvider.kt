package com.jtech.zemer.search

/**
 * Which engine backs the online search screen.
 *
 * - [ZEMER] — the whitelist-scoped, Hebrew-aware engine at search.zemer.io (the default). Results are
 *   already content-filtered server-side, so the app does not re-run the local whitelist filter on them.
 * - [YOUTUBE] — the upstream YouTube Music search path, with the local whitelist filter applied on top.
 *
 * Persisted as its [name] under `searchProvider` (see `SearchProviderKey`).
 */
enum class SearchProvider {
    ZEMER,
    YOUTUBE,
}
