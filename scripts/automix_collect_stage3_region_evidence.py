#!/usr/bin/env python3
"""Collect bounded, explicitly named local AutoMix research. No network or source writes.

Only allowlisted reports and text files named for region-function addresses are
considered. Review manifest.json before sharing. No binaries, whole archives,
credentials, song responses, or unrelated source trees are collected by design.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import tempfile
import zipfile

ADDRESSES = frozenset({
    "2722307ec", "2722315f8", "272233100", "2722335b4", "27223218c",
    "272231b68", "272233804", "272233888", "272233adc", "272233c88",
    "272232790", "2722305c8", "272234b0c", "272234e14", "272236920",
    "27223c3ac", "27223c184", "27223d324", "27223d6cc", "27223cd58",
    "272234380", "27222d644", "272231d04", "2722290a0", "27222919c",
    "272229758", "272213c20", "27222e9bc", "27222eb20",
})
REPORTS = frozenset({
    "AUTOMIX_CANDIDATE_GENERATION.md", "AUTOMIX_SCORING.md",
    "P1_AUTOMIX_CLOSURE.md", "P2_AUTOMIX_CLOSURE.md",
    "TRANSITION_PLANNER_CLEANROOM_SPEC.md", "RETRACTED_CLAIMS.md",
})
TEXT_SUFFIXES = frozenset({".asm", ".s", ".c", ".cc", ".cpp", ".h", ".hpp", ".txt", ".md", ".json"})
EXCLUDE_DIRS = frozenset({".git", ".gradle", ".idea", "build", "node_modules", "__pycache__", ".cache"})
MAX_FILES = 128
MAX_FILE_BYTES = 2 * 1024 * 1024
MAX_TOTAL_BYTES = 16 * 1024 * 1024
MAX_SCANNED = 100_000


def is_target(name: str) -> bool:
    if name in REPORTS:
        return True
    if Path(name).suffix.lower() not in TEXT_SUFFIXES:
        return False
    # Do not match a short address embedded in a longer hexadecimal identifier.
    tokens = re.findall(r"(?<![0-9a-f])(?:0x)?([0-9a-f]{9})(?![0-9a-f])", name.lower())
    return bool(ADDRESSES.intersection(tokens))


def collect(roots: list[Path], output: Path) -> dict:
    if not roots:
        raise ValueError("At least one explicit research root is required")
    if output.exists() or output.is_symlink():
        raise FileExistsError("Output already exists; refusing to overwrite")
    if not output.parent.is_dir():
        raise ValueError("Output parent directory does not exist")
    resolved = []
    for root in roots:
        if root.is_symlink() or not root.is_dir():
            raise ValueError("Each research root must be an existing nonsymlink directory")
        real = root.resolve()
        if real in resolved:
            raise ValueError("Duplicate research roots")
        resolved.append(real)
    entries, rejected, payloads = [], [], []
    total, scanned = 0, 0

    def walk_error(error: OSError) -> None:
        raise OSError("Unable to enumerate a research directory") from error

    for root_number, root in enumerate(resolved):
        for directory, dirs, files in os.walk(root, followlinks=False, onerror=walk_error):
            dirs[:] = sorted(d for d in dirs if d not in EXCLUDE_DIRS and not (Path(directory) / d).is_symlink())
            for name in sorted(files):
                scanned += 1
                if scanned > MAX_SCANNED:
                    raise ValueError("Scan limit exceeded; select a narrower research root")
                if not is_target(name):
                    continue
                path = Path(directory) / name
                relative = path.relative_to(root).as_posix()
                alias = f"root{root_number}/{relative}"
                info = path.lstat()
                if not stat.S_ISREG(info.st_mode):
                    rejected.append({"path": alias, "reason": "NOT_REGULAR_FILE"})
                    continue
                if info.st_size > MAX_FILE_BYTES:
                    rejected.append({"path": alias, "reason": "FILE_TOO_LARGE", "bytes": info.st_size})
                    continue
                if len(payloads) >= MAX_FILES or total + info.st_size > MAX_TOTAL_BYTES:
                    raise ValueError("Package limit exceeded; select a narrower root; no output written")
                # Linux O_NOFOLLOW protects the final path against a symlink swap.
                descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
                with os.fdopen(descriptor, "rb") as stream:
                    before = os.fstat(stream.fileno())
                    if (before.st_ino, before.st_dev, before.st_size, before.st_mtime_ns) != (
                        info.st_ino, info.st_dev, info.st_size, info.st_mtime_ns
                    ):
                        raise ValueError("Research input changed while being collected")
                    data = stream.read(MAX_FILE_BYTES + 1)
                    after = os.fstat(stream.fileno())
                if len(data) != info.st_size or after.st_size != before.st_size or after.st_mtime_ns != before.st_mtime_ns:
                    raise ValueError("Research input changed while being collected")
                try:
                    data.decode("utf-8")
                    if b"\0" in data:
                        raise UnicodeError("NUL bytes")
                except UnicodeError:
                    rejected.append({"path": alias, "reason": "NOT_UTF8_TEXT"})
                    continue
                total += len(data)
                entries.append({"path": alias, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
                payloads.append((alias, data))
    if not payloads:
        raise ValueError("No eligible research text found; no output written")
    manifest = {
        "schemaVersion": 1,
        "purpose": "Stage 3 region-pair source evidence; collection is not proof of semantics",
        "rootCount": len(resolved), "fileCount": len(entries), "uncompressedBytes": total,
        "files": entries, "rejected": rejected,
        "selection": "Exact report names or allowlisted region-function addresses in filenames only",
    }
    descriptor, temporary = tempfile.mkstemp(prefix=".automix-evidence-", suffix=".zip", dir=output.parent)
    os.close(descriptor)
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name, data in payloads:
                archive.writestr(name, data)
            archive.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
        # Create-only publication. Hard linking fails rather than replacing an
        # output created concurrently after the initial existence check.
        os.link(temporary, output)
    finally:
        Path(temporary).unlink(missing_ok=True)
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, action="append", required=True, help="Explicit local research directory; repeatable")
    parser.add_argument("--output", type=Path, required=True, help="New ZIP path with an existing parent directory")
    args = parser.parse_args()
    try:
        manifest = collect(args.root, args.output)
    except (OSError, ValueError) as error:
        print(f"Collection failed: {error}", file=sys.stderr)
        return 1
    print(f"Collected {manifest['fileCount']} text files, {manifest['uncompressedBytes']} bytes; "
          f"{len(manifest['rejected'])} candidates rejected. Review manifest.json before sharing.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
