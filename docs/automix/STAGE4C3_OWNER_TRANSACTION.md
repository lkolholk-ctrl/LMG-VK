# Stage 4c-3: native input ownership transaction

Applies after accepted Stage 4c-2 Cue Barrier & Lossless Release. This package delivers
**two-source native PCM admission**, NOT a complete live playback executor.
There is no AudioTrack writer, frame-clock implementation, DSP pumping, gain lease
implementation, end-of-stream policy, renderer handback, or automatic activation.
The real output-owner implementation is a remaining dependency, not a stub silently
installed in the app. Tests use an explicitly identified output-lease test double.

## Entry points and permissions

`RenderBoundaryController.prepareCueOwner`, `commitCueOwner`, `readCueOwner` and
`cueOwnerSnapshot` are internal playback-thread entry points. No new AudioService
method or UI switch is added. Existing debug cue requests remain diagnostic-only.
Dormant operation and reversible release preserve the accepted Stage 4c-2 contract.

A future playback adapter must provide `CuePlaybackLease`, backed by actual exclusive
sink/clock/gain ownership. A constant-true implementation is invalid in production.
NativeCueOwnerIngress cannot manufacture this lease: it owns no output resources.
All reported `canExecute` and `liveDspInstalled` values remain false. INPUT_OWNED and
`inputAcceptedByOwner` mean only admission into the native source queues; they do not
assert audible output or permission to bypass remaining renderer integration.

## Ownership sequence

1. With both original codec buffers held and not yet offered to the old sink, stage
   private PCM copies into the prepared native object's invisible storage. Validate
   both entire initial buffers before publishing any initial queue. Originals' bytes,
   positions, limits and PTS are unchanged. The ticket is opaque and instance-bound.
2. Recheck lease, pair, actual output identity, positions, format and publication.
3. Linearize CLAIMED against new-generation/revision publication with a short monitor
   containing only epoch validation and an atomic phase change. No JNI, buffer copy,
   delegate call, or output write runs under that publication monitor.
4. Quarantine both original streams before calling native commit. Commit makes BOTH
   initial queues visible by swapping preallocated storage. It cannot publish one
   side and call that pair ready. Native commit contains no allocation or DSP call.
5. On each renderer's next callback, acknowledge THAT original buffer once by moving
   its position to its limit. Do not acknowledge the other renderer's retained buffer
   from this callback. Codec lifetime stays with Media3 until its normal return path.
6. Later buffers enter bounded queues only in source-frame order. Position advances
   by accepted frames, never by offered frames. Partial retries require the identical
   original object, unchanged PTS/limit and expected position. No legacy sink write
   runs on the admitted path. Native acknowledgements do not increment the old
   AudioSink accepted-byte ledger.

Cancellation BEFORE CLAIMED discards invisible stage and restores ordinary original
buffer delivery. Cancellation or ANY uncertain error AFTER CLAIMED is terminal for
that transaction: native queues discard unread frames, state becomes RESET_REQUIRED,
and callbacks refuse legacy fallback. A coordinated true flush/reset must end both
old streams before ordinary playback can continue. Pause/configure without a real
flush do not license replay. No automatic flush is invoked by this package.

Main-thread invalidation publishes revocation, but native cleanup is deferred to the
established playback owner's next callback/cleanup. A new request can replace the
mailbox while an older pair is staged; retained holds keep that stage reachable for
cleanup on callback or flush. Staging allocations must also be closed explicitly on
the playback owner during service teardown. No JNI cleanup runs from Main merely
because an observation changes. If callbacks stop, this package does not claim a
bounded cleanup or audible-recovery deadline.

The old short cue timeout applies before claim only. Applying its 100 ms default to
an already committed multi-second transition would discard valid admitted input.
After claim a real output owner must implement its own readiness/starvation policy.

## Native transport

`PcmOwnerIngress` is one prepared object containing two independent ring queues and
two private staging arrays. Maximum capacity is 262144 frames per side, mono/stereo;
maximum allocated sample storage is 8 MiB for stereo (staging + visible queues).
Construction allocates; stage/commit/push/read use that storage. Input is explicitly
little-endian PCM16 or IEEE float32. PCM16 converts by /32768; floats are finite and
not clipped or attenuated, including values outside [-1,1] and signed zero.

Initial buffers must contain their cue and fit the staging capacity. Later buffers
can exceed queue capacity (bounded by 1 MiB); only free queue frames are accepted.
Every accepted sample has exactly one source-frame index. The per-source invariant:

    accepted = readByOwner + discarded + queued

`readByOwner` is NOT AudioTrack-consumed or audible frames. Reads are ordered and
never straddle the cue: pre-cue prefix is returned separately with an explicit flag.
This preserves the prefix for a future same-output writer instead of silently
playing, discarding, double-feeding or treating it as already rendered. Input reads
are guarded by the same epoch/lease before and after native access. On an error,
discard destination bytes and metadata even though its cursor is not published.
Cancellation can happen after the final software check; physical output still needs
its own serialized ownership and clock contract. This is not an atomic device write.

EOF/drain, actual ring-to-DSP consumption, output padding/tails, prefix playback,
clock remapping and A->B->C state carry are NOT implemented by ingress read.

## JNI and cleanup

`NativePcmOwnerIngress` adds nine native exports in the EXISTING
liblmg_automix_jni.so: nativeProtocol/Create/Stage/Commit/Abort/Push/Read/Stats/Destroy.
Protocol=1; handles are monotonically numbered registry IDs, never C++ pointers.
Registry shared ownership prevents destruction of an entry during an active call.
Kotlin and native code enforce one established owner thread. Direct buffers are
range-checked; native output validates writable state and float alignment. Input
cursors are not changed by the JNI wrapper. Only its caller publishes consumption.
Private native errors do not include source payload, URL or recording metadata.

The registry, operation mutex and synchronized Kotlin wrapper are safety mechanisms,
not a claim of lock-free or allocation-free real-time execution. Stage makes views
and receipts; these entry costs and JVM/GC behavior need device profiling before a
real audio callback integration. Objects require explicit close on their established
owner thread; there is no hidden Cleaner or extra executor/player thread.

## Integration changes

Existing PcmCueGate, RenderBoundary and BoundaryForwarding gain the dormant admission
path. AudioService, Media3BoundaryAudioSink, the fork/AAR, PlayerAudioChain, DSP math,
frame mappings, offline stabilization patches, Gradle and previous observation wire
formats remain unchanged. The fork remains 1.5.1-lmg30-boundary1 for the accepted app.
No Player, Renderer, AudioSink, AudioTrack or new external audio library is created.

Two CTests are added. Forty real-JNI JUnit scenarios are mandatory in the APK guard;
its previous 53 real-JNI become 93. All 44 observation lifecycle, 78 boundary JVM,
78 cue JVM and eight existing fork tests retain their own counts. Existing three
schedule/selection exports remain; all nine new ingress exports must be dynamically
defined in each existing target ABI. No old APK can pass just from fresh XML.

## Validation interpretation

- Actual new C++ core tests: 26 groups + 512 randomized two-source streams.
- Actual new shared JNI + production gate: 40 scenarios, including 20 randomized
  end-to-end stream partitions inside one scenario. Lease is a test double only.
- Independent native and Kotlin semantic mutations require assertion failures;
  compiler errors, signals and timeouts do not count. Kotlin mutant class origin
  is verified and the same original test bytecode is reused.
- ASan/UBSan run actual new C++ code and both native suites with leak detection.
- Previous cue/ledger and observation lifecycle regression scripts remain gates.
- Full repository CTest, complete production shared-library linking, Gradle/JUnit,
  Android ABI builds, APK checks and devices require the user's server. A targeted
  host JNI is not the full DSP library. Tests never claim physical AudioTrack output.

Next missing consumer: prepared same-output playback owner that satisfies the real
lease, plays the pre-cue prefixes, feeds existing DSP, maps a shared output clock,
applies transition gain once, drains and hands back correctly. It must not install
a fake lease or turn canExecute true merely because input admission tests pass.
