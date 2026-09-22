# Channel EOS stages

`ChannelEndState` ports the stage selection in
`MEMixerChannel::PropagateEndOfStream` (1b8f635ec–1b8f63660). Values 1/2/3/4
represent pending upstream processing, TimePitch, downstream processing and
completion. It skips stages absent in the configured processor topology and
only advances. The source checks the processing-node pointer at +330, placement
byte at +320 and TimePitch pointer at +570. The class takes resolved topology;
it does not infer it from PCM amplitude.

`TimePitchUpstream` (1b8f5f2fc–1b8f5f324) records the end of the upstream pull
(start plus requested frame count) the first time stage 2 is observed. The
initial sentinel is the double 1e63 at 1b916bed0. Later pulls must not move that
boundary. `TimePitchSampleTimes` (1b8f689d4–1b8f68a04) advances to stage 3 when
the callback's mapped source start reaches that boundary. This is NOT a test
of an empty output ring or a fixed FFT-sized silent tail.

Evidence: `time_pitch/channel_propagate_end.asm`, `channel_sample_times.asm`
and `time_pitch_upstream.asm`. The reference generator executes the original
selection/control flow through the stage store, stopping before logging and
host stop-notification setup. Its fixture contains all 160 combinations of
binary topology flags, old state 0–4 and request 1–4. The portable test compares
reachable states (unreachable topology/state combinations remain in the fixture)
and tests one-shot boundary capture, the exact crossing and a downstream graph.
The host test passed with ASan/UBSan, leak detection disabled.

Not yet connected: graph activity/tail completion, TimePitch history-to-source
reporting and renderer drain requests. The original completion notification
also schedules host stopping using a device latency; that platform side effect
is not part of this class. This implementation alone does not make stream EOF
ready for playback integration.
