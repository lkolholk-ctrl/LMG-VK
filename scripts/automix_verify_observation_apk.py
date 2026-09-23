#!/usr/bin/env python3
"""Verify packaging, byte integrity, and actual host-JNI execution; no APK extraction."""
from __future__ import annotations

import hashlib
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
from zipfile import ZipFile

SHA256 = "fe3d0a36625ccb043c519bcc5119c98190893a9911cfa58412ad60cbab04d120"
ABIS = ("arm64-v8a", "armeabi-v7a", "x86")


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
    report = root / "app/build/test-results/testDebugUnitTest/TEST-com.lmg.vk.engine.automix.NativeObservationIntegrationTest.xml"
    suite = ET.parse(report).getroot()
    cases = suite.findall("testcase")
    if len(cases) < 3 or any(c.find(tag) is not None for c in cases for tag in ("skipped", "failure", "error")):
        raise ValueError("Real host-JNI integration tests did not all execute successfully")
    print(f"Verified {len(apks)} APK(s), {len(ABIS)} JNI ABIs, catalog SHA-256, and {len(cases)} real JNI tests")


if __name__ == "__main__":
    try:
        verify(Path(__file__).resolve().parents[1])
    except (OSError, ValueError, KeyError, ET.ParseError) as error:
        print(f"AutoMix packaging verification failed: {error}", file=sys.stderr)
        raise SystemExit(1)
