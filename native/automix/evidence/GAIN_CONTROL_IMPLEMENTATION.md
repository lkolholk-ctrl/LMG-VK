# GainBox smoothing control

AudioToolboxCore's named `DSPGraph::GenericGainBox<LinearGainPolicy>` methods were
read from the locally extracted image. Bounded disassemblies are in `gain/`.

- Constructor `GainBox::GainBox` 0x18f5c3d6c reads two doubles at 0x18f652b60:
  smoothing .020 seconds and minimum .002 seconds.
- Property 0xc1a setter 0x18f5c3dd8 accepts a double and floors it to that minimum.
- Parameter setter 0x18f5c416c stores gain (ID 0) or mute (ID 1). Before the
  first process/reset boundary, it synchronizes current and target immediately.
- Reset 0x18f3c2dd8 restores current from gain/mute and sets first/ready flags.
- Process 0x18f3bfd74 updates target only when its ready flag is true. A gain
  written during an unfinished ramp stays queued rather than restarting it.
- The new slope is `(target-current)/float(trunc(seconds*integerSampleRate))`.
  The projected block end is an explicit float FMA. An overshoot splits the
  block at trunc(abs((target-current)/increment)), followed by constant target.
- Input flagged silent skips ramp advancement. Equality of current and target
  sets ready for the next process call.

`GainSmoother` implements this control state and returns a `GainBlock` description.
It deliberately does not claim to implement the imported vector ramp-multiply
instruction path yet. The graph constructor will need to apply this control to
the actual sample multiplier, with channel-independent state advancement once
per graph slice. The default 20 ms smoothing cannot be replaced with immediate
automation-value multiplication when connecting the graph.

The native test covers initial writes, the 960-frame default at 48 kHz, queued
retargeting, silent-input hold, overshoot, mute/reset and the 96-frame minimum.
Unlike the AUFilter/reverb fixtures, this test uses assertions derived from the
instructions; it does not execute the original GainBox host in Unicorn.
The 0..1 gain bounds and supported rate range are host input constraints.

Next raw imports to resolve: GenericGainBox steady multiply at 0x193734640,
ramp multiply at 0x1937345e0; MixBox accumulates subsequent inputs through
0x193734620. Do not guess their rounding or replace smoothing with curve samples.
