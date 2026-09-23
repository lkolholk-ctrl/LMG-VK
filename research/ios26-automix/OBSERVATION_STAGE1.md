# AutoMix observation foundation: changes 1 and 2

## Scope and invariant

The production integration point remains `engine/AudioService.kt`. This change
builds the native bridge and the cloud/catalog preparation APIs but does **not**
attach a new sink, effect, PCM processor, player, or planner to the running service.
`AudioService`, `PlayerAudioChain`, existing DSP and `planner_scoring.cpp` are unchanged.
A parsed song or loaded style catalog is **not** a validated transition plan.

The last implementation request explicitly targets the branch-pinned
`1.5.1-lmg30` API. The separate `media3-lmg` 1.11.0 fork is not modified or
downgraded. `AutoMixStreamIdentity.from(timeline, mediaPeriodId)` does not depend
on `AudioSinkConfig` and must be fed actual renderer-period context. Legacy
`configure(Format, int, int[])` cannot identify a playlist occurrence; missing
context stays unresolved. Never infer it from `Format.id` or the player's
current item.

No Oboe, JUCE, TFLite, or local analysis model is added or used by these APIs.
Existing unrelated legacy dependencies/code have not been removed in this change.

## Data path

* `AppleSongAnalysisParser.parse(bytes, requestedSongId)` calls the existing C++
  `decodeMediaApiSongAnalysis` through `NativeObservationBridge`. C++ resolves
  the requested song and included analysis resources and emits schema version 1
  metadata. Kotlin deserializes only that transport snapshot, not the cloud schema.
* Cloud units, unknown strings, false/zero values, absent fields, explicit empty
  arrays and unresolved resource linkage are preserved. No beat grid, tempo,
  eligibility, unit conversion or fallback analysis is inferred in Kotlin.
* `TransitionStyleCatalog.loadBundled(context.assets::open)` reads bounded data,
  validates the fixed SHA-256, and invokes the existing native style parser.
  Kotlin exposes descriptors and defensive copies of the source bytes for future
  native planning. It does not compile effects or reproduce style mathematics.
* Both APIs belong on a worker/control thread, never the audio callback. There is
  no HTTP request, authorization change, payload logging, or player access here.
  Errors are stable enum reasons, without server payloads or URLs.

The canonical 55,905-byte resource remains
`research/ios26-automix/TransitionStyles.json`, SHA-256
`fe3d0a36625ccb043c519bcc5119c98190893a9911cfa58412ad60cbab04d120`.
CMake, Gradle asset staging, runtime loading and APK verification pin this hash.
The native CTest style suite now receives the actual catalog path. The new
observation suite verifies native snapshot transport and the 14/55/74 catalog.

## Build and verification

Host (JDK available for JNI):

```sh
cmake -S native/automix -B build_native -DCMAKE_BUILD_TYPE=Debug \
  -DLMG_AUTOMIX_JNI=ON -DLMG_AUTOMIX_TESTS=ON
cmake --build build_native --parallel 2
ctest --test-dir build_native --output-on-failure
```

Android (SDK/NDK and branch-pinned media3 Maven artifacts installed):

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug \
  -PautomixHostLibraryPath="$PWD/build_native/android"
python3 scripts/automix_verify_observation_apk.py
```

Without the host-library property, the dedicated JNI integration class is
explicitly skipped for ordinary local JVM runs. AutoMix CI sets the property,
requires the real JNI probe, and rejects skipped/missing/failed JNI test reports.
It also verifies the exact catalog bytes in the APK and the JNI library for
arm64-v8a, armeabi-v7a and x86. Native tests are not packaged in the app.

## Remaining work (not claimed by this change)

Validate against real captures for the user's public source and recorded song
versions; implement native-backed plan validation and lifecycle observation in
`AudioService`; bind renderer identities across queue changes and seeks; then
separately implement and verify audible transitions. Android device playback,
ABI loading, latency and PCM parity require device tests and are not implied by
host or packaging checks.
