package com.lmg.vk.engine.automix.observation

import kotlinx.serialization.Serializable

/** Raw native-decoded cloud values. A decoded field is not a planner eligibility claim. */
@Serializable
data class AppleSongAnalysis(
    val id: String,
    val durationInMillis: Double? = null,
    val supportsSmartTransitions: Boolean? = null,
    val isrc: String? = null,
    val genreNames: List<String>? = null,
    val audio: AnalysisResource<AudioAnalysis>? = null,
    val flex: AnalysisResource<FlexAnalysis>? = null,
) {
    val audioAvailability: Availability get() = availability(audio)
    val flexAvailability: Availability get() = availability(flex)

    enum class Availability { ABSENT, LINKAGE_ONLY, RESOLVED }
    private fun availability(resource: AnalysisResource<*>?) = when {
        resource == null -> Availability.ABSENT
        resource.attributes == null -> Availability.LINKAGE_ONLY
        else -> Availability.RESOLVED
    }
}

@Serializable
data class AnalysisResource<T>(val id: String, val attributes: T? = null)
@Serializable
data class Composite<T>(val main: T? = null, val beginning: T? = null, val ending: T? = null)
@Serializable
data class Bpm(
    val main: Double? = null, val beginning: Double? = null, val ending: Double? = null,
    val percentDeviation: Double? = null,
)
@Serializable
data class TonalKey(val tonic: String? = null, val mode: String? = null)
@Serializable
data class Statistics(val value: Double? = null, val range: Double? = null, val peak: Double? = null)
@Serializable
data class TimeRangeMs(val startInMilliseconds: Double? = null, val endInMilliseconds: Double? = null)
@Serializable
data class Beats(val beatsInMilliseconds: List<Double>? = null, val barsInMilliseconds: List<Double>? = null)
@Serializable
data class SampledValues(val value: List<Double>? = null, val samplingFrequency: Double? = null)
@Serializable
data class VocalActivity(
    val startInMilliseconds: Double? = null, val endInMilliseconds: Double? = null,
    val strength: String? = null, val kind: String? = null,
)
@Serializable
data class Fades(val fadeIn: TimeRangeMs? = null, val fadeOut: TimeRangeMs? = null)
@Serializable
data class AudioAnalysis(
    val bpm: Bpm? = null,
    val beats: Beats? = null,
    val key: Composite<TonalKey>? = null,
    val acousticness: Composite<Double>? = null,
    val danceability: Composite<Double>? = null,
    val melodicness: Composite<Double>? = null,
    val energy: Composite<Double>? = null,
    val valence: Composite<Double>? = null,
    val loudness: Composite<Statistics>? = null,
    val loudnessCurve: SampledValues? = null,
    val vocalActivity: List<VocalActivity>? = null,
    val fades: Fades? = null,
    val phrases: List<TimeRangeMs>? = null,
)
@Serializable
data class PivotPoint(
    val timeInSeconds: Double? = null, val fadeToBlack: Double? = null,
    val gainTimeInSeconds: List<Double>? = null, val gainValue: List<Double>? = null,
    val tags: List<String>? = null,
)
@Serializable
data class VideoEvents(val score: List<Double>? = null, val timeInSeconds: List<Double>? = null)
@Serializable
data class FlexAnalysis(
    val entryPoints: List<PivotPoint>? = null, val exitPoints: List<PivotPoint>? = null,
    val videoEvents: VideoEvents? = null,
    val arousal: SampledValues? = null, val valence: SampledValues? = null,
    val visualTempo: SampledValues? = null,
)
