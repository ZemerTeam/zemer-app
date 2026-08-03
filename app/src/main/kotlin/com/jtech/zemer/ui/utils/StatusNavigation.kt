package com.jtech.zemer.ui.utils

/**
 * Route to the full-screen story viewer, opened at a given creator by STABLE id (not a list index,
 * which would remap to the wrong creator after a process-death re-fetch under the recency sort). The
 * viewer resolves the id against the shared creators list. Creator ids are Supabase UUIDs (URL-safe),
 * so no encoding is needed. Pure so it is unit-tested.
 */
fun storyRoute(creatorId: String): String = "story/$creatorId"

/**
 * Route to the local saved-status viewer for one creator (their downloaded statuses, newest-saved
 * first), optionally opened at [startId]. Creator ids are Supabase UUIDs (URL-safe), so no encoding is
 * needed. Pure so it is unit-tested.
 */
fun savedStatusRoute(creatorId: String, startId: String? = null): String =
    "saved_status/$creatorId" + (startId?.let { "?start=$it" } ?: "")
