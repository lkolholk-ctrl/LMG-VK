# Newly recovered built-in AudioUnit code (23A341)

This supersedes the assumption that only effect metadata was available. It does
not yet prove numerical equivalence or complete the DSP implementation.

- Base image table: `/srv/research/tmp/dyld_shared_cache_arm64e`.
- Image: `/System/Library/Frameworks/AudioToolbox.framework/libEmbeddedSystemAUs.dylib`,
  VM base 0x234f04000; __text 0x234f04d60..0x234ffa43c.
- Code part `.48`, branch island `.49`, linkedit `.72.dyldlinkedit` extracted
  individually from existing `appos/sys_plain/090-88480-660.dmg` into
  `/tmp/lmg-au-evidence/System/Library/Caches/com.apple.dyld/`.
- All three UUIDs checked against the base cache subcache entries and matched.
- Linkedit required the existing LZBITMAP extractor because 7z does not decode
  compression type 14. All 2700 blocks decoded, 176947200 output bytes. Measured
  child peak RSS 229060 KiB, elapsed 1.39 s; whole cache never loaded.
- Image contains 4451 nlist entries and 3644 function starts. Most internal names
  are redacted, but 32 factory exports retain names. Tool verified complete bounded
  decoding for all 32 factories. `system_aus_index.json` records addresses/provenance.

## Traced links

| Export | Address | Factory constructor callback |
|---|---|---|
| AULopassFactory | 0x234f68a00 | 0x234f41814 |
| AUHipassFactory | 0x234fc5c94 | 0x234f41a2c |
| AUMatrixReverbLiteFactory | 0x234fe55e8 | 0x234f3fda4 |
| AUDelayFactory | 0x234fdf30c | Not yet followed |
| AUNewTimePitchFactory | 0x234f9e984 | Not yet followed |

Lowpass constructor installs a vtable at 0x284ad2670; data exists in previously
extracted `.70.dylddata`. Raw chained pointers require slide-format-aware decoding;
low-32-bit rebasing is only a trace lead until independently validated.

Candidate kernel 0x234f683bc calls update 0x234f68434 when a generation counter
changes, then branches to processing 0x234f8975c. Update reads parameters 0/1,
clamps parameter 0 at 10, converts sample rate through float, computes
`2*frequency/sampleRate`, clamps above at 0.99, and multiplies by pi. Parameter 1
is multiplied by 0.05 and passed to external 0x236f454f0. That branch island
jumps to 0x296db4bc0 in libsystem_m; the exact math export is not yet resolved.
Do not replace it with guessed exp/pow. Update then invokes coefficient dispatcher
0x234fee538 with kind 1; it branches to 0x234fee094. Further tracing required.

Raw constants read from __TEXT: 0x234ffcb60 = 0.05,
0x234ffcb68 = pi (double), 0x234ffcb70 = 0.99. These are binary facts; parameter
semantics and complete processing still require call-chain verification.

Reproduce selected bounded disassembly with `tools/inspect_system_aus.py` and
`--address` options. Raw dumps currently reside in `/tmp/lmg-au-evidence/disassembly`.
No research source modified, no inferred DSP restored, no app integration performed.
Next: establish subtype registration links (especially rvb2), resolve math imports,
trace coefficient dispatcher/process and kernel construction before porting.
