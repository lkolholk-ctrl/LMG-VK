# Bounded streaming owner

TimePitchStream connects reset, rings, hop clocks, identity/spectral windows and
PCM delivery. Controls are explicit host inputs. Enqueue/dequeue accept planar
mono or one coherent stereo pair and return actual accepted/delivered frames;
maximum block size and ring capacity impose backpressure without allocation.

Source connections:
- 234f459ac copies acquired PCM into input rings and commits source anchors.
- 234f45c58 applies (A+B)*0x3f3504f3 and (A-B)*0x3f3504f3 on BOTH input and
  output of a coherent stereo pair (orthonormal mid/side). Mono copies directly.
- 234f46400 chooses identity for equal rounded input/output hops unless pitch
  forces the spectral branch, and 234f46208 commits source/output cursors.
- 234f45ce4 delivers only outputWrite-outputRead available samples. Clearing
  extends from outputClear through NEW outputRead, including initial skipped
  scheduled padding (234f45f04..10); just clearing delivered frames is wrong.

ASan/UBSan tests verify identical PCM for 64/127-frame block partitions at
0.75x/1x/1.25x in normal/scheduled modes, ring wrapping, source/output counters,
constant independent stereo levels and reset/control validation. This is a
host composition check, not a whole original-AU streaming oracle. Low-level
DSP components retain their independent original fixtures.

Explicit remaining scope: source mapped-rate schedules (this owner currently
uses constant/control-updated rates), EOF/tail policy, source activity reporting
(the current spectralActive reports branch use), original whole-stream fidelity,
channel layouts beyond mono/stereo, JNI and Media3 binding. No automatic tail
padding, guessed trim length or pass-through fallback is supplied. Hosts must
serialize configuration and processing and provide valid disjoint planes.

Mapped-hop support is now connected through TimePitchTimeMapView, following
234f46440..6494: next output time is mapped before computing input advance.
TimePitchTimeMap implements the original linked-node boundary selection and
AudioToolboxUtility node constructor 1dcf70308 / evaluator 1dcf704b4. This
constructor derives output duration from source duration divided by mean
endpoint rate. Evaluation uses a quadratic inside/before the node and linear
extrapolation after its endpoint, with the source FMA order. This differs from
simply applying the higher-level PlaybackTimeMap inverse at every hop.

96 original constructors and 480 evaluations pass bit-for-bit with no hooks;
stream tests also pass block-partition invariance with this recovered variable
rate map. The remaining schedule work is binding Automix's timed rate events to
these node arguments in the source's frame/time domain, not inventing a new
interpolator. The map object must outlive its attached view. EOF/tail, activity
reporting, whole-AU verification and JNI/Media3 integration remain outstanding.

## Source-time reporting

`timePitchHistorySourceTime` now implements 234f46810–234f468ec: inspect
ceil(FFT size / last output hop) entries backwards from the current history
cursor, sum window weights in double and weighted frame coordinates using the
original float multiplication, round with floor(x + 0.5), then apply the input
anchor. Signed frame additions/subtractions wrap as on ARM. Missing/zero-weight
history follows the original anchor fallback. The host accepts at most 64
history entries per query, covering the supported smoothness range.

`TimePitchStream::sourceTimeForOutput` dispatches to the attached time map when
present, as 234f467f0–234f4680c does, otherwise uses the stored history and actual
last committed output hop. An unmapped query before the first hop is rejected.
The method uses the current history cursor; it does not reconstruct historical
render-callback snapshots for arbitrarily old output buffers.

256 queries executed against the original ARM arithmetic match bit-for-bit,
including inactive history, zero weights, negative coordinates, wrapped history
indices and fractional timestamp anchors. The native stream test also verifies
mapped dispatch and identity source times while feeding and draining 256-frame
blocks. ASan/UBSan passed (leak detection disabled). This does not verify reporting
under a prolonged downstream stall: production timing of the sample-time
callback remains to be bound to the render/drain schedule before EOS activation.
