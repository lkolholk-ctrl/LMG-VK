# Connected spectral branch

`TimePitchSpectral` composes the source operations in `0x234f46400`:

* `0x234f466a4..66d4`: copy a selected channel frame, multiply the analysis
  window, forward FFT.
* `0x234f466d8..6708`: leading-channel phase analysis (including magnitudes),
  optional coherence, then optional transient preservation.
* `0x234f4670c..6734`: synthesis, pitch-bin remapping, inverse FFT and OLA.
* `0x234f4674c..67ac`: paired channel repeats window/FFT/synthesis/remap/OLA
  with updateRotation=false and the leading channel's rotation and mapping.

The wrapper owns its phase histories, prepared FFT and scratch. No processing
allocation occurs. It takes selected frames and caller-owned output rings, so
it does not invent an input-pull policy, output readiness, EOF, or a clock.
Caller-provided transient state returns the source-adjusted input-hop value.
The stereo API represents the source coherent pair, not arbitrary channel
layout grouping. Window multiplication is one float multiply per element.

Integration tests passed under ASan/UBSan: silence, equal-channel bit identity,
wrapped overlap-add, deterministic fresh reset, retained phase across repeated
hops and pitch change. These are composition/lifecycle checks, not an original
whole-hop oracle. The individual kernels and FFT have their own original ARM
fixtures. Complete composed-hop fidelity remains to be checked end-to-end.

The identity arithmetic at `0x234f46510..65a4` is now available separately as
`timePitchIdentityWindow`: float gain `8/(smoothness*3)`, analysis window,
gain multiply, synthesis-window OLA, and retention of the windowed/scaled frame
for later phase reseeding. Tests verify constant-signal reconstruction at
smoothness 4/8/16, wrap handling and preservation of the input/previous frame.
The streaming owner must choose this branch using the original equal-hop and
force-spectral conditions; it is not an unconditional rate==1 bypass.

Still absent from this wrapper: automatic branch dispatch and source flag
management, silence
activity reporting, source clock preparation/commit and stream pull/drain.
`reset` here resets spectral histories, not the complete streaming AU. This
module is not yet wired into the Android audio sink and is not a complete
TimePitch product. Ring size, pointer lengths, finite PCM, positive effective
input hop and the source parameter ranges must be validated by that owner.

Phase reseeding is now implemented by `seedFromPreviousIdentity`, following
`0x234f465fc..66a0`: re-window the retained leading-channel PCM, FFT, original
vDSP phase, multiply by the source float 0x3e22f983 and copy analysis to
synthesis history. The source flag +0x9a2 controls whether to invoke it; no
unproven automatic reset on every identity window is introduced.

`timePitchSeedPhaseRadians` ports the aligned main SIMD path of vDSP_zvphas
at 0x234b36dec. A reproducible dependency slice extracts 67 operations from
the first lane of the unrolled block; all groups are checked against original
output, not presumed equivalent. The FRECPE mantissa table is captured from
the original instruction and exponent scaling preserves float arithmetic.
Twelve original vector cases across 128/256/2048 bins, random quadrants,
zero axes, signed zeros and large/small magnitudes match bit-for-bit under
ASan/UBSan. No DSP arithmetic is hooked in the oracle. The API is restricted
to the supported multiple-of-32 bin counts; it does not claim scalar/tail
vDSP compatibility. The connected identity/reseed/spectral stereo test also
passes. Full streaming source-clock and input/output ownership remain open.
