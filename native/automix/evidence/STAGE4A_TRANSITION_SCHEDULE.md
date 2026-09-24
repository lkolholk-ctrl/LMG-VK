# Stage 4a — Structured candidate to continuous schedule

Date: 2026-09-24. Applies after the accepted Stage3b Source Context package (user-reported 20b4b9f).
This is the structured, continuous, nonspatial MusicKit subset with no previous playback end state.
It does not process PCM, enable playback, close all upstream policy, or claim full Apple parity.

## Evidence identity and scope

The user supplied the extracted `_SonicKit_MusicKit_Packages`: 1,063,640 bytes,
SHA256 e843a9a94838f49c9c1e9b11f57fca70c6eebde59d7a3fd8d43917ee65607b4d,
UUID 83c387fd-3c68-344a-85e5-0c3c015811c1. The image was inspected using LLVM 17 disassembly
and direct VM/file mapping. Existing textual dumps were auxiliary; ambiguous outlined-function
names were resolved from actual branch immediates. No original instructions were executed.

STAGE4A_SCHEDULE_PROVENANCE.json records 27 inspected byte windows, direct calls and witness
bindings. The read-only automix_verify_schedule_image.py verifies the exact image and these
relationships. It emits no code, disassembly or binary slices. Hashes prove attribution, not
semantic correctness. The binary and original instruction bodies are not redistributed.

## Resolved dispatch and ownership

2722685b4 repacks the selected candidate and previous-state fields; continuous dispatch
27226725c enters 27221013c. The region-pair variant at +0xaa selects the structured wrapper
through 2722105b4. That wrapper uses witness table 2884aaa50, not the other unstructured table.
The schedule constructor takes source/cue ranges from slots +8/+16, automation parent ranges
from +24/+32, rate endpoints from +40/+48/+56/+64, overall range from +72, each side's playback
range from +80/+88 and reference time from +96. The constructor calls the existing style
compiler 272210660 separately for each side with these parent windows and rate endpoints.

Source field names include transitionTimeRange, playbackTransitionTimeRange,
referenceTransitionTime, automations, startPlaybackSongTime and previousPlaybackEndState.
The reference time is preserved as data. This patch does NOT assign it authority to change
MediaSession's active item or assume it is a safe renderer-switch callback.

## Recovered rate geometry

Let outgoing=[a0,a1], incoming=[b0,b1], DA=a1-a0, DB=b1-b0. Let NA/NB be the differences
of the boundary **beat indices**, and F the incoming half/one/two scale (0.5,1,2).

* 27220fda4 converts the incoming count, multiplies by F, then divides by the outgoing count.
  R=(F*NB)/NA. The 3-double factor table is at 272296638.
* 27220fba0: effective incoming duration E=DB/R.
* 27220fb44: outgoing rate endpoints are 1 and DA/E in the supported nondegenerate domain.
* 27220fc00: create a virtual LINEAR ramp [b1-E,b1], values [E/DA,1], and evaluate it at
  the actual b0. That value is the incoming start rate; incoming end rate is 1.

The last rule is not generally just E/DA: b0 may lie within the virtual ramp. The production
implementation reuses ContinuousAutomationValues, preserving its boundary precedence.
It does not use a new Kotlin formula or replace this with a cloud BPM ratio.

Count attribution: 27222bd20 converts the region endpoints to the BeatEvent protocol;
27220fe30 subtracts its indices. The BeatEvent witnesses at 2884ab8b8 and 2884ab970 both
point to getter 2722174dc, which reads +8. The distinct downbeat getter 27221756c reads +16.
Counting bars instead of beats would conflate these protocols.

## Windows and time maps

The structured witness supplies each selected source region as its side's parent automation
window. The existing compileContinuousStyle preserves nested placement, source record order,
separate/repeated descriptors and equal-time bypa points. Its ts_rate branch replaces resource
values with planner-produced rates and linear curves. Mapped parameter requirements and nonzero
style offsets are outside this scoped compiler; canonical BM styles 8/9/12 need neither.

No automation is rounded to PCM frames. The existing selectedPlaybackRateRamp still selects the
FIRST full descriptor and FIRST adjacent ramp; no merging or global sort is introduced.

TA/TB are measured by the existing schedulePlaybackTimeMap, from each source-region start to
end, with temporary transition anchor zero. Source witnesses 27220fce4 / 27220fd28 produce:

    global transition range = [0, TA]
    outgoing playback range = [0, TA]
    incoming playback range = [S, TA], where S=max(0,TA-TB)
    referenceTransitionTime = S+(TA-S)*0.5

27220f400 uses the outgoing measurement twice; it is not max(TA,TB). 27220f568 performs the
separate multiply/add for reference time. Source asymmetry is preserved even when TB>TA:
TB is not silently shortened and TA is not extended. Playback range and transformed source
extent are distinct fields. A future PCM consumer must respect these fields, not infer equality.

Final side time maps use the side's playback start and original source cue. No previous-state
carry is fabricated. The supported source-context API continues to reject previous/spatial
inputs and reference/playback duration mismatch under its existing temporary LMG policy.

## Production composition, limits and failure behavior

observePlannerScheduledSource resolves the existing source context and calls the unchanged
seed producer/selector ONCE. A small extraction, encodePlannerSelectionBindingResult, reuses the
same typed selection result for the previous report and schedule. Tests compare the entire
embedded source envelope byte-for-byte with the previous API for 19 native cases.

The older composition intentionally releases its map ownership. The schedule layer therefore
re-prepares one bounded pair from the SAME immutable native cloud objects, then verifies exact
winner event/time identity. There is no second search or score choice. This additional bounded
preparation is not charged as another search budget, and is not claimed to be free. Future
ownership optimization may remove it; no long-lived native handle is introduced here.

Existing limits (4096 events, 4096 vocals, 16384 loudness points, 64 seed pairs, 16M search work)
remain. New LMG control-thread caps: at most 32 automation records per side, 2..4 points per
record, total response <=2048 signed64 words. A scope/compiler failure yields NO partial plan.
A valid candidate may remain visible as diagnostics when its schedule cannot be built.
Allocation/internal failures propagate. Timeout discards output; it does not interrupt C++.

Low-level compiler host guards reject nonfinite/nonpositive/overflowing geometry, unbound
styles, inaccurate candidate-to-structure references, mapped values, reversed event order and
excessive records. These are integration-domain constraints, not claims about Apple's traps
or every malformed-input behavior.

## Stateless JNI and generation-bound publication

New export: NativeObservationBridge.compileMusicKitScheduleV1. Both existing
selectResolvedPairV2 and selectMusicKitSourcePairV1 remain. No JNI fallback to an older entry
is attempted. The APK verifier requires ALL THREE defined dynamic exports in every ABI.

The source request remains 24 words. The result is an envelope containing the previous full
source selection envelope and, on COMPILED only, a complete continuous plan. Header fields
include version, length, generation, revision, status, payload lengths, executable=0, reserved=0.
The 40-word plan header identifies candidate/regions, beat counts, rates, spans, cues, source
reference time and counts. Following records use the fixed existing allEffectParameters()
order, followed by 2..4 (value,songTime,curve) points each. No pointers or input JSON are exposed.

Kotlin validates bounded shapes, versions, exact candidate/epoch/Criteria echoes, dictionary
indices, allowed curves, point order and source windows. It does not recalculate musical
weights or time integrals. Descriptor min/max values are NOT used to clamp catalog values
(e.g. existing LP defaults). Lists are defensively copied and unmodifiable.

PlannerSourceContextBinding calls the new entry inside the SAME serialized calculation and
publishes under existing generation/revision checks. No new player/listener/sink is registered.
AudioService and ObservationPipeline are unchanged. Explicit manually-resolved observation
continues through V2 and does not acquire a schedule implicitly.

Public read path: autoMixObservationState?.value?.result?.selection?.schedule. COMPILED is a
new descriptive status, not authorization. All reports and plans have canExecute=false.

checkPlannerScheduleFreshness is a tested read-only native helper: match epoch/revision and
reject missing/invalid/current outgoing positions strictly past the cue. Exact cue equality
passes the position comparison only; decoder readiness, safety margin, frame clock and atomic
arming remain Stage4c obligations. It is NOT wired to player mutation in Stage4a. No state
means "ready to execute". Consumers must revalidate position because time can advance within
a single generation; requiresPositionRevalidation=true documents that obligation.

## Tests and interpretation

35 native compiler/freshness groups; 560 Decimal-based independent geometry rows; 20 native
composition groups; 16 real native snapshots decoded by Kotlin and 740 malformed variants
rejected. Nine isolated semantic mutations must fail assertions, not compilation or signals.
The mutations include inverted count geometry, virtual-ramp error, forced-zero delay,
max-duration substitution, reference-time error, stale revision and execution flag.
The unchanged default-blocker mutation's textual anchor was updated for the encoder extraction;
its test still rejects dropping the unresolved-source mask.

Host composition uses a deliberately small synthetic 3-style TEST catalog (including wrong
resource ts values to test overrides), not a substituted production asset. The full canonical
JSON CTest and 10 new real-JNI JUnit tests load the unchanged SHA-verified original resource on
the server. Raw JSON identity, complete previous-envelope equivalence, three canonical BM
styles, generation publication, mutation of caller bytes and unsupported contexts are gates.

No original ARM/Swift execution or captured full-Apple-plan differential comparison was
performed. Local targeted C++, Kotlin transport, sanitizer and predecessor host checks are
not a claim of full repository CTest, canonical positive raw-JSON/JNI, Android build or device
verification. Expected server totals: 63 CTest, 53 real-JNI, existing 44 lifecycle tests,
three target ABIs and unchanged catalog SHA. Build fresh host JNI AND APK.

## Next boundary

Stage4b executes the compiled value schedule against deterministic PCM using the existing
DSP, validating partial consumption, latency and tails. Stage4c integrates frame-clock and
stream ownership in the user's single ExoPlayer. Neither is implemented or enabled here.
