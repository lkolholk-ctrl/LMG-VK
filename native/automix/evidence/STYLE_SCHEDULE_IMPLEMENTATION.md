# Resource styles to continuous automations

`compileContinuousStyle` now produces the per-side continuous automation list
from the existing strict resource parser, a planner-supplied parent window,
planner playback-rate endpoints and mapped values. It ports the confirmed
portions of `0x272210660`, preserving resource order and separate automation
records (including repeated descriptors), nested placement/ramp timing, and the
implicit bypass gate.

## Descriptor catalog and name mapping

All 29 AutomationEffectParameter descriptors were extracted by executing their
original ARM initializers, with no runtime/math hooks. The tool
`tools/parameter_catalog_reference.py` maps the source __TEXT and writable
initializer globals and reads each resulting 56-byte structure. It decodes both
Swift inline strings and literal string pointers. The binary SHA, initializer
addresses, raw descriptor bytes and decoded fields are saved in
`scheduling/parameter_catalog.json`. `parameter_catalog.cpp` preserves the
resulting values without normalization.

The previously unresolved string at descriptor +0x28 is now identified as
**styleParameterID**: the Codable key decoder at `0x27226bf98` returns key 3 for
the literal at `0x272291640`. `0x27226bd5c` searches the allCases array using this
string (array item +0x28, i.e. array storage +0x48). Thus JSON uses names such as
`fx_reverb_high_frequency_decay_time`, which resolve to graph ID `RVhf`.
`styleEffectParameter` and `effectParameter` deliberately expose the two distinct
lookup domains. No invented aliases are accepted.

Source oddities are retained: LP1f/LP2f default 22000 exceeds their declared
maximum 21829.5; some upper range endpoints include small offsets (40.01, 2.01,
20.01). These are descriptor metadata, not instructions to clamp all style values.
DSP kernels retain their separately recovered parameter handling.

## Builder behavior

For each resource ramp, the builder resolves the descriptor and the recovered
two-level time window. Ordinary endpoint values use a mapped entry when present,
then their fallback; unresolved mandatory mappings fail. This follows
`0x27224b6b8` (its missing mandatory-value path throws SmartTransitionsError 13).
Ordinary ramps retain their curve at both generated points. For the complete
canonical ts_rate descriptor, `0x272210890..0x272210978` uses the caller's rate
endpoints and forces both curve bytes to 0x80, ignoring resource endpoint values.
The port preserves that special path. Finite values and positive playback rates
are explicit LMG preparation-time guards.

Finally `withStyleBypass` implements the already recovered full-descriptor check
and optional gate insertion. Empty instruction lists still receive the gate for
a nonempty parent interval; zero-width parents do not. No output-gain automation
is invented when missing.

## Verification and remaining scope

ASan/UBSan style tests now compile both sides of all 14 raw resource styles
(55 instructions, 74 automations), resolving all style names against the extracted
catalog and checking generated window bounds and bypass boundaries. Tests also
cover mapped override/fallback/missing values, planner ts_rate override, separate
duplicate automations and empty intervals. All 332 original time references
continue to pass. The normalized arithmetic has its own 126 ARM block fixtures.
Full Swift builder output has not been replayed end-to-end; the new integration
tests are source-derived behavior checks around those independently verified
components.

Parent-window/cue selection and rate endpoint calculation still belong to the
planner. Event cadence and delivery, AU bypass lifecycle, actual TimePitch audio
processing and JNI/Media3/app integration remain incomplete. This is a continuous
schedule compiler for supplied planner inputs, not a completed AutoMix product.
