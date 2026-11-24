package com.example.hardware

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import com.example.model.AudioControlState
import com.example.model.EqBandState
import com.example.model.SoundProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

/**
 * Real Android AudioEffect controller backed by `android.media.audiofx.Equalizer`
 * and `android.media.audiofx.BassBoost`. Never fakes EQ support if the device AudioEffect HAL
 * rejects initialization.
 */
class AudioEffectsController {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private val _state = MutableStateFlow(AudioControlState())
    val state: StateFlow<AudioControlState> = _state.asStateFlow()

    val builtInProfiles: List<SoundProfile> = listOf(
        SoundProfile(
            id = "balanced",
            name = "Balanced Studio",
            description = "Flat reference curve tuned for neutral clarity",
            bandOffsetsNormalized = listOf(0.50f, 0.50f, 0.50f, 0.50f, 0.50f),
            bassBoostStrength = 0,
            isBuiltIn = true,
        ),
        SoundProfile(
            id = "nord_bass",
            name = "Nord Deep Bass",
            description = "Warm sub-bass punch with crisp high-hat presence",
            bandOffsetsNormalized = listOf(0.82f, 0.68f, 0.46f, 0.56f, 0.64f),
            bassBoostStrength = 680,
            isBuiltIn = true,
        ),
        SoundProfile(
            id = "vocal_clarity",
            name = "Vocal Clarity",
            description = "Forward midrange presence for podcasts & vocals",
            bandOffsetsNormalized = listOf(0.40f, 0.48f, 0.72f, 0.68f, 0.55f),
            bassBoostStrength = 150,
            isBuiltIn = true,
        ),
        SoundProfile(
            id = "acoustic_stage",
            name = "Acoustic Stage",
            description = "Natural resonance and airy upper harmonics",
            bandOffsetsNormalized = listOf(0.58f, 0.52f, 0.56f, 0.66f, 0.76f),
            bassBoostStrength = 250,
            isBuiltIn = true,
        ),
        SoundProfile(
            id = "crisp_treble",
            name = "Crisp Detail",
            description = "Elevated upper-mid and treble definition",
            bandOffsetsNormalized = listOf(0.44f, 0.46f, 0.58f, 0.74f, 0.85f),
            bassBoostStrength = 100,
            isBuiltIn = true,
        ),
    )

    fun initialize(customProfiles: List<SoundProfile>, activeProfileId: String) {
        release()
        var eqSupported = false
        var eqMessage = "System Equalizer active on AudioEffect HAL"
        val bandList = mutableListOf<EqBandState>()
        val hwPresets = mutableListOf<String>()

        runCatching {
            val eq = Equalizer(0, 0).apply { enabled = true }
            equalizer = eq
            val numBands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minMb = range.getOrNull(0)?.toInt() ?: -1500
            val maxMb = range.getOrNull(1)?.toInt() ?: 1500

            for (i in 0 until numBands) {
                val bandIdx = i.toShort()
                val freqHz = (eq.getCenterFreq(bandIdx) / 1000).coerceAtLeast(20)
                val levelMb = eq.getBandLevel(bandIdx).toInt()
                bandList.add(
                    EqBandState(
                        index = i,
                        centerFreqHz = freqHz,
                        minLevelMb = minMb,
                        maxLevelMb = maxMb,
                        currentLevelMb = levelMb,
                    ),
                )
            }

            val presetCount = eq.numberOfPresets.toInt()
            for (p in 0 until presetCount) {
                hwPresets.add(eq.getPresetName(p.toShort()))
            }
            eqSupported = numBands >= 5
            if (numBands in 1..4) {
                eqSupported = true
                eqMessage = "Hardware AudioFX exposes $numBands bands"
            }
        }.onFailure { err ->
            eqSupported = false
            eqMessage = "Android AudioEffect HAL unavailable (${err.javaClass.simpleName}): ${err.message ?: "unsupported on this build"}"
        }

        var bassSupported = false
        var bassStrength = 0
        runCatching {
            val bb = BassBoost(0, 0).apply { enabled = true }
            bassBoost = bb
            bassSupported = bb.strengthSupported
            if (bassSupported) {
                bassStrength = bb.roundedStrength.toInt().coerceIn(0, 1000)
            }
        }.onFailure {
            bassSupported = false
        }

        val allProfiles = builtInProfiles + customProfiles
        _state.update { current ->
            current.copy(
                isEqSupported = eqSupported,
                eqStatusMessage = eqMessage,
                bands = bandList,
                isBassBoostSupported = bassSupported,
                bassBoostStrength = bassStrength,
                profiles = allProfiles,
                hardwarePresetNames = hwPresets,
                activeProfileId = activeProfileId,
            )
        }

        allProfiles.firstOrNull { it.id == activeProfileId }?.let { profile ->
            if (eqSupported || bassSupported) {
                applyProfile(profile)
            }
        }
    }

    fun updateBandNormalized(bandIndex: Int, normalized: Float) {
        val clamped = normalized.coerceIn(0f, 1f)
        val eq = equalizer ?: return
        val currentBand = _state.value.bands.getOrNull(bandIndex) ?: return
        val targetMb = (currentBand.minLevelMb + clamped * (currentBand.maxLevelMb - currentBand.minLevelMb))
            .roundToInt()
            .coerceIn(currentBand.minLevelMb, currentBand.maxLevelMb)

        runCatching {
            eq.enabled = true
            eq.setBandLevel(bandIndex.toShort(), targetMb.toShort())
            _state.update { current ->
                current.copy(
                    bands = current.bands.map {
                        if (it.index == bandIndex) it.copy(currentLevelMb = targetMb) else it
                    },
                    activeProfileId = "custom_unsaved",
                )
            }
        }.onFailure { err ->
            _state.update { it.copy(eqStatusMessage = "EQ band write rejected by HAL: ${err.message}") }
        }
    }

    fun setBassBoostStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        val bb = bassBoost ?: return
        if (!bb.strengthSupported) return
        runCatching {
            bb.enabled = clamped > 0
            bb.setStrength(clamped.toShort())
            _state.update { it.copy(bassBoostStrength = clamped, activeProfileId = "custom_unsaved") }
        }
    }

    fun applyProfile(profile: SoundProfile) {
        val currentBands = _state.value.bands
        val eq = equalizer
        val updatedBands = if (eq != null && currentBands.isNotEmpty()) {
            currentBands.mapIndexed { idx, band ->
                val norm = interpolateProfileNormalized(profile.bandOffsetsNormalized, idx, currentBands.size)
                val targetMb = (band.minLevelMb + norm * (band.maxLevelMb - band.minLevelMb))
                    .roundToInt()
                    .coerceIn(band.minLevelMb, band.maxLevelMb)
                runCatching {
                    eq.enabled = true
                    eq.setBandLevel(band.index.toShort(), targetMb.toShort())
                }
                band.copy(currentLevelMb = targetMb)
            }
        } else {
            currentBands
        }

        val bb = bassBoost
        var appliedBass = _state.value.bassBoostStrength
        if (bb != null && bb.strengthSupported) {
            val targetBass = profile.bassBoostStrength.coerceIn(0, 1000)
            runCatching {
                bb.enabled = targetBass > 0
                bb.setStrength(targetBass.toShort())
                appliedBass = targetBass
            }
        }

        _state.update { current ->
            current.copy(
                bands = updatedBands,
                bassBoostStrength = appliedBass,
                activeProfileId = profile.id,
            )
        }
    }

    fun updateProfilesList(customProfiles: List<SoundProfile>) {
        _state.update { current ->
            current.copy(profiles = builtInProfiles + customProfiles)
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
    }

    private fun interpolateProfileNormalized(offsets: List<Float>, bandIndex: Int, totalBands: Int): Float {
        if (offsets.isEmpty()) return 0.5f
        if (offsets.size == totalBands) return offsets[bandIndex].coerceIn(0f, 1f)
        if (totalBands <= 1) return offsets.first().coerceIn(0f, 1f)
        val pos = bandIndex.toFloat() / (totalBands - 1) * (offsets.size - 1)
        val lo = pos.toInt().coerceIn(0, offsets.lastIndex)
        val hi = (lo + 1).coerceIn(0, offsets.lastIndex)
        val frac = pos - lo
        return (offsets[lo] * (1f - frac) + offsets[hi] * frac).coerceIn(0f, 1f)
    }
}
