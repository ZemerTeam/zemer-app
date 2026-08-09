package com.jtech.zemer.constants

/**
 * How the app sources audio bytes for playback. This is the SINGLE switch the playback layer reads to
 * choose between the normal on-device resolution and the server relay; it never changes anything else
 * (search, browse, metadata, content filters all stay on `*.zemer.io` regardless).
 *
 * - [DIRECT] (the default for every normal user): resolve `/player` on-device and stream from
 *   `googlevideo` exactly as before. This mode is untouched by the relay feature.
 * - [RELAY]: a login-less mode for devices whose kosher filter blocks `music.youtube.com` /
 *   `googlevideo.com`. Playback bytes arrive over the whitelisted relay host instead (see RelayStream).
 *   Opt-in only, chosen on the login screen or toggled in Settings.
 */
enum class PlaybackMode {
    DIRECT,
    RELAY,
}
