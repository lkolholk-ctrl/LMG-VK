# Offline audio and artwork

Single-track and playlist downloads share unique WorkManager jobs keyed by the SHA-256 of the track ID. At most two transfers execute together. Jobs survive process recreation, require connectivity, support actual cancellation, refresh failed signed audio URLs, and retry transient failures. Force-stopping Android apps still prevents background work until the app is opened again.

Audio is streamed to private staging files. HTTP errors, textual playlists returned as audio, empty or incomplete transfers, and damaged MPEG-TS packets are rejected. HLS MPEG audio is extracted without transcoding; AAC is remuxed into M4A with Android MediaExtractor/MediaMuxer. Tags and original cover bytes are embedded transactionally before exporting via MediaStore. Export failure retains the private file. Existing downloaded audio is edited through a staging copy.

Apple/iTunes artwork is preferred, with a real source cover as fallback. Audio remains usable if artwork download fails. Worker retries can complete missing assets without downloading the song again. After exhausted retries the UI reports that artwork is incomplete. No cover or motion is invented when the catalog has none.

Motion files live in `filesDir/offline_motion`, outside evictable cache storage. A complete finite HLS rendition (or MP4) is downloaded before publishing its manifest. Playback from this store has no network upstream and no metadata TTL. Identical clips can be shared; deleting one track preserves other owners. Deleting all downloads clears persistent motion media too. The download reserves free disk space and cleans new partial cache resources on failure.

Playback prefers the registered local MediaStore/private audio URI before requesting a network stream. Cached HLS MIME hints must never override the progressive format of local audio. The Downloads header places its clear action beside the title; confirmation is retained.

Validation includes real HTTP transfer/cancellation tests, byte-exact MP3/AAC demux fixtures generated with FFmpeg, embedded-artwork tests that compare encoded audio before and after tagging, manifest persistence/ownership checks, and existing artwork/transition regressions. Android-specific remuxing, WorkManager service lifecycle, and airplane-mode rendering still require a device run; JVM tests do not prove those behaviors.

## Embedded TTML (2026-09-24)

The downloader now checks existing embedded TTML and the Apple TTML cache, then fetches the original XML with a bounded request. It writes that document into the container's lyrics tag (MP3 USLT / M4A lyrics / FLAC and OGG LYRICS) before publishing the audio. Word timestamps, backing vocals and translations are preserved as XML. Other players need TTML support to interpret this structured tag.

A missing lyric response never invalidates saved audio. Ready marker version 3 distinguishes lyrics-complete files; older downloads or files without TTML can be processed again through a staging copy. Retagging without new lyrics preserves old lyrics, just as missing new artwork preserves existing artwork.

`LyricsRepository` checks local content/file URIs or the registered downloaded file before network providers and sends embedded TTML directly to the rich renderer. `LyricsParser` also projects embedded TTML for legacy consumers. MediaStore files with no extension are identified by container signature. Malformed XML, DOCTYPE and oversized lyric text are rejected. On-device offline rendering remains a manual check after installation.
