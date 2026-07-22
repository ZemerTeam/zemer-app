package com.jtech.zemer.playback

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import androidx.media3.common.util.UnstableApi
import com.jtech.zemer.constants.EqualizerBandsKey
import com.jtech.zemer.constants.EqualizerEnabledKey
import com.jtech.zemer.constants.EqualizerPresetKey
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.get
import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.min
import kotlin.math.sqrt

data class EqualizerState(
    val enabled: Boolean,
    val preset: String,
    val bands: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EqualizerState
        if (enabled != other.enabled) return false
        if (preset != other.preset) return false
        return bands.contentEquals(other.bands)
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + preset.hashCode()
        result = 31 * result + bands.contentHashCode()
        return result
    }
}

/**
 * Combined audio-effects engine for Zemer: a [Visualizer]-backed spectrum/level source (used by the
 * waveform seekbar and the animated particles) plus an [Equalizer] controller with named presets and
 * a 10-band device-agnostic layout.
 *
 * Both effects are attached to the active ExoPlayer audio session, so they stay valid across the
 * crossfade player swaps (the service re-attaches to the new active session).
 */
@UnstableApi
class AudioEffectsEngine(
    private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val SPECTRUM_BARS = 48

        /** Standard ISO 10-band center frequencies (Hz), device-agnostic EQ model. */
        val EQ_BAND_FREQS = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        private val PRESETS: Map<String, FloatArray> = mapOf(
            "flat" to FloatArray(10) { 0f },
            "acoustic" to floatArrayOf(-1f, 1f, 2f, 3f, 1f, -1f, -1f, -1f, 0f, 1f),
            "bass" to floatArrayOf(6f, 5f, 4f, 2f, 0f, -1f, -2f, -2f, -1f, 0f),
            "treble" to floatArrayOf(-2f, -1f, 0f, 1f, 2f, 3f, 4f, 5f, 6f, 6f),
            "vocal" to floatArrayOf(-2f, -1f, 1f, 3f, 4f, 4f, 3f, 1f, 0f, -1f),
            "rock" to floatArrayOf(4f, 3f, -1f, -2f, -1f, 1f, 3f, 4f, 4f, 3f),
            "pop" to floatArrayOf(-1f, 2f, 4f, 4f, 1f, -1f, -1f, 0f, 2f, 3f),
            "jazz" to floatArrayOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f),
            "classical" to floatArrayOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f),
            "electronic" to floatArrayOf(5f, 4f, 1f, 0f, -1f, 1f, 2f, 3f, 4f, 5f),
        )

        fun presetNames(): List<String> = PRESETS.keys.toList()
        fun presetGains(name: String): FloatArray = PRESETS[name]?.copyOf() ?: FloatArray(10) { 0f }
    }

    private var visualizer: Visualizer? = null
    private var equalizer: Equalizer? = null
    private var attachedSessionId = -1

    private val _spectrum = MutableStateFlow(FloatArray(SPECTRUM_BARS) { 0f })
    val spectrum: StateFlow<FloatArray> = _spectrum.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _eqPreset = MutableStateFlow("flat")
    val eqPreset: StateFlow<String> = _eqPreset.asStateFlow()

    private val _eqBands = MutableStateFlow(FloatArray(10) { 0f })
    val eqBands: StateFlow<FloatArray> = _eqBands.asStateFlow()

    private val _equalizerState = MutableStateFlow(
        EqualizerState(false, "flat", FloatArray(10) { 0f }),
    )
    val equalizerState: StateFlow<EqualizerState> = _equalizerState.asStateFlow()

    private fun emitEqualizerState() {
        _equalizerState.value = EqualizerState(eqEnabled, _eqPreset.value, _eqBands.value.copyOf())
    }

    private var eqEnabled = false

    fun attach(sessionId: Int) {
        if (sessionId <= 0) return
        if (attachedSessionId == sessionId && (visualizer != null || equalizer != null)) return
        attachedSessionId = sessionId

        setupVisualizer(sessionId)
        setupEqualizer(sessionId)
    }

    private fun setupVisualizer(sessionId: Int) {
        try {
            if (visualizer == null) {
                visualizer = Visualizer(sessionId).apply {
                    captureSize = 1024
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                v: Visualizer?,
                                waveform: ByteArray?,
                                samplingRate: Int,
                            ) {
                                if (waveform == null) return
                                var peak = 0f
                                for (b in waveform) {
                                    val a = kotlin.math.abs(b - 128) / 128f
                                    if (a > peak) peak = a
                                }
                                val smoothed = _amplitude.value + (peak - _amplitude.value) * 0.35f
                                _amplitude.value = smoothed.coerceIn(0f, 1f)
                            }

                            override fun onFftDataCapture(
                                v: Visualizer?,
                                fft: ByteArray?,
                                samplingRate: Int,
                            ) {
                                if (fft == null) return
                                updateSpectrum(fft)
                            }
                        },
                        min(Visualizer.getMaxCaptureRate(), 20000),
                        true,
                        true,
                    )
                    enabled = true
                }
            }
        } catch (e: Exception) {
            reportException(e)
        }
    }

    private fun updateSpectrum(fft: ByteArray) {
        val n = fft.size / 2
        val magnitudes = FloatArray(n)
        for (i in 1 until n) {
            val re = fft[2 * i].toFloat()
            val im = fft[2 * i + 1].toFloat()
            magnitudes[i] = sqrt(re * re + im * im) / 128f
        }
        val bars = FloatArray(SPECTRUM_BARS)
        val per = n.toFloat() / SPECTRUM_BARS
        for (b in 0 until SPECTRUM_BARS) {
            val start = (b * per).toInt().coerceAtLeast(1)
            val end = ((b + 1) * per).toInt().coerceAtMost(n)
            var max = 0f
            for (i in start until end) if (magnitudes[i] > max) max = magnitudes[i]
            val prev = _spectrum.value[b]
            bars[b] = (prev + (max.coerceIn(0f, 1f) - prev) * 0.5f)
        }
        _spectrum.value = bars
    }

    private fun setupEqualizer(sessionId: Int) {
        try {
            if (equalizer == null) {
                equalizer = Equalizer(0, sessionId)
            }
            applyEqualizerState()
        } catch (e: Exception) {
            reportException(e)
            equalizer = null
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        eqEnabled = enabled
        applyEqualizerState()
        emitEqualizerState()
        persist()
    }

    fun setPreset(name: String) {
        val gains = presetGains(name)
        _eqPreset.value = name
        _eqBands.value = gains.copyOf()
        applyEqualizerState()
        emitEqualizerState()
        persist()
    }

    fun setBand(index: Int, dB: Float) {
        if (index < 0 || index >= 10) return
        val bands = _eqBands.value.copyOf()
        bands[index] = dB
        _eqBands.value = bands
        _eqPreset.value = "custom"
        applyEqualizerState()
        emitEqualizerState()
    }

    /** Persist the current EQ configuration (call when the user finishes editing a band). */
    fun commitEqualizer() {
        persist()
    }

    fun presetsList(): List<String> = presetNames()

    private fun applyEqualizerState() {
        val eq = equalizer ?: return
        try {
            if (!eqEnabled) {
                eq.enabled = false
                return
            }
            eq.enabled = true
            val bands = _eqBands.value
            val numBands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minLevel = range[0].toInt()
            val maxRange = range[1].toInt()
            val centerFreqs = IntArray(numBands) { eq.getCenterFreq(it.toShort()).toInt() }
            for (i in 0 until numBands) {
                val nearest = nearestStandardBand(centerFreqs[i])
                val level = (bands[nearest] * 100).toInt().coerceIn(minLevel, maxRange)
                eq.setBandLevel(i.toShort(), level.toShort())
            }
        } catch (e: Exception) {
            reportException(e)
        }
    }

    private fun nearestStandardBand(freq: Int): Int {
        var best = 0
        var bestDiff = Int.MAX_VALUE
        for (i in EQ_BAND_FREQS.indices) {
            val d = kotlin.math.abs(EQ_BAND_FREQS[i] - freq)
            if (d < bestDiff) {
                bestDiff = d
                best = i
            }
        }
        return best
    }

    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {
        }
        visualizer = null
        try {
            equalizer?.release()
        } catch (_: Exception) {
        }
        equalizer = null
        attachedSessionId = -1
        scope.cancel()
    }

    /** Load EQ configuration from DataStore and apply it. Called once on service start. */
    fun initFromStore() {
        eqEnabled = context.dataStore.get(EqualizerEnabledKey, false)
        val preset = context.dataStore.get(EqualizerPresetKey, "flat") ?: "flat"
        val stored = context.dataStore.get(EqualizerBandsKey, "")
        val bands = if (stored.isNullOrEmpty()) {
            presetGains(preset)
        } else {
            try {
                val arr = Json.parseToJsonElement(stored) as JsonArray
                FloatArray(arr.size) { (arr[it] as JsonPrimitive).content.toFloat() }
            } catch (_: Exception) {
                presetGains(preset)
            }
        }
        _eqPreset.value = preset
        _eqBands.value = bands
        emitEqualizerState()
    }

    fun persist() {
        val arr = JsonArray(_eqBands.value.map { JsonPrimitive(it) })
        scope.launch {
            try {
                context.dataStore.edit {
                    it[EqualizerBandsKey] = arr.toString()
                    it[EqualizerPresetKey] = _eqPreset.value
                    it[EqualizerEnabledKey] = eqEnabled
                }
            } catch (e: Exception) {
                reportException(e)
            }
        }
    }
}
