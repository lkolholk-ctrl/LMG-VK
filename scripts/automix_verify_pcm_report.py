#!/usr/bin/env python3
"""Read-only integrity/accounting check of the host PCM bench capture.

This is NOT an Apple fidelity oracle. It independently reads float WAV files,
recomputes measurements and the one-timeline mix, and checks honest tail/latency
labels. The DSP and block-partition tests must be run separately.
"""
from __future__ import annotations
import argparse
from array import array
import hashlib
import json
import math
import os
from pathlib import Path
import stat
import struct
import sys
from typing import Any

MAX_SAMPLES = 32 * 1024 * 1024
NAMES = ("outgoing-source.wav", "incoming-source.wav", "outgoing-raw.wav",
         "incoming-raw.wav", "outgoing-gain.wav", "incoming-gain.wav", "mix.wav")
POLICIES = {
    "tailPolicy": "BOUNDED_ZERO_INPUT_AND_FIXED_CAPTURE",
    "latencyPolicy": "NO_EXTRA_TRIM_AFTER_NATIVE_SCHEDULED_MODE",
    "gainPolicy": "FIRST_FULL_CONTINUOUS_OUT_GAIN_ONCE_AFTER_TIMEPITCH",
    "automationPolicy": "FIXED_SOURCE_GRID_PLUS_POINT_BOUNDARIES",
}

class InvalidCapture(ValueError):
    pass

def need(condition: bool, message: str) -> None:
    if not condition:
        raise InvalidCapture(message)

def read_regular(path: Path, limit: int) -> bytes:
    need(not path.is_symlink(), f"Symlink not accepted: {path.name}")
    fd = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    with os.fdopen(fd, "rb") as f:
        before = os.fstat(f.fileno())
        need(stat.S_ISREG(before.st_mode) and 0 <= before.st_size <= limit, "Input size/type")
        data = f.read(limit + 1)
        after = os.fstat(f.fileno())
    need(len(data) == before.st_size and (before.st_size, before.st_mtime_ns) ==
         (after.st_size, after.st_mtime_ns), "Input changed while reading")
    return data

def unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result = {}
    for key, value in pairs:
        need(key not in result, f"Duplicate JSON key: {key}")
        result[key] = value
    return result

def integer(value: Any, name: str, minimum: int = 0, maximum: int = MAX_SAMPLES) -> int:
    need(type(value) is int and minimum <= value <= maximum, f"Invalid integer: {name}")
    return value

def number(value: Any, name: str, minimum: float = -math.inf, maximum: float = math.inf) -> float:
    need(type(value) in (float, int), f"Invalid number: {name}")
    value = float(value)
    need(math.isfinite(value) and minimum <= value <= maximum, f"Invalid finite range: {name}")
    return value

def wav(data: bytes, sample_rate: int, channels: int) -> array:
    need(len(data) >= 44 and data[:4] == b"RIFF" and data[8:12] == b"WAVE", "Not RIFF WAVE")
    need(struct.unpack_from("<I", data, 4)[0] + 8 == len(data), "RIFF length mismatch")
    offset, seen_format, samples, fact = 12, False, None, None
    while offset < len(data):
        need(offset + 8 <= len(data), "Truncated WAVE chunk")
        tag, size = struct.unpack_from("<4sI", data, offset)
        offset += 8
        need(size <= len(data) - offset, "WAVE chunk exceeds file")
        body = memoryview(data)[offset:offset + size]
        if tag == b"fmt ":
            need(not seen_format and size >= 16, "Duplicate/short format")
            fmt, c, sr, byte_rate, stride, bits = struct.unpack_from("<HHIIHH", body)
            need((fmt, c, sr, byte_rate, stride, bits) ==
                 (3, channels, sample_rate, sample_rate * channels * 4, channels * 4, 32), "WAVE format mismatch")
            seen_format = True
        elif tag == b"fact":
            need(fact is None and size == 4, "Invalid fact chunk")
            fact = struct.unpack_from("<I", body)[0]
        elif tag == b"data":
            need(seen_format and samples is None and size % (4 * channels) == 0, "Data order/stride")
            need(size // 4 <= MAX_SAMPLES, "PCM limit")
            samples = array("f")
            need(samples.itemsize == 4, "Float32 platform required")
            samples.frombytes(body)
            if sys.byteorder != "little":
                samples.byteswap()
        offset += size + (size & 1)
        need(offset <= len(data), "Missing chunk padding")
    need(seen_format and samples is not None, "WAVE data/format missing")
    need(fact is None or fact == len(samples) // channels, "WAVE fact frame count")
    need(all(math.isfinite(v) for v in samples), "Nonfinite PCM")
    return samples

def metrics(samples: array, channels: int, begin: int = 0, end: int | None = None) -> dict[str, Any]:
    end = len(samples) // channels if end is None else end
    need(0 <= begin <= end <= len(samples) // channels, "Invalid metric range")
    first, stop = begin * channels, end * channels
    peak, peak_frame, overloads = 0.0, begin, 0
    for i in range(first, stop):
        value = abs(samples[i])
        if value > peak:
            peak, peak_frame = value, i // channels
        overloads += value > 1.0
    size = stop - first
    mean = math.fsum(samples[i] for i in range(first, stop)) / size if size else 0.0
    rms = math.sqrt(math.fsum(float(samples[i]) ** 2 for i in range(first, stop)) / size) if size else 0.0
    return dict(frames=end - begin, peak=peak, rms=rms, mean=mean,
                peakFrame=peak_frame, samplesAboveUnity=overloads)

def check_metrics(actual: Any, expected: dict[str, Any]) -> None:
    need(type(actual) is dict and set(actual) == set(expected), "Metrics schema mismatch")
    for name, value in expected.items():
        if type(value) is int:
            need(integer(actual[name], name) == value, f"Wrong measured {name}")
        else:
            measured = number(actual[name], name)
            need(math.isclose(measured, value, rel_tol=2e-9, abs_tol=1e-11), f"Wrong measured {name}")

def verify(directory: Path) -> dict[str, Any]:
    need(directory.is_dir() and not directory.is_symlink(), "Capture must be a nonsymlink directory")
    raw_report = read_regular(directory / "report.json", 128 * 1024)
    report = json.loads(raw_report, object_pairs_hook=unique_object,
                        parse_constant=lambda x: (_ for _ in ()).throw(InvalidCapture(f"Nonfinite JSON: {x}")))
    need(type(report) is dict, "Report object required")
    for key, value in {"schemaVersion": 1, "kind": "LMG_OFFLINE_PCM_BENCH", "canExecute": False,
                       "playerObjects": 0, "outputTimelines": 1, "scheduled": True,
                       "pitch": 1, "smoothness": 8, "coherence": False, "preserveTransients": False,
                       "partitionComparisons": 5, **POLICIES}.items():
        need(type(report.get(key)) is type(value) and report.get(key) == value, f"Unsupported {key}")
    sr = integer(report.get("sampleRate"), "sampleRate", 8000, 192000)
    ch = integer(report.get("channels"), "channels", 1, 2)
    maximum = integer(report.get("maximumFrames"), "maximumFrames", 1, 16384)
    integer(report.get("effectStepSourceFrames"), "effectStepSourceFrames", 1, 16384)
    zero_budget = integer(report.get("maximumZeroInputFrames"), "maximumZeroInputFrames", 0, MAX_SAMPLES // ch)
    quiet = integer(report.get("quietWindowFrames"), "quietWindowFrames", 1)
    threshold = number(report.get("residualThreshold"), "residualThreshold", 0)
    frames = integer(report.get("transitionFrames"), "transitionFrames", 1, MAX_SAMPLES // ch)
    offset = integer(report.get("incomingStartFrame"), "incomingStartFrame", 0, frames - 1)
    tail = integer(report.get("diagnosticTailFrames"), "diagnosticTailFrames", 0, MAX_SAMPLES // ch)
    need(report.get("scenario") in ("unity", "stretch"), "Unknown fixture scenario")
    expected_style = 9 if report["scenario"] == "unity" else 12
    need(integer(report.get("styleId"), "styleId") == expected_style, "Wrong fixture style")
    need(report.get("sourceKind") in ("SYNTHETIC_TEST_SIGNALS", "CALLER_CUED_PCM_WITH_SYNTHETIC_ANALYSIS"), "Unproven source attribution")
    tolerance = number(report.get("partitionTolerance"), "partitionTolerance", 0, 2e-5)
    need(tolerance == 2e-5, "Unexpected partition tolerance")
    number(report.get("maxPartitionError"), "maxPartitionError", 0, tolerance)
    captures, hashes = {}, {}
    for name in NAMES:
        data = read_regular(directory / name, MAX_SAMPLES * 4 + 1024 * 1024)
        hashes[name] = hashlib.sha256(data).hexdigest()
        captures[name] = wav(data, sr, ch)
    for side, count in (("outgoing", frames + tail), ("incoming", frames - offset + tail)):
        item = report.get(side)
        need(type(item) is dict, "Track report required")
        source, raw, gained = (captures[f"{side}-{part}.wav"] for part in ("source", "raw", "gain"))
        source_frames = len(source) // ch
        need(len(raw) == len(gained) == count * ch, "Capture length mismatch")
        need(integer(item.get("sourceFrames"), "sourceFrames") == source_frames, "Source count")
        accepted = integer(item.get("acceptedSourceFrames"), "acceptedSourceFrames", 0, source_frames)
        zeros = integer(item.get("acceptedZeroFrames"), "acceptedZeroFrames", 0, zero_budget)
        need(not zeros or accepted == source_frames, "Zero input before source EOF")
        need(type(item.get("sourceFullyAccepted")) is bool and item["sourceFullyAccepted"] == (accepted == source_frames), "False source completion")
        need(integer(item.get("deliveredFrames"), "deliveredFrames") == count, "Delivery accounting")
        integer(item.get("firstDeliveryAfterInputFrames"), "firstDeliveryAfterInputFrames", 1, accepted + zeros)
        enqueue = integer(item.get("enqueueCalls"), "enqueueCalls", 1)
        dequeue = integer(item.get("dequeueCalls"), "dequeueCalls", 1)
        integer(item.get("backpressureCalls"), "backpressureCalls", 0, enqueue)
        integer(item.get("partialReads"), "partialReads", 0, dequeue)
        integer(item.get("pendingFramesAtCaptureEnd"), "pendingFramesAtCaptureEnd", 0, maximum)
        integer(item.get("graphEvents"), "graphEvents", 0, 131072)
        fft = integer(item.get("fftSize"), "fftSize", 256, 8192)
        need(fft & (fft - 1) == 0, "FFT geometry")
        need(number(item.get("declaredScheduledLatencySeconds"), "declaredScheduledLatencySeconds") == 0.0, "Scheduled latency claim")
        need(integer(item.get("outputGainApplications"), "outputGainApplications") == 1, "Gain ownership")
        eof = integer(item.get("firstTailOutputFrame"), "firstTailOutputFrame", 0, count)
        need(integer(item.get("tailFramesObserved"), "tailFramesObserved") == count - eof, "Tail length accounting")
        raw_m = metrics(raw, ch)
        gain_m = metrics(gained, ch)
        tail_m = metrics(raw, ch, eof, count)
        final_m = metrics(raw, ch, count - min(count, quiet), count)
        for key, expected in (("raw", raw_m), ("weighted", gain_m), ("rawTail", tail_m), ("rawFinalWindow", final_m)):
            check_metrics(item.get(key), expected)
        expected_tail = ("NOT_CAPTURED" if eof == count else "RESIDUAL_AT_CAPTURE_LIMIT"
                         if final_m["peak"] > threshold else "FINAL_WINDOW_QUIET_NOT_PROOF_OF_DRAIN")
        need(item.get("tailObservation") == expected_tail, "Unfounded tail-completion claim")
    mix = captures["mix.wav"]
    need(len(mix) == frames * ch, "Mixed output length")
    a, b = captures["outgoing-gain.wav"], captures["incoming-gain.wav"]
    for i, value in enumerate(mix):
        expected = float(a[i])
        if i >= offset * ch:
            expected = struct.unpack("<f", struct.pack("<f", expected + b[i - offset * ch]))[0]
        need(value == expected, "Mix differs from single post-gain sum at declared offset")
    check_metrics(report.get("mix"), metrics(mix, ch))
    return {"status": "CAPTURE_ACCOUNTING_VERIFIED", "canExecute": False,
            "fullDrainProven": False, "appleParityProven": False,
            "sampleRate": sr, "channels": ch, "frames": frames,
            "reportSha256": hashlib.sha256(raw_report).hexdigest(), "wavSha256": hashes}

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    try:
        result = verify(args.directory)
    except (OSError, ValueError, KeyError, TypeError, struct.error, OverflowError) as error:
        print(f"PCM verification FAILED: {error}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
