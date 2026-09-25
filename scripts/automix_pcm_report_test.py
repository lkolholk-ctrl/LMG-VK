#!/usr/bin/env python3
"""Synthetic capture/report negatives; this does not run or substitute for DSP."""
from __future__ import annotations
from array import array
import copy
import json
from pathlib import Path
import struct
import tempfile
import unittest
import automix_verify_pcm_report as guard


def float32(v: float) -> float:
    return struct.unpack("<f", struct.pack("<f", v))[0]


def wave(values: list[float], sr: int = 8000, channels: int = 1) -> bytes:
    payload = struct.pack("<" + "f" * len(values), *values)
    fmt = struct.pack("<4sIHHIIHH", b"fmt ", 16, 3, channels, sr, sr * channels * 4, channels * 4, 32)
    body = b"WAVE" + fmt + struct.pack("<4sII", b"fact", 4, len(values) // channels)
    body += struct.pack("<4sI", b"data", len(payload)) + payload
    return b"RIFF" + struct.pack("<I", len(body)) + body


def make_fixture(root: Path) -> dict:
    frames, offset, tail = 20, 2, 4
    data = {}
    for side, count, source_frames in (("outgoing", 24, 10), ("incoming", 22, 8)):
        data[side + "-source.wav"] = [float32(.01 * (i + 1)) for i in range(source_frames)]
        data[side + "-raw.wav"] = [float32((-.08 if side == "incoming" else .125) / (i + 1)) for i in range(count)]
        data[side + "-gain.wav"] = [float32(v * .5) for v in data[side + "-raw.wav"]]
    data["mix.wav"] = [float32(data["outgoing-gain.wav"][i] + (data["incoming-gain.wav"][i - offset] if i >= offset else 0.0)) for i in range(frames)]
    report = dict(schemaVersion=1, kind="LMG_OFFLINE_PCM_BENCH", canExecute=False, playerObjects=0,
                  outputTimelines=1, sampleRate=8000, channels=1, maximumFrames=1024,
                  maximumZeroInputFrames=4096, quietWindowFrames=4, residualThreshold=1e-6,
                  effectStepSourceFrames=256, scheduled=True, pitch=1, smoothness=8,
                  coherence=False, preserveTransients=False, transitionFrames=frames,
                  incomingStartFrame=offset, diagnosticTailFrames=tail,
                  scenario="unity", styleId=9, sourceKind="SYNTHETIC_TEST_SIGNALS",
                  partitionComparisons=5, partitionTolerance=2e-5, maxPartitionError=0.0, **guard.POLICIES)
    for side in ("outgoing", "incoming"):
        raw = array("f", data[side + "-raw.wav"])
        gained = array("f", data[side + "-gain.wav"])
        source_frames = len(data[side + "-source.wav"])
        count = len(raw)
        report[side] = dict(sourceFrames=source_frames, acceptedSourceFrames=source_frames,
                            acceptedZeroFrames=300, deliveredFrames=count,
                            firstDeliveryAfterInputFrames=256, enqueueCalls=4, dequeueCalls=5,
                            backpressureCalls=1, partialReads=1, pendingFramesAtCaptureEnd=32,
                            graphEvents=3, fftSize=256, declaredScheduledLatencySeconds=0,
                            outputGainApplications=1, sourceFullyAccepted=True,
                            firstTailOutputFrame=source_frames, tailFramesObserved=count-source_frames,
                            tailObservation="RESIDUAL_AT_CAPTURE_LIMIT", raw=guard.metrics(raw, 1),
                            weighted=guard.metrics(gained, 1), rawTail=guard.metrics(raw, 1, source_frames, count),
                            rawFinalWindow=guard.metrics(raw, 1, count-4, count))
    report["mix"] = guard.metrics(array("f", data["mix.wav"]), 1)
    for name, values in data.items():
        (root / name).write_bytes(wave(values))
    (root / "report.json").write_text(json.dumps(report))
    return report


class ReportTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="automix-pcm-report-")
        self.root = Path(self.temporary.name)
        self.report = make_fixture(self.root)

    def tearDown(self):
        self.temporary.cleanup()

    def put_report(self):
        (self.root / "report.json").write_text(json.dumps(self.report))

    def bad(self):
        with self.assertRaises((guard.InvalidCapture, OSError, ValueError)):
            guard.verify(self.root)

    def test_positive_hashes_and_no_claims(self):
        result = guard.verify(self.root)
        self.assertEqual(result["status"], "CAPTURE_ACCOUNTING_VERIFIED")
        self.assertEqual(len(result["wavSha256"]), 7)
        self.assertIs(result["fullDrainProven"], False)
        self.assertIs(result["appleParityProven"], False)
        self.assertIs(result["canExecute"], False)

    def test_report_fields_fail_closed(self):
        bad_fields = {
            "schemaVersion": 2, "canExecute": True, "playerObjects": 2, "outputTimelines": 2,
            "scheduled": False, "coherence": True, "preserveTransients": True, "pitch": 2,
            "smoothness": 16, "sampleRate": 1, "channels": 3, "maximumFrames": 0,
            "effectStepSourceFrames": 0, "maximumZeroInputFrames": 2, "quietWindowFrames": 0,
            "residualThreshold": -1, "transitionFrames": 21, "incomingStartFrame": 20,
            "diagnosticTailFrames": 5, "scenario": "apple-recording", "styleId": 8,
            "sourceKind": "APPLE_PARITY_PROVEN", "partitionComparisons": 0,
            "partitionTolerance": .01, "maxPartitionError": .001, "latencyPolicy": "TRIM_HALF_FFT",
            "tailPolicy": "FULL_DRAIN_PROVEN", "gainPolicy": "GAIN_TWICE", "automationPolicy": "IO_BLOCK_GRID",
        }
        original = copy.deepcopy(self.report)
        for field, value in bad_fields.items():
            with self.subTest(field=field):
                self.report = copy.deepcopy(original)
                self.report[field] = value
                self.put_report()
                self.bad()
        self.assertEqual(len(bad_fields), 29)

    def test_track_counter_and_metric_negatives(self):
        bad_fields = {
            "acceptedSourceFrames": 11, "acceptedZeroFrames": 5000, "deliveredFrames": 23,
            "firstDeliveryAfterInputFrames": 9999, "enqueueCalls": 0, "dequeueCalls": 0,
            "backpressureCalls": 99, "partialReads": 99, "pendingFramesAtCaptureEnd": 1025,
            "fftSize": 300, "sourceFrames": 11, "outputGainApplications": 2,
            "declaredScheduledLatencySeconds": .1, "sourceFullyAccepted": False,
            "tailFramesObserved": 0, "firstTailOutputFrame": 25,
            "tailObservation": "FINAL_WINDOW_QUIET_NOT_PROOF_OF_DRAIN",
        }
        original = copy.deepcopy(self.report)
        for field, value in bad_fields.items():
            with self.subTest(field=field):
                self.report = copy.deepcopy(original)
                self.report["outgoing"][field] = value
                self.put_report()
                self.bad()
        for field in ("raw", "weighted", "rawTail", "rawFinalWindow"):
            with self.subTest(metric=field):
                self.report = copy.deepcopy(original)
                self.report["outgoing"][field]["rms"] += .001
                self.put_report()
                self.bad()
        self.assertEqual(len(bad_fields), 17)

    def test_boolean_is_not_integer(self):
        self.report["sampleRate"] = True
        self.put_report()
        self.bad()

    def test_duplicates_and_nonfinite_json(self):
        for text in ('{"schemaVersion":1,"schemaVersion":1}', '{"x":NaN}', '{"x":Infinity}'):
            (self.root / "report.json").write_text(text)
            self.bad()

    def test_nonfinite_wav(self):
        data = bytearray((self.root / "mix.wav").read_bytes())
        struct.pack_into("<f", data, 56, float("nan"))
        (self.root / "mix.wav").write_bytes(data)
        self.bad()

    def test_wav_format_extent_and_fact(self):
        original = (self.root / "mix.wav").read_bytes()
        for at in (0, 4, 20, 22, 24, 28, 32, 34, 44, 52):
            with self.subTest(offset=at):
                data = bytearray(original)
                data[at] ^= 1
                (self.root / "mix.wav").write_bytes(data)
                self.bad()

    def test_mix_not_applied_twice(self):
        data = bytearray((self.root / "mix.wav").read_bytes())
        struct.pack_into("<f", data, 56, .0)
        (self.root / "mix.wav").write_bytes(data)
        # Recompute the mix's summary to ensure the actual sample equation catches it.
        self.report["mix"] = guard.metrics(guard.wav(data, 8000, 1), 1)
        self.put_report()
        self.bad()

    def test_missing_and_symlink(self):
        target = self.root / "incoming-raw.wav"
        content = target.read_bytes()
        target.unlink()
        self.bad()
        outside = self.root / "not-the-capture.wav"
        outside.write_bytes(content)
        target.symlink_to(outside)
        self.bad()

    def test_quiet_window_is_not_drain_proof(self):
        self.report["residualThreshold"] = 1.0
        for side in ("outgoing", "incoming"):
            self.report[side]["tailObservation"] = "FINAL_WINDOW_QUIET_NOT_PROOF_OF_DRAIN"
        self.put_report()
        self.assertIs(guard.verify(self.root)["fullDrainProven"], False)

    def test_zero_before_eof_is_rejected(self):
        self.report["outgoing"]["acceptedSourceFrames"] = 9
        self.report["outgoing"]["sourceFullyAccepted"] = False
        self.put_report()
        self.bad()

    def test_metrics_signed_zero(self):
        values = array("f", [-0.0, 0.0])
        self.assertEqual(guard.metrics(values, 1)["peakFrame"], 0)
        self.assertEqual(guard.metrics(values, 1, 2, 2)["peakFrame"], 2)

if __name__ == "__main__":
    unittest.main(verbosity=2)
