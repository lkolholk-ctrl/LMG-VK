# Reverb block-boundary bypass

`ReverbEffect` wraps the recovered tank for serialized audio-thread property
updates. TrackGraph now uses this wrapper. Whole-graph bypass also includes the
different filter/delay reset behavior described in EFFECT_BYPASS_IMPLEMENTATION.md.

Source: libEmbeddedSystemAUs, iOS 23A341. Constructor `0x234f3fda4`
initializes both bytes at +0x230/+0x231 to zero. Property setter
`0x234fe4d2c`, global property 21, writes only +0x230, normalized to bool.
It neither clears the tank nor updates +0x231. The bounded setter disassembly
and its digest are saved in `reverb/234fe4d2c.asm`.

Process `0x234fe37c8` checks +0x230 before touching tank state. If bypassed,
it copies input to output and returns through `0x234fe3f90`. Mono-to-stereo
duplicates input. This return does **not** write the previous-state byte +0x231.
The active path tests that byte for reset at `0x234fe3928`; its tail selects
the fade at `0x234fe3fa8` and writes +0x231 at `0x234fe3fe0`.

Consequently, starting with the original initialized flags and making property
changes only between calls, +0x231 stays zero. Toggling bypass does not invoke
either reset or fade. The tank is frozen during bypass and resumes unchanged.
This is not an assumption based on the presence of a fade routine: the original
setter and process control flow were executed for 21 calls covering repeated
toggles and all three supported channel layouts. The harness skips the active
DSP body, intercepts bus lookup/memcpy, and records reset/fade calls. It is a
control-flow check, **not** an end-to-end AudioUnit execution. Raw tank DSP has
separate original-ARM fixtures.

`tools/reverb_bypass_reference.py` regenerates
`tests/fixtures/reverb_bypass_reference.json`; it uses bounded instruction
execution, without booting an operating system or Android emulator. Only PAC
entry/return instructions are patched. Source code hashes are in the report.

The C++ tests check bypass copying, mono duplication, in-place operation, no
callback allocation, invalid buffers, zero frames, explicit reset, and bitwise
tail continuity against a tank that receives no bypassed frames. Parameter
values are not evaluated on the bypass path; they take effect on active render.
The wrapper has no concurrent setters. Apple's mid-render property-change
paths and fade routine are deliberately outside this serialized contract.
