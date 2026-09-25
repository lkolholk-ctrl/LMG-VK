# Lyrics provider selection

LyricsScreen loads sources sequentially through LyricsRepository:

1. The user's original Apple TTML API, including its existing persistent cache.
2. BiniLyrics.
3. LyricsPlus.
4. LRCLIB.

Disabled providers are skipped. BetterLyrics, VK and embedded text remain optional
fallbacks after this chain. A successful primary result prevents lower-priority
requests. LRCLIB's memory lookup accepts only LRCLIB entries, so a cached result
from another provider cannot masquerade as LRCLIB.

BiniLyrics uses the endpoint referenced by the official YouLy+ client:
`https://lyrics-api.binimum.org/getLyrics?q=<title artist>`. It is a separate source
in the source picker, enabled once on upgrade; later disabling it is respected.
The user's API keeps first priority. Search and TTML downloads share an eight-second
budget, and track changes cancel network requests.

Matches require the same normalized artist and title. A generic title can match
a parenthesized edition only with a known duration within eight seconds; explicitly
requested editions must match their full title. Exact titles rank first, followed
by word timing and duration proximity. At most three matching files are attempted.
Only HTTPS URLs on the documented `lyrics-storage.binimum.org` host are accepted.
The downloaded TTML is validated by the existing renderer parser and passed through
unchanged, preserving its timing, background vocals and translations. Search errors,
unavailable files and malformed TTML allow the next provider to run.

On 2026-09-07 a live search for What Is Love / Haddaway and download of the
270-second word-timed edition both returned HTTP 200. Earlier checks returned 403,
so availability is intermittent. Protocol reference:
`ibratabian17/YouLyPlus`, `src/background/services/biniLyricsService.js`.

LyricsPlus uses the existing mirror list and an eight-second overall budget.
The documented `/v2/lyrics/get` format exposes `syllabus` entries with millisecond
`time`/`duration`, `isBackground`, translation/transliteration and singer metadata.
Those are converted directly to the existing Legacy model, preserving background
vocals and localized layers instead of flattening them through LyricsPlusRichAdapter.
When JSON is unavailable a mirror's `/v1/ttml/get` endpoint is tried within the
same budget. Raw TTML and JSON containing a `ttml` field are both accepted.

Provider-only TTML normalization fills missing paragraph begin/end and span end
attributes from supplied timing, duration, subsequent boundaries or track length.
It rejects non-TTML XML and DTDs before parsing and refuses external entity
resolution, including on Android where desktop DOM security features are absent.
Nested background timing is resolved independently of main-vocal word boundaries.
Original primary-provider TTML is
never normalized by this code. Word-timed mirror responses take precedence over
line-timed responses while the bounded mirror search is still running.

Protocol reference: `ibratabian17/lyricsplus`, branch `cookie`,
`docs/endpoints.md` and `src/shared/parsers/ttml.parser.js` (read-only research).
Server probes on 2026-09-07 found the configured mirrors returning HTTP errors,
DNS failure or a TLS hostname mismatch. No TLS verification was disabled and no
mirror server was modified. Passing parser tests does not prove live availability.

Tests cover reduced TTML, overlapping background timing, inferred next paragraphs,
v2 syllables/background/translation preservation,
punctuation, missing durations, untimed/invalid responses, and provider priority.
Renderer, playback clock, font, motion and VK network/proxy files are unchanged.
