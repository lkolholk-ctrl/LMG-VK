# Matrix reverb reconstruction

Source: libEmbeddedSystemAUs.dylib, iOS 23A341, same verified cache image as the
LP/HP/delay evidence. This implements the reached AUMatrixReverbLite algorithm,
not the unrelated LexiPlate symbols in libAudioDSP or the excluded prototype.
Factory `0x234fe55e8` constructs `0x234f3fda4`; its vtable `0x284ad57c8`
dispatches initialization at +0x38 to `0x234fe5200`, processing at +0xb8 to
`0x234fe37c8`. The latter selects stereo `0x234fe431c`, mono `0x234fe4834`,
or mono-to-stereo `0x234fe45b0`. Bounded disassemblies are in `reverb/`.

## Geometry

Initialization allocates a 0x4f0-byte algorithm object containing 16 records of
0x48 bytes, beginning at +0x70. `0x234fe4068` computes a geometric progression
with factor `pow(double(float(max/min)), 1/15)`. The 14 interior delays receive
an exponent perturbation `(random/2147483647 - .5)*.8`. Their seconds narrow
to float before `floor(fma(rate, seconds, .5))`. End delays use the original
float min/max. `0x234fe4bcc` chooses the first prime at least this sample count,
allocates a power-of-two ring, and stores actual seconds as prime/rate.
One subsequent pass advances a delay to the next prime if it equals its previous
neighbor. It does not enforce global uniqueness (equal min/max can alternate).

The imported RNG calls branch to `0x18e6f2804` and `0x18e6a67d4` in .05.
Their default configuration, read from .33 data at `0x1eae9f418`, is type 3,
31 words, separation 3. Initialization uses the recovered signed Park-Miller
step (zero substitution 123459876), followed by 310 additive-generator steps.
The implementation keeps this state locally; it does not alter application libc
state. A nondefault Apple process-global initstate or concurrent external RNG
calls are outside this deterministic reconstruction.

## Decay and samples

`0x234fe41c8` caps high decay at low decay, evaluates float low/high division
before promoting it to double, then derives each line's damping and feedback:

```
exponent = -3 * actualDelaySeconds / low
amplitude = max(exp10(exponent), .0001)
damping = min((1 - double(float(low/high))^2) * (exponent * .575646273248512), .99)
feedback = amplitude * (1 - damping) * .25
```

Each line uses float feedback/damping/history. Read all 16 lines first, apply
four ordinary sum/difference butterfly stages, then write input plus the matrix
outputs back into the rings. Stereo input is `(left+right)*.5`; mono input is
unchanged. Stereo wet output selects matrix indices 1 and 2; mono selects 1.
Two-tap output compensation and reciprocal sum of line feedbacks use the exact
narrowing/operation order in the assembly. Wet interpolation is linear:
`dry + wet * (processed - dry)`. It is not the delay's square-root wet/dry law.
Gain is `exp10f(float(gainDb * .05f))`. No implicit FMA is allowed in sample math.

Relevant external branches were resolved through .49:

- `0x236f45bd0` -> `0x296db3060` (`_pow`).
- `0x236f454f0` -> `0x296db4bc0` (`___exp10`).
- `0x236f45500` -> `0x296db6de8` (`___exp10f`), confirmed in libsystem_m nlist.

C++ pow/powf implements the same functions; Apple libm ULP identity is not claimed.
Reset clears the rings, histories and output compensation memories, sets write
to zero and read to `(-delay)&mask` (`0x234fe3ff4`). AU writes to parameter IDs
2..6 increment a generation counter (`0x234fe4cec`); the processing wrapper then
reinitializes and resets the tank. Consequently changed decay values clear our
tank. Repeated writes of the same decay value still require a scheduler reset;
the value-only kernel API cannot detect such write events. Wet/gain preserve tails.

## Verification and boundaries

`tools/reverb_binary_reference.py` runs the original bounded ARM routines through
Unicorn, including the original RNG initializer/generator, geometry loop, decay
math and sample kernels. Only PAC/stack infrastructure and allocation/libm calls
are replaced. Allocation uses the original 6542-entry prime table. No Android
or iOS system is booted. Execution took under one second here.

Checked-in fixture hashes, code hashes, settings and prime-delay arrays are in
`tests/fixtures/reverb_reference.json`. The C++ tests compare every output float
bit against those fixtures (4096 frames each, stereo/mono/mono-to-stereo), and
check block splitting, exact in-place processing, reset, impulse latency,
parameter-change tail rules, invalid calls and zero process-time allocations.
Both coefficient preparations use host pow; these are algorithm/kernel comparisons,
not recordings of AudioUnit execution on a physical iPhone.

The kernel supports planar mono/stereo. The AU wrapper's additional multichannel
bus layouts, bypass fade and tail/property machinery are not exposed by this API.
Geometry changes require preparation outside the callback; arbitrary AU geometry
write events are not implemented by this class. Host bounds (8..192 kHz, min delay
>= .0001 s, max <= 1 s; positive bounded decay and gain) are explicit resource/input
limits, not inferred Apple clamps. Full graph scheduling/integration remains work.
