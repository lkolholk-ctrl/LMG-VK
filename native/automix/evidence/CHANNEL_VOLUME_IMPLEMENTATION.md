# Recovered mixer-channel volume state

`channel_volume.cpp` ports `MEMixerChannel::_CombineVolumes` from
AudioToolbox at `0x1b8f6188c..0x1b8f61bd0`. Listing:
`time_pitch/combine_volumes.asm`. This is a state evaluator, not yet a complete
AVAudioMix volume scheduler or PCM multiplication implementation.

The original visits 11 slots of stride `0x28` at channel offset `0x368`,
starting with the base volume at `0x528`. It multiplies all slots into the
first result. If the boolean input is true, the second result multiplies slots
0..9; otherwise that result remains the base volume. Slot 10 never contributes
to the second result. The output boolean indicates an unfinished active ramp.

For each slot, the first evaluation captures current volume as the start value.
It does not interpolate on that first call. Later evaluations calculate the
elapsed ratio in float, clamp it to [0,1], and interpolate with one float FMA.
At or beyond the end frame, the ramp deactivates and uses the exact end volume.
Curve value 1 takes the recovered direction-dependent sine approximation;
other values use the linear ratio. The original polynomial constants and
operation order, including `FNMSUB` as `b*abs(b)-b`, are retained.

`tools/channel_volume_reference.py` executes the entire original function in
Unicorn, including updates to all 11 slots. Only the PAC prologue is replaced
by NOP, execution stops before authenticated return, and logging is disabled
through its original null configuration pointer. No arithmetic calls are
intercepted. Constants are read from the supplied Mach-O.

`tests/fixtures/channel_volume.bin` contains 192 initial configurations, each
evaluated three times in sequence: 576 original input/output state pairs.
The C++ test compares both result floats and all meaningful slot fields,
including activation/capture flags and start/current/end values. Coverage
includes zero duration, positions before and after ramps, both curve directions,
first/repeated evaluations, and both modes of the second result.

The test passes bit-for-bit with ASan/UBSan. LeakSanitizer was disabled because
the execution environment uses ptrace; this is not a leak-check result.

## Target-event state updates

`scheduleChannelVolumeTarget` ports the state mutation at
`UpdateParameters` `0x1b8ecbc38..0x1b8ecbc4c` and
`0x1b8ecc1b0..0x1b8ecc204`. The slot's float ramp-time parameter lives at
offset 8; a duration-parameter event writes it at `0x1b8ecc01c`.

* A zero ramp time writes the target to current volume and clears only active.
* A nonzero ramp time interrupts an active ramp by copying its previous end
  volume to current and setting the capture-pending flag.
* It then activates the new ramp, clears started, stores the new target,
  and computes start as block frame plus the signed 32-bit event offset.
* Duration frames are the unsigned-saturating ARM `FCVTZU` conversion of
  `sampleRate * double(floatRampSeconds)`. There is no nearest-frame rounding.

`channel_volume_reference.py --targets PATH` additionally executes these exact
instruction ranges with supplied slot/event/channel/stack state, stopping after
the state writes and before the subsequent scheduler-queue maintenance.
The fixture `channel_volume_targets.bin` and C++ test compare 256 updates:
active/inactive slots, signed offsets, positive/zero/negative duration and
saturating large duration at four sample rates. All meaningful resulting slot
fields match bit-for-bit. This verifies state installation, not event ordering
or queue-boundary insertion in the complete scheduler.

## Remaining binding work

### MediaToolbox curve events located

`0x19672a39c` reads dictionary key `AudioCurve_Volume` (CFString
`0x1f0d8c838`). It handles records of time, volume and ramp-style in groups
of three; style `EqualPower` is CFString `0x1f0d8d1b8`. The instruction
listing is `time_pitch/media_volume_curve_scheduler.asm`.

The fourth integer argument selects one of two parameter groups:

| Argument w4 | Ramp duration ID | Volume target ID | Curve ID | Mixer slot |
| --- | --- | --- | --- | --- |
| nonzero | 12 | 11 | 18 | 3 |
| zero | 27 | 26 | 28 | 9 |

The IDs are written at `0x19672aa28..aa98`; the alternate pair (27,26)
is stored at `0x196a93758`. At `0x19672ab14..ab24`, the comparison helper's
nonzero result selects curve value 1, and zero selects 0. The curve value is
also passed through `0x1967742d4` before the later three-parameter scheduled
write. Resolve those helpers and the surrounding initialization before claiming
the complete external-style-to-channel-curve binding.

`Scheduler_SetParameter` sends IDs 11/26 to target events, 12/27 to ramp-time
events, and 18/28 to the curve setter. `ParamIDToVolIndex` at `0x1b8f6ab78`
maps their groups to slots 3/9 respectively. The parameter-index listing is
saved in `time_pitch/volume_parameter_index.asm`.

`0x1960c8dd0` invokes the curve builder with the object's dictionary at
offset `0x290` and w4=1; `0x1960c8e04` uses offset `0x298` and w4=0.
These are distinct volume lanes. Which dictionary carries the Smart Transitions
AVAudioMix envelope still needs an upstream property-binding trace.

### Duration transformation before AudioQueue

The curve builder calls wrapper `0x19677447c`, whose final call at
`0x1967745c0` is `AudioQueueScheduleParameters`. This is directly resolved
through stub `0x19a478130` to AudioToolbox export `0x1b901dfb8`, not inferred
from its argument count. The stub cache `.08` has been extracted (442,368
bytes); no additional framework extraction is needed for these stubs.

For IDs selected by bitmask `0x08001440` (6,10,12,27), that wrapper transforms
the duration before submission. At `0x196774544..590`, it computes:

1. float duration * float signed sample rate;
2. convert that product to double and add the original event frame;
3. transform the resulting timestamp with `0x196121e00`;
4. subtract the separately transformed original event frame;
5. divide by double signed sample rate and narrow the result to float.

Other values pass through unchanged. The mapping's complete timestamp behavior
must be recovered before substituting any of the existing time-map helpers.
Simply using the untransformed ramp duration would skip source behavior.

### Queue timestamp preparation resolved further

The original `0x196121e00` is saved as `time_pitch/media_queue_timestamp.asm`.
If the timestamp has no valid sample time (flag bit 0 clear), it copies the
timestamp unchanged. Otherwise it:

1. converts the double sample time to signed integer with ARM FCVTZS;
2. calls CMTimeMake with the queue's signed sample rate at offset `0x34`;
3. subtracts the queue's CMTime offset at `0x1c`;
4. converts back to the queue sample-rate scale with rounding method **1**;
5. calls AudioQueueConvertToUnscaledSampleTime, retaining the previous sample
   time on an error, and changes only the sample-time field in the copied stamp.

Stub resolution: `0x19a47b090 -> 0x196bf77dc` is CMTimeMake;
`0x19a47b240 -> 0x196bf7e9c` is CMTimeSubtract;
`0x19a47b020 -> 0x196bf7a0c` is CMTimeConvertScale;
`0x19a477ff0 -> 0x1b901e610` is AudioQueueConvertToUnscaledSampleTime.
Names were checked against the respective Mach-O symbol tables.

`convertMediaTimeScaleNearest` now ports the numeric method-1 conversion in
`media_time.cpp`: nearest, ties away from zero, preserving epoch and rounded
flag. It uses quotient/remainder integer arithmetic and rejects the original
infinite conversion results rather than exposing them as PCM frame positions.
`media_time_scale_reference.py` executes the original CoreMedia function and
its integer helpers without arithmetic hooks; 1002 fixture cases pass against
the C++ conversion alongside the existing effect-event tests under ASan/UBSan.
This closes the rounding primitive, not the complete queue-offset subtraction
or AudioQueue unscaled-time mapping. It does not justify rounding Media3 buffer
timestamps independently on every buffer.

`MixInputProc` skips `UpdateParameters` when the channel's TimePitch exists
(`0x1b8ecb1d0..1e4`). `TimePitchDownstream` instead invokes that updater at
`0x1b8f5fd70` with the channel's mapped time at `+0x5a8`. This is evidence
about scheduling, not sufficient proof of where PCM volume multiplication
occurs or which slot receives AVAudioMix `out_gain`.

Before attaching this evaluator to `NativeProcessedTrack`, trace the
AVAudioMix curve-to-slot events, ramp initialization, processing boundaries,
and downstream multiplication. Do not feed generic `AutomationLane` values
into it or assume that slot 10 is the AutoMix envelope.
