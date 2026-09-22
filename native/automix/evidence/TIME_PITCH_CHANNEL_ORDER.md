# Mixer-channel insertion order: DSPGraph precedes TimePitch

Source: `/srv/research/tmp/extracted_dylibs/AudioToolbox`, arm64e.
Symbols resolved with `llvm-nm-19 --demangle`; instruction listings are retained
in `time_pitch/channel_processors.asm`, `channel_chain.asm`,
`channel_add_processing_node.asm`, and `time_pitch_upstream.asm`.

## Direct observations

`MEMixerChannel::GetValidProcessors` at `0x1b8f55a6c` builds an ordered
vector. The TimePitch processor at channel offset `0x568` is appended at
`0x1b8f55b70..0x1b8f55b88`. Additional processing nodes are inserted later,
at `0x1b8f55de4..0x1b8f55ee0`, according to the node's field at offset `0x30`:

* value 1: insert at the beginning;
* value 2: insert immediately after the processor identified by FourCC
  `p_sp`, or at the beginning if that processor is absent;
* other values: append at the end.

`MEMixerChannel::AddProcessingNode` at `0x1b8f64150` identifies the source
of that field. It atomically loads `AQProcessingNode + 0x40` at
`0x1b8f64228..0x1b8f6422c`, then stores it at allocation offset `0x48`
at `0x1b8f642b4`. The embedded processor starts at allocation offset
`0x18`, so this is precisely processor offset `0x30`.

`RebuildMixerChannelChain` iterates the vector without reversing it
(`0x1b8f54664`, `0x1b8f54bbc`). For FourCC `tmpt`, it inserts a
`TimePitchUpstream` wrapper before the TimePitch node
(`0x1b8f54760..0x1b8f5481c`, `0x1b8f54bd0..0x1b8f54bf0`).
The wrapper converts the upstream timestamp and calls
`XProcessingInsertBase::PullInput` at `0x1b8f5f2dc..0x1b8f5f2f4`.

## Scope of the generic mixer evidence

The generic mixer supports multiple effect positions. These functions alone
do **not** prove that the AutoMix DSPGraph precedes TimePitch, or where
`out_gain` is applied. The earlier research document's upstream placement
is explicitly labelled STRONG_INFERENCE, not direct evidence.

The concrete AVFoundation binding below resolves the DSPGraph placement.
The volume-ramp (`out_gain`) application and EOF/tail handling remain separate
unresolved host contracts. Identifying `p_sp` is unnecessary for placement 1.

## Located next binding layer

The already extracted shared-cache `.07` contains both AVFCore (Mach-O
header `0x195c90000`) and MediaToolbox (`0x195f03000`). Their LC_SYMTAB
entries resolve against shared `__LINKEDIT` at `0x1fffe4000`, file offset
`0x4000`; no additional cache extraction is needed.

MediaToolbox exports the explicit key
`_kFigAudioProcessingUnitOptionKey_PlacementFlags` at `0x1e7695870`,
alongside component subtype/manufacturer, properties, and DSPGraphText keys.
It imports `ATAudioProcessingNodeInstantiate`. The remaining trace should
follow this placement key into that call, rather than infer ordering from
the AVAudioMix API name. Export/import evidence is in
`time_pitch/mediatoolbox_effect_symbols.txt`.

AVFCore's `AVAudioMixProcessingEffect` class is at `0x1ee7d65e8`;
its class-ro data is at `0x1f0d222e0`, and its relative method list is at
`0x195e4fe30` (15 entries). The implementation-address span has been saved
in `time_pitch/avf_effect_methods.asm`. Method implementation offsets resolve
relative to their own fields (`entry + 8`); selector names in this optimized
cache need separate resolution and have not been assigned guessed names.
## Concrete DSPGraph binding

The method at `0x195d76188` builds the processing-unit options when the effect
has graph text. It writes manufacturer `appl` at `0x195d761d0..61f0`, subtype
`dspg` at `0x195d761f8..6218`, and placement **1** at
`0x195d76220..623c`. The latter loads the placement-key address through GOT
slot `0x1e6b756b8`, whose decoded target is `0x1e7695870`. This is a concrete
DSPGraph binding, not a default inferred from the generic mixer.

MediaToolbox's constructor at `0x196498368` reads the same placement key
through its CFString (`0x1f0dce718`) at `0x196498434..444`; it requires a
numeric value and passes its converted value as argument 2 at
`0x196498504..51c`. The listing is `processing_unit_placement.asm`.
The AudioToolbox server instantiation preserves its placement argument in
`AQProcessingNode + 0x40` at `0x1b90dbca4`
(`processing_node_server.asm`). Combined with the insertion logic above,
this places the DSPGraph before TimePitch.

`ProcessedTrackStream` implements this ordering with a bounded intermediate
buffer. Graph events advance on accepted source PCM; downstream backpressure
cannot reapply the graph to the same samples. The host must still resolve
event times, own output gain, and specify EOS behavior.

The transport test compares against separately scheduled graph processing
followed by TimePitch, forcing input backpressure in mono/stereo at rates
0.75, 1 and 1.25. Graph block boundaries are kept equal because the recovered
gain-ramp arithmetic itself has block-dependent floating-point accumulation;
TimePitch pull pacing differs. This tests the new composition, not complete
Apple host rendering or Media3 integration.
