package com.jtech.zemer.ui.component

import androidx.annotation.DrawableRes
import com.jtech.zemer.R

/**
 * The per-genre motif drawable (owner direction, 2026-07-30 voice notes): every genre carries a
 * small monochrome icon — "microscopic guitars on a nice card" — tinted with the ONE theme accent,
 * never colored. Vector drawables in `res/drawable` (the repo icon convention — Material Symbols
 * outlined, 24dp, `genre_` prefixed), referenced via `painterResource`. Keyed off the server SLUG
 * (the stable contract; labels change freely). Unknown/new slugs — and the editorially hidden ones
 * (see HIDDEN_GENRE_SLUGS) reachable only by deep link — fall back to the plain music note, so a
 * server addition renders sanely before this map learns it. Slug vocabulary: zemer-app-genres.md §7.
 */
@DrawableRes
internal fun genreIcon(slug: String): Int = when (slug) {
    // Styles
    "nigunim" -> R.drawable.genre_music_note
    "acapella" -> R.drawable.genre_mic
    "chazzanus" -> R.drawable.genre_chazzan
    "instrumental" -> R.drawable.genre_piano
    "dance" -> R.drawable.genre_celebration
    "electronic" -> R.drawable.genre_graphic_eq
    "calm" -> R.drawable.genre_self_improvement
    "wedding" -> R.drawable.genre_diamond
    "march" -> R.drawable.genre_flag
    "yiddish" -> R.drawable.genre_alef
    "english" -> R.drawable.genre_abc
    "israeli" -> R.drawable.genre_star
    "mizrachi" -> R.drawable.genre_wb_sunny
    "yemenite" -> R.drawable.genre_landscape
    // Occasions
    "purim" -> R.drawable.genre_theater_comedy
    "pesach" -> R.drawable.genre_wine_bar
    "chanukah" -> R.drawable.genre_menorah
    "yamim-noraim" -> R.drawable.genre_campaign
    "succos" -> R.drawable.genre_sukkah
    "shavuos-simchas-torah" -> R.drawable.genre_menu_book
    "lag-baomer" -> R.drawable.genre_local_fire_department
    "tu-bishvat" -> R.drawable.genre_park
    "three-weeks" -> R.drawable.genre_heart_broken
    "rosh-chodesh" -> R.drawable.genre_dark_mode
    "shabbos" -> R.drawable.genre_dinner_dining
    "melave-malka" -> R.drawable.genre_nightlife
    else -> R.drawable.genre_music_note
}

/**
 * The per-genre motif for PODCAST genres (server vocab: zemer-app-podcasts-request.md §genres note) —
 * the podcast twin of [genreIcon]. Podcast topics (gemara/mussar/halacha/…) don't match the music
 * motifs, so each slug maps to its own distinct drawable from the shared set: a book for study, a
 * shield for halacha, a flame for chizuk, and so on — never the plain note for everything. Keyed off
 * the stable slug; an unknown/new slug falls back to the note so a server addition still renders.
 *
 * NOTE: these are INTERIM mappings to existing (mostly music) drawables — the app has no bespoke
 * podcast-topic icon set yet. They are placeholders to be replaced with dedicated per-genre art;
 * a few are approximations (machshava→compass, women→account, tefilla→sun). Slugs are the contract,
 * so swapping the drawables later touches only this map.
 */
@DrawableRes
internal fun podcastGenreIcon(slug: String): Int = when (slug) {
    "gemara" -> R.drawable.genre_menu_book
    "parsha" -> R.drawable.genre_parsha
    "chassidus" -> R.drawable.genre_chassidus
    "mussar" -> R.drawable.genre_self_improvement
    "halacha" -> R.drawable.security
    "machshava" -> R.drawable.genre_machshava
    "tefilla" -> R.drawable.genre_wb_sunny
    "stories" -> R.drawable.genre_theater_comedy
    "history" -> R.drawable.history
    "kiruv" -> R.drawable.subscribe
    "family" -> R.drawable.genre_dinner_dining
    "parnassah" -> R.drawable.genre_diamond
    "health" -> R.drawable.favorite
    "news" -> R.drawable.genre_campaign
    "people" -> R.drawable.person
    "music" -> R.drawable.genre_music_note
    "chizuk" -> R.drawable.genre_local_fire_department
    "shiur", "shiurim" -> R.drawable.genre_shiurim
    "moadim" -> R.drawable.genre_celebration
    "women" -> R.drawable.account
    "marriage" -> R.drawable.genre_rings
    // Mental Health reuses the self-improvement/wellness motif (shared with mussar); a bespoke icon can
    // replace this later without touching callers.
    "mentalhealth" -> R.drawable.genre_self_improvement
    "comedy" -> R.drawable.genre_theater_comedy
    else -> R.drawable.genre_music_note
}
