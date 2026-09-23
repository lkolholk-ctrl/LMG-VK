#!/usr/bin/env python3
"""Verify packaging, byte integrity, and actual host-JNI execution; no APK extraction."""
from __future__ import annotations

import hashlib
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
from zipfile import BadZipFile, ZipFile

SHA256 = "fe3d0a36625ccb043c519bcc5119c98190893a9911cfa58412ad60cbab04d120"
ABIS = ("arm64-v8a", "armeabi-v7a", "x86")


def verify_test_report(report: Path, minimum_cases: int) -> int:
    """Reject missing, malformed, skipped, failed and empty test executions."""
    suite = ET.parse(report).getroot()
    if suite.tag != "testsuite":
        raise ValueError(f"Not a JUnit testsuite: {report.name}")
    cases = suite.findall("testcase")
    if len(cases) < minimum_cases:
        raise ValueError(f"Too few executed tests: {report.name}")
    names = [case.get("name") for case in cases]
    if any(not name for name in names) or len(names) != len(set(names)):
        raise ValueError(f"Missing or duplicated test names: {report.name}")
    if any(suite.findall(".//" + tag) for tag in ("skipped", "failure", "error")):
        raise ValueError(f"Skipped or failed required tests: {report.name}")
    for field in ("failures", "errors", "skipped", "disabled"):
        if int(suite.get(field, "0")) != 0:
            raise ValueError(f"Unsuccessful suite counters: {report.name}")
    if int(suite.get("tests", str(len(cases)))) != len(cases):
        raise ValueError(f"Inconsistent suite test count: {report.name}")
    return len(cases)


def verify(root: Path) -> None:
    source = root / "research/ios26-automix/TransitionStyles.json"
    canonical = source.read_bytes()
    if hashlib.sha256(canonical).hexdigest() != SHA256:
        raise ValueError("Canonical catalog SHA-256 mismatch")
    apks = sorted((root / "app/build/outputs/apk/debug").glob("*.apk"))
    if not apks:
        raise ValueError("No Debug APK was produced")
    for apk in apks:
        with ZipFile(apk) as archive:
            names = set(archive.namelist())
            for abi in ABIS:
                name = f"lib/{abi}/liblmg_automix_jni.so"
                if name not in names or archive.getinfo(name).file_size == 0:
                    raise ValueError(f"Missing or empty AutoMix JNI for {abi}")
            asset = archive.read("assets/automix/TransitionStyles.json")
            if asset != canonical or hashlib.sha256(asset).hexdigest() != SHA256:
                raise ValueError("Packaged catalog differs from the verified source")
    reports = root / "app/build/test-results/testDebugUnitTest"
    stage1 = verify_test_report(reports / "TEST-com.lmg.vk.engine.automix.NativeObservationIntegrationTest.xml", 3)
    stage2 = verify_test_report(reports / "TEST-com.lmg.vk.engine.automix.observation.NativeMetadataProbeIntegrationTest.xml", 6)
    lifecycle = verify_test_report(reports / "TEST-com.lmg.vk.engine.automix.observation.ObservationPipelineTest.xml", 20)
    print(f"Verified {len(apks)} APK(s), {len(ABIS)} JNI ABIs, catalog SHA-256, "
          f"{stage1 + stage2} real JNI tests and {lifecycle} lifecycle tests")


if __name__ == "__main__":
    try:
        verify(Path(__file__).resolve().parents[1])
    except (OSError, ValueError, KeyError, ET.ParseError, BadZipFile) as error:
        print(f"AutoMix packaging verification failed: {error}", file=sys.stderr)
        raise SystemExit(1)
