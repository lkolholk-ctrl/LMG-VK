package com.lmg.vk.engine.automix.observation

import com.lmg.vk.engine.automix.AppleSongAnalysisParser
import com.lmg.vk.engine.automix.TransitionStyleCatalog
import java.io.InputStream

/** Accessed only inside the pipeline's serialized worker section. */
internal class AppleObservationCalculator(private val openAsset: (String) -> InputStream) {
    private val parser = AppleSongAnalysisParser()
    private var catalog: TransitionStyleCatalog? = null

    fun decode(input: ByteArray, requestedSongId: String): DecodedObservationSong =
        DecodedObservationSong.capture(input, requestedSongId, ::parse)

    private fun parse(input: ByteArray, requestedSongId: String): AppleSongAnalysis =
        when (val parsed = parser.parse(input, requestedSongId)) {
            is AppleSongAnalysisParser.Result.Parsed -> parsed.analysis
            is AppleSongAnalysisParser.Result.Rejected -> throw ObservationFailure(
                if (parsed.reason == AppleSongAnalysisParser.Reason.NATIVE_UNAVAILABLE) {
                    ObservationReason.NATIVE_UNAVAILABLE
                } else {
                    ObservationReason.ANALYSIS_REJECTED
                }, parsed.reason.name,
            )
        }

    fun calculate(outgoing: DecodedObservationSong, incoming: DecodedObservationSong, pair: ObservationPair): MetadataProbeReport {
        val verified = catalog ?: when (val loaded = TransitionStyleCatalog.loadBundled(openAsset)) {
            is TransitionStyleCatalog.LoadResult.Loaded -> loaded.catalog.also { catalog = it }
            is TransitionStyleCatalog.LoadResult.Rejected -> throw ObservationFailure(
                if (loaded.reason == TransitionStyleCatalog.Reason.NATIVE_UNAVAILABLE) {
                    ObservationReason.NATIVE_UNAVAILABLE
                } else {
                    ObservationReason.CATALOG_REJECTED
                }, loaded.reason.name,
            )
        }
        return MetadataProbe.calculate(project(outgoing.analysis, pair.outgoing.durationMs),
            project(incoming.analysis, pair.incoming.durationMs), verified.sha256)
            .copy(preparation = outgoing.prepareWith(incoming, pair))
    }

    private fun project(analysis: AppleSongAnalysis, durationMs: Long?): MetadataProbeTrack {
        val bpm = analysis.audio?.attributes?.bpm
        return MetadataProbeTrack(
            analysis.durationInMillis, durationMs, bpm?.main, bpm?.beginning, bpm?.ending,
            when (analysis.audioAvailability) {
                AppleSongAnalysis.Availability.ABSENT -> 0
                AppleSongAnalysis.Availability.LINKAGE_ONLY -> 1
                AppleSongAnalysis.Availability.RESOLVED -> 2
            },
            when (analysis.supportsSmartTransitions) { null -> 0; false -> 1; true -> 2 },
        )
    }
}
