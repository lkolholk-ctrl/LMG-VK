# Cloud video events to internal Flex events

This adapter supplies MusicKitInternal-style Flex events from the raw decoder's
`CloudVideoEvents`. It does not assert a beat-grid source or implement a planner
scoring predicate. Those are separate downstream operations.

## Exact source

MusicKitInternal `Event.init?(time:score:)` at `0x1d3e81ad4` receives a signed
64-bit score in x0, time in d0 and a 24-byte result destination in x8. Its unsigned
range checks select these cases:

| Inclusive score range | Raw TimeScale tag | Amplitude |
|---|---|---|
| 200–299 | 0, short | `(score % 100) / 100.0` |
| 400–499 | 1, medium | same |
| 600–699 | 2, long | same |
| 800–899 | 3, extra long | same |

All other scores produce optional-none tag 4. `sdiv`/`msub` compute the integer
remainder, `scvtf` converts it to double, and `fdiv` divides by exactly 100.0.
Time is stored unchanged; the initializer neither normalizes nor checks it.

The cloud converter at `0x1d3e5ef4c` inlines that operation. Key instructions:

- `0x1d3e5f040` loads the time array count; `0x1d3e5f05c` loads time[index].
- `0x1d3e5f090..098` checks index against score count. On failure execution goes
  to `brk #1` at `0x1d3e5f1d0`. It does **not** truncate the traversal to the
  smaller array length.
- `0x1d3e5f0a0` loads a signed 64-bit score payload. No float-to-integer
  conversion occurs in this converter. The subsequent arithmetic is signed.
- `0x1d3e5f0ac..0f4` applies the same accepted ranges; unsupported scores skip
  append but still consume their time-array index.
- `0x1d3e5f114..144` writes time, the one-byte case tag and amplitude. Appended
  records have a 24-byte stride.
- `0x1d3e5f148..150` increments the index until the **time** count is reached.
  Additional score entries are never read. Time ordering and duplicates remain.

Copies of these exact disassemblies are under `evidence/planner_flex/`.

## Host boundary

The transport decoder preserves cloud JSON numbers as doubles. To bridge to the
original signed64 score representation, consumed values must be integral, finite
and within `[-2^63, 2^63)`. Fractional values are rejected, not truncated. The
upper exclusive bound avoids casting rounded double(INT64_MAX) with undefined
behavior. This is LMG validation of a raw model; it is not a recovered Apple
JSON-number conversion rule. Scores outside the accepted event ranges are then
discarded by the original event initializer behavior.

Missing either parallel array returns unavailable, distinct from a present empty
event list. A short score array raises invalid_argument instead of reproducing
Swift's fatal bounds trap. Extra scores are ignored, including uninterpreted
trailing values. Nonfinite time is rejected by the cloud adapter; the standalone
initializer retains the original unrestricted time-copy behavior. Negative,
duplicate and non-monotonic finite times are neither clamped nor reordered.

## Original-instruction verification

`tests/fixtures/planner_flex.bin` contains 1,045 executions of the unmodified
initializer loaded from the original MusicKitInternal Mach-O into Unicorn ARM64.
No hooks, patched instructions or substituted arithmetic were used. The score
set covers every integer -20 through 1020, INT64_MIN, INT64_MAX and ±2^62, across
four time payloads including negative zero. Results include the optional tag,
copied time and amplitude. Source and fixture hashes plus the binary layout are
recorded in `planner_flex_reference.json`.

The C++ test compares all present fields bit for bit against those records and
checks array-length asymmetry, missing versus empty arrays, preserved ordering,
fractional/range/nonfinite failures and raw decoder integration. An optional
local captured-response test reads the existing MediaAPI sample without copying
its full payload into the repository. The original full cloud getter/allocation
path was inspected in disassembly, not executed by the initializer harness.
