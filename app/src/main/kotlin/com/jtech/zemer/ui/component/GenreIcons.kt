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
    "chazzanus" -> R.drawable.genre_record_voice_over
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
