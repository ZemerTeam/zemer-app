package com.jtech.zemer.constants

/**
 * Where playback bytes come from. [DIRECT] resolves on-device from googlevideo (the default, untouched by
 * this feature); [RELAY] is the opt-in login-less mode that streams via the Zemer relay for filtered
 * devices. See the handoff doc / RelayStream for the rationale.
 */
enum class PlaybackMode {
    DIRECT,
    RELAY,
}
