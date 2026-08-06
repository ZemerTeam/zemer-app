package com.jtech.zemer.ui.screens

/**
 * The Home content-type selector tabs (the top [com.jtech.zemer.ui.component.ChipsRow]). Each tab
 * renders only its own shelves: MUSIC the music feed, PODCASTS the podcast surfaces, RADIO the Zemer
 * Radio stations, VIDEO the featured video-songs. VIDEO is dropped from the selector when videos are
 * blocked (its shelves render nothing then). MUSIC is the default view.
 */
enum class HomeContentTab { MUSIC, PODCASTS, RADIO, VIDEO }
