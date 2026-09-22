# AudioQueue rate-history conversion

`QueueRateHistory` ports the arithmetic of AudioToolbox
`AQRateChangeHistory::ConvertToUnscaled` (0x1b9056524) and
`ConvertToScaled` (0x1b90661e8). Complete bounded listings are in
`time_pitch/queue_history_0x1b9056524.asm` and
`time_pitch/queue_history_0x1b90661e8.asm`.

Records contain two integer frame anchors, a double scaled origin and a double
rate. Both directions use upper-bound search, retain the last equal anchor and
extrapolate from the first record before its start. Unscaling searches with
FCVTAS, computes a double FMA and returns FCVTAS; an empty table uses FCVTZS.
Scaling uses wrapping integer subtraction, conversion to double, division,
separate addition and FRINTA. Both update the unscaled high-water mark.
The class is single-owner and takes an immutable snapshot; it does not emulate
the original reader-lock bank or produce new rate-history records.

`tools/queue_rate_history_reference.py` executes original arithmetic spans
0x1b9056550–0x1b90565f4 and 0x1b9066228–0x1b90662cc under Unicorn. Only locking
and return sequences are excluded; no arithmetic is replaced. The fixture has
1024 pairs (2048 conversions), with empty tables, duplicate anchors, half-frame
boundaries, fractional origins, rates 0.03125–32, extrapolation, saturation and
signed-overflow inputs. The test compares integer results, double output bits,
returned rate and high-water state. Host ASan/UBSan run passed; leak detection
was disabled because of the environment's ptrace limitation.

This is the queue clock mapping used by timestamp preparation, not TimePitch's
quadratic time map. Production rate-history creation, queue-offset CMTime
subtraction and the Media3 event-clock adapter are still to be connected.
