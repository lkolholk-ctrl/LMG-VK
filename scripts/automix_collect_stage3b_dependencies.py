#!/usr/bin/env python3
"""Collect explicit AutoMix functions AND bounded transitive text dependencies.

No network, no code execution, no source edits, no binary/shared-cache collection.
A complete file manifest is not proof of semantic closure. Review it before sharing.
The existing Stage 3 collector and its user-increased scan limit are not modified.
"""
from __future__ import annotations
import argparse
from collections import deque
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import tempfile
import zipfile

DEFAULT_REQUEST = Path(__file__).resolve().parents[1] / 'research/ios26-automix/stage3b/required-functions.json'
SUFFIXES = {'.asm', '.s', '.c', '.cc', '.cpp', '.txt'}
REPORTS = {'AUTOMIX_CANDIDATE_GENERATION.md', 'AUTOMIX_SCORING.md', 'P1_AUTOMIX_CLOSURE.md',
           'P2_AUTOMIX_CLOSURE.md', 'TRANSITION_PLANNER_CLEANROOM_SPEC.md', 'RETRACTED_CLAIMS.md'}
EXCLUDE = {'.git', '.gradle', '.idea', 'build', 'node_modules', '__pycache__', '.cache'}
MAX_SCAN = 2_000_000
MAX_FILE = 2 * 1024 * 1024
MAX_TOTAL = 32 * 1024 * 1024
MAX_FILES = 2048
CODE_LOW, CODE_HIGH = 0x27220E000, 0x272248000
TOKEN = re.compile(r'(?<![0-9a-f])(?:0x)?([0-9a-f]{9})(?![0-9a-f])', re.I)


def addresses(name: str) -> set[str]:
    return {a.lower() for a in TOKEN.findall(name)} if Path(name).suffix.lower() in SUFFIXES else set()


def stable_read(path: Path) -> bytes:
    before = path.lstat()
    if not stat.S_ISREG(before.st_mode):
        raise ValueError('Selected source is not a regular file')
    if before.st_size > MAX_FILE:
        raise ValueError('Selected source exceeds per-file limit')
    descriptor = os.open(path, os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0))
    with os.fdopen(descriptor, 'rb') as stream:
        opened = os.fstat(stream.fileno())
        fields = lambda s: (s.st_dev, s.st_ino, s.st_size, s.st_mtime_ns)
        if fields(before) != fields(opened):
            raise ValueError('Source changed before read')
        data = stream.read(MAX_FILE + 1)
        after = os.fstat(stream.fileno())
    if fields(opened) != fields(after) or len(data) != before.st_size:
        raise ValueError('Source changed during read')
    data.decode('utf-8')
    if b'\0' in data:
        raise ValueError('Selected source contains NUL bytes')
    return data


def callees(data: bytes, suffix: str) -> set[str]:
    text = data.decode('utf-8')
    found = set()
    if suffix.lower() in {'.asm', '.s'}:
        instructions = [(int(a, 16), instruction) for a, instruction in
                        re.findall(r'^([0-9a-f]{9}):\s*(.+)$', text, re.M | re.I)]
        if not instructions:
            return found
        low, high = min(a for a, _ in instructions), max(a for a, _ in instructions)
        for _, instruction in instructions:
            match = re.fullmatch(r'(bl|b)\s+0x([0-9a-f]{9})', instruction, re.I)
            if match:
                target = int(match[2], 16)
                if CODE_LOW <= target < CODE_HIGH and not low <= target <= high:
                    found.add(match[2].lower())
    else:
        # Includes named callbacks passed as arguments, not just direct calls.
        found.update(a.lower() for a in re.findall(r'\bFUN_([0-9a-f]{9})\b', text, re.I)
                     if CODE_LOW <= int(a, 16) < CODE_HIGH)
    return found


def collect(roots: list[Path], output: Path, request: dict, depth: int = 4) -> dict:
    if not roots or not 0 <= depth <= 8:
        raise ValueError('Explicit roots and a depth in 0..8 are required')
    if output.exists() or output.is_symlink() or not output.parent.is_dir():
        raise ValueError('Output must be new, with an existing parent directory')
    if request.get('schemaVersion') != 1:
        raise ValueError('Unsupported dependency request')
    required = {item['address'].lower() for item in request['required']}
    original = {a.lower() for a in request.get('originalEntryPoints', [])}
    initial = required | original
    if not required or any(re.fullmatch(r'[0-9a-f]{9}', a) is None for a in initial):
        raise ValueError('Invalid function addresses')
    resolved = []
    index: dict[str, list[tuple[str, Path]]] = {}
    reports = []
    scanned = 0
    def walk_error(error: OSError) -> None:
        raise OSError('Research tree enumeration failed') from error
    for number, root in enumerate(roots):
        if root.is_symlink() or not root.is_dir():
            raise ValueError('Research roots must be nonsymlink directories')
        real = root.resolve()
        if any(real == prior or real in prior.parents or prior in real.parents for prior in resolved):
            raise ValueError('Research roots may not overlap')
        resolved.append(real)
        for directory, dirs, names in os.walk(real, followlinks=False, onerror=walk_error):
            dirs[:] = sorted(d for d in dirs if d not in EXCLUDE and not (Path(directory) / d).is_symlink())
            for name in sorted(names):
                scanned += 1
                if scanned > MAX_SCAN:
                    raise ValueError('Scan limit reached; choose narrower roots')
                path = Path(directory) / name
                if path.is_symlink():
                    continue
                alias = f'root{number}/{path.relative_to(real).as_posix()}'
                if name in REPORTS:
                    reports.append((alias, path))
                for address in addresses(name):
                    index.setdefault(address, []).append((alias, path))
    payloads: dict[str, bytes] = {}
    records = {}
    total = 0
    def include(alias: str, path: Path) -> bytes:
        nonlocal total
        if alias not in payloads:
            data = stable_read(path)
            if len(payloads) >= MAX_FILES or total + len(data) > MAX_TOTAL:
                raise ValueError('Package limit reached; no output written')
            total += len(data)
            payloads[alias] = data
        return payloads[alias]
    queued = {a: 0 for a in initial}
    queue = deque(sorted(initial))
    beyond_depth = set()
    while queue:
        address = queue.popleft()
        level = queued[address]
        files = index.get(address, [])
        references = set()
        disassembly_found = False
        for alias, path in files:
            data = include(alias, path)
            if path.suffix.lower() in {'.asm', '.s'} and re.search(
                    rf'^{address}:\s+', data.decode(), re.M | re.I):
                disassembly_found = True
            references.update(callees(data, path.suffix))
        references.discard(address)
        records[address] = {'depth': level, 'files': [alias for alias, _ in files],
                            'hasDisassemblyEntry': disassembly_found,
                            'referencedFunctions': sorted(references)}
        for target in sorted(references):
            if target in queued:
                continue
            if level >= depth:
                beyond_depth.add(target)
            else:
                queued[target] = level + 1
                queue.append(target)
    for alias, path in reports:
        include(alias, path)
    if not payloads:
        raise ValueError('No requested source text found; no output written')
    missing = sorted(a for a in required if not records[a]['hasDisassemblyEntry'])
    manifest = {
        'schemaVersion': 1, 'purpose': 'Stage 3b bounded dependency corpus; NOT proof of semantic closure',
        'requestBaseCommit': request.get('baseCommit'), 'rootCount': len(resolved), 'scanCount': scanned,
        'depthLimit': depth, 'fileCount': len(payloads), 'uncompressedBytes': total,
        'status': 'INCOMPLETE_REQUIRED_DISASSEMBLY' if missing else 'REQUIRED_DISASSEMBLY_FOUND',
        'missingRequiredDisassembly': missing,
        'missingTransitiveDisassembly': sorted(a for a in records if a not in required and not records[a]['hasDisassemblyEntry']),
        'unvisitedAtDepthLimit': sorted(beyond_depth - records.keys()),
        'functions': records,
        'files': [{'path': name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
                  for name, data in sorted(payloads.items())],
    }
    descriptor, temporary = tempfile.mkstemp(prefix='.automix-dependencies-', suffix='.zip', dir=output.parent)
    os.close(descriptor)
    try:
        with zipfile.ZipFile(temporary, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
            for name, data in sorted(payloads.items()):
                archive.writestr(name, data)
            archive.writestr('manifest.json', json.dumps(manifest, indent=2) + '\n')
        os.link(temporary, output)  # Create-only publication, including concurrent writers.
    finally:
        Path(temporary).unlink(missing_ok=True)
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, action='append', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--request', type=Path, default=DEFAULT_REQUEST)
    parser.add_argument('--depth', type=int, default=4)
    args = parser.parse_args()
    try:
        request = json.loads(stable_read(args.request))
        manifest = collect(args.root, args.output, request, args.depth)
        print(f"{manifest['status']}: {manifest['fileCount']} text files, "
              f"{manifest['uncompressedBytes']} bytes; "
              f"{len(manifest['missingRequiredDisassembly'])} required disassemblies missing.")
        print('Review manifest.json before sharing. File collection does not establish source fidelity.')
        return 0
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f'Dependency collection failed: {error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
