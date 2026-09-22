# Lowpass reconstruction from 23A341

This implementation replaces NONE of the excluded prototype by assumption. A new
`lowpass.cpp` follows newly recovered code; the rest of DSP remains incomplete.

## Closed call chain

- `_AULopassFactory` 0x234f68a00 installs constructor callback 0x234f41814.
- Constructor installs vtable 0x284ad2670. Slot +0x240 points to kernel allocator
  0x234f68338; allocator installs kernel vtable 0x284ad06b0. Kernel slot +0x18
  points to 0x234f683bc. The embedded section has identity coefficients and zero
  history at construction; generation counter initialized to -1.
- Kernel compares generation counters, updates coefficients via 0x234f68434,
  then processes samples at 0x234f8975c.
- Coefficient helper 0x234fee538 dispatches kind 1 to 0x234fee094.

Pointer decoding was checked against slide info version 5, page size 16384,
value_add 0x180000000 in `.70.dylddata`. Authenticated targets use low 34 bits
plus value_add (not the earlier provisional low-32-bit shortcut).
Format references:
https://raw.githubusercontent.com/apple-oss-distributions/dyld/main/include/mach-o/dyld_cache_format.h
https://raw.githubusercontent.com/apple-oss-distributions/dyld/main/include/mach-o/fixup-chains.h

## Math imports resolved from code AND symbol table

Additional `.73` / `.77.dyldlinkedit` UUIDs match base cache. libsystem_m image base
0x296daf000, symtab file offset 0xa2e81f0, 447 entries; string table 0xa5bbba0.

| Branch island | Actual target | Export |
|---|---|---|
| 0x236f454f0 | 0x296db4bc0 | ___exp10 |
| 0x236f45690 | 0x296daf7b4 | _cos |
| 0x236f45c80 | 0x296daf648 | _sin |

LP update narrows sample rate through float, clamps cutoff at 10, then computes
`normalized = min(2*cutoff / float(sampleRate), 0.99)`,
`angle = normalized*pi`, `Q = exp10(double(float(resonanceDb))*0.05)`.
Coefficient dispatcher uses `alpha=sin(angle)/(Q+Q)`, numerator `(1-cos(angle))*0.5`,
then reciprocal denominator multiplication in the order of the disassembly.
Storage is a1,a2,b0,b1,b2. Wrapper upper angle 0x234ffcb80 is 3.1101767270538954,
identical to 0.99*pi for these constants. No extra Q clamp or sqrt(2) factor exists.

## Sample processing

The machine routine primes four samples, iterates in groups of five, emits the
four primed outputs, then handles 0..4 scalar samples. Main-path feedforward order
starts with separately rounded b0*x then fma(b1,x1,...); scalar tail starts with
separately rounded b1*x1 then fma(b0,x,...). Remaining terms use explicit FMAs.
The C++ loop preserves these per-sample orders without reproducing pointer
prefetching. Contraction is disabled except explicit std::fma calls.

History (two prior inputs, two prior double outputs) is retained at full precision
within the call. At the end of EACH call, including an empty call, history values
survive only for 1e-15 < abs(value) < 1e15; others become zero. Float PCM output is
not clipped. Constants are read at 0x234ffd348/0x234ffd350. Consequently block
partition equality is NOT an invariant of the recovered kernel. Tests explicitly
exercise that difference and the main/scalar FMA rounding difference.

## Evidence limits and verification

Tests include analytic quarter-rate cases (Q=1 and Q=10), independent 70-digit
formula values, clamp/rate-conversion cases, impulse response, exact in-place
processing, independent state and invalid host buffers. No CPU/device emulator
was run. These tests are not an Apple playback capture comparison.

C++ uses the same recovered math via platform std::pow(10,x), sin, cos and fma;
platform transcendental rounding is not claimed bit-identical to libsystem_m.
Host input validation is separate from Apple behavior for malformed parameters.
The filter is not yet connected to the graph, automation publication or Media3.
AU parameter scheduling and full graph lifecycle still require implementation.

Selected disassemblies with code SHA256 are retained in `evidence/lowpass/`.
Linkedit decompression peak child RSS: 228744 KiB; 1.21 seconds. No full-cache
analysis, Android build, JUCE or Oboe was used.
