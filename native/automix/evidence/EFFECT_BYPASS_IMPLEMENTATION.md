# Live graph bypass lifecycle

TrackGraph and ScheduledTrackGraph now accept serialized bypass changes.
`prepareGraphEvents` accepts binary `bypa` writes; existing rate/geometry
compatibility restrictions remain. There is no guessed fade time or threshold.

## Recovered dispatch and state changes

The four effect-class vtables in libEmbeddedSystemAUs (iOS 23A341) are:
LP `0x284ad2670`, HP `0x284ad2b58`, delay `0x284ad3420`, filter `0x284adf598`.
At +0x48 they all point to reset dispatcher `0x234f365b0`; at +0x248/+0x250
they point to bypass setter/getter `0x234f39214`/`0x234f3920c`. These access the
byte at +0x228. Process at +0xb0 is `0x234f36380`: bypass returns before channel
kernel processing and before silence/tail bookkeeping. TrackGraph supplies the
direct pass-through routing explicitly.

Property setter +0x60 is `0x234fd8be4` for LP/HP/delay, forwarding property 21
to `0x234ff5d3c`. Filter uses `0x234ff5d3c` directly. This shared setter:

1. Normalizes the incoming unsigned property value to bool.
2. Returns without changes if the value matches current bypass.
3. On true -> false, if initialized (+0x11 == 1), calls reset (+0x48).
4. Stores the new flag through virtual setter +0x248.

Reset dispatcher calls each channel kernel's +0x10 reset. Verified targets:

| Kernel | Reset | Effect |
| --- | --- | --- |
| LP | `0x234f684f0` | Zero four double histories at +0x40 |
| HP | `0x234fc5788` | Same |
| Filter | `0x234f39724` | Zero histories of all five sections |
| Delay | `0x234fded20` | Zero accumulator and ring; retain circular indices |

Reverb has a different setter/process implementation and preserves its tank
on serialized bypass transitions. See REVERB_BYPASS_IMPLEMENTATION.md. Gain
smoothers remain active throughout graph bypass, as required by graph topology.

## Verification and limits

`tools/effect_bypass_reference.py` runs the original shared setter for 14 cases,
covering initialized/uninitialized effects, repeated writes, both edges and
nonzero property normalization. Virtual reset dispatch is intercepted and
recorded. It separately executes all four original kernel resets, checking
the exact bytes cleared and that other state, including delay indices, survives.
Pointer authentication is removed and authenticated calls use ordinary `blr`;
`bzero` is replaced by an equivalent memory write. This does not boot an OS or
execute the whole AudioUnit host. Digests/results are saved in
`tests/fixtures/effect_bypass_reference.json`; bounded assembly is in `bypass/`.

Native graph tests exercise dry and wet paths with existing filter/delay tails:
reactivation matches clean channel state, while repeated active writes preserve
tails. Reset/apply allocate no memory. Scheduled tests verify four gate changes
inside one stereo callback, exact pass-through windows and agreement with manual
slices. Reverb has separate preserved-tail and copying tests.

The graph is always fully initialized before applying a snapshot. Its changes
run on one audio thread. Concurrent AU property writes, outer-host silent-buffer
propagation and continuous-to-stepped scheduling are not covered by this port.
Fractional `bypa` automation is rejected at preparation; only the recovered
binary style gates are supported. The native graph is not yet wired to Android
playback, and this work does not change the existing lmg31 crossfade.
