# Analysis proxy and occurrence-bound prefetch

Basis: `Astra_Pro_Automix_Analysis_Addon.txt` supplied 2026-09-26 and the supplied
`LMG-VK-project.zip`. This change does not touch native DSP, output clocks, the
media3-lmg artifact, renderer creation, gain automation or playback eligibility.

## Implemented

The fixed origin is `https://ru-lyrics.gsgit.org/v2/automix/analysis`.
Requests use GET and **raw=1**; the existing `AppleLyricsConfig.API_KEY` supplies
X-API-Key. Its value matched the supplied addon when checked. No new copy of the
credential, developer-token request, or Authentication/Authorization bearer is added.
The header X-Track-Id is required and must match an explicitly requested ID.
The canonical raw body and ID then go through the existing `AppleSongAnalysisParser`
and C++ decoder before a positive cache entry is accepted. Header BPM/support flags
are not used as a replacement for the JSON or as permission to run a transition.

Requests are started by collecting the **existing** observation requests while
PlayerSettings.autoMix is enabled. The observer already obtains the next occurrence
from Timeline/repeat/shuffle. The incoming request is scheduled first; current-track
analysis is also fetched when missing (e.g. first song/resume). There is no second
Player listener. Player metadata is captured on the application looper. HTTP, byte
copies, UTF-8 validation and native parsing run on IO/worker dispatchers, not in the
PCM callback. This is not a claim that networking itself is allocation-free.

Both lookup forms are supported:
- verified ID: `?id=...&raw=1`;
- discovery: escaped title/artist and exact decimal seconds from milliseconds.

The addon mentions ISRC, but provides no independent ISRC parameter. None is invented.

## Recording identity and duration: deliberate acceptance boundary

For direct submission, the metadata field
`lmg.automix.verifiedAppleSongId` must have been set by the trusted provider for the
EXACT playback recording. Never derive it solely from numeric mediaId/title/artist
or a similar duration. The original occurrence ticket and all source fields are
rechecked before submission; the established pipeline checks it again.

Without that provider assertion, metadata lookup downloads and validates a cached
**CANDIDATE_ONLY** response but does NOT submit it to the live observation pipeline.
This is not a new matching algorithm. A production matching UI/provider is not added.
Once verified, the provider can rebuild that MediaItem's metadata with the verified
ID, causing the usual metadata/timeline event and a fresh generation. The new ID
request is made independently; search data is not silently upgraded to verified.

Equal reference/actual durations and policy eligibility remain the previous native
restrictions. The server's **±6 second search tolerance** is NOT a permissible PCM
remapping error. No ±6-second relaxation, spatial default, previous-state default or
ALLOWED policy verdict is added. ID validation against the JSON proves the returned
catalog ID, not that a third-party audio file is that recording.

## Cache / cancellation / failure contracts

The cache is service-lifetime MEMORY, not a new SQLite table: max 16 MiB raw payload,
max 32 entries, positive TTL 10 min, 404 TTL 30 sec. These are LMG policies, not
reported Apple constants. Temporary/authentication/rate-limit errors are not cached.
There is no infinite retry, polling, or cache file on disk. The server's own SQLite
cache is outside this code and is not independently verified here.

Identical requests share one flight, including two distinct occurrences with the
same catalog ID. Each subscriber keeps its ORIGINAL ticket; cancellation of one does
not cancel another valid subscriber. Generation/enable changes cancel subscribers.
When the last subscriber leaves, work is cancelled and disconnect is requested on IO.
Semaphore permits are held until the actual blocking worker exits (at most two HTTP
workers per store). Four distinct queued/in-flight keys are allowed. A new request
beyond this bounded scope returns BUSY, not an unbounded task queue.

Connect timeout 10 s; read timeout 15 s; task discard timeout 30 s. Cancellation is
best-effort for HttpURLConnection and does NOT promise an exact socket teardown
latency. No disconnect is performed synchronously on the audio/Main cancellation path.
HTTP redirects are refused so credentials stay at the fixed origin. Known MIME /
content encodings, UTF-8, declared/actual length, nonempty body and the 4 MiB cap are
checked. No URLs, search strings, track IDs, credentials or payloads enter diagnostics.

404/network errors result in unavailable analysis. They do NOT install or invoke
TFLite/SmartTransitionFinder, create a fallback recipe, seek or mute the player.
Existing playback simply retains ownership. An existing cached validated response
can still satisfy a request offline, without broadening identity or scope checks.

## Timing: what this patch does NOT claim

The addon asks for N+1 fetch at track start, a midpoint/45–60 s readiness checkpoint,
and renderer prebuffering 10–15 s before the selected cue.

This patch implements the **early data fetch**, not a new renderer schedule.
For verified inputs the current pipeline still parses/calculates AS SOON AS both
responses are available. It is not deliberately delayed until 50%; this makes the
checkpoint a desired readiness bound, not a sleep condition. There is no new watchdog
that guarantees readiness by that bound. A late response is still subject to the
existing cue/freshness/lease checks.

The 10–15 s preroll behavior is NOT implemented/verified here. It requires changes
inside the existing single-player playback-owner/renderer preparation, not a timer
calling play() on a second player or a forced hold of the sink. Current renderer,
queue constraints, single-pair/terminal-B scope and PCM warmup remain unchanged.

The addon's exitPoints/entryPoints prose does NOT replace the existing C++ Flex
structure and supported 8/9/12 selection with guessed point pairing or new styles.

## Explicit conflict in the supplied addon

Its final section names `SmartTransitionFinder + automix_v2.tflite` as fallback.
That conflicts with the project's explicit no-TFLite rule. This patch follows that
rule and does not add/re-enable models. This is a documented divergence, not a claim
that the supplied final section requested the same behavior.

## Validation boundaries

32 host scenario groups exercise real new transport/store/coordinator code with a
recording HttpURLConnection and an injected validator. They do not hit the deployed
endpoint, execute native parsing, run Media3, or check Android lifecycle callbacks.
One JUnit method wraps these same groups; do not report 32 new real-JNI or 32 JUnit
methods. The native decoder itself is reused without modification.

`Media3AnalysisPrefetch` and its observer hook require the full Android Gradle build
against the installed fork. Server latency, uptime, cached <2 ms replies, matching
quality and first-half readiness are NOT independently certified by these tests.

Official API contracts consulted: Kotlin suspendCancellableCoroutine /
CancellableContinuation cancellation; Java HttpURLConnection redirects and
connection lifecycle. No dependency versions are upgraded by this patch.
