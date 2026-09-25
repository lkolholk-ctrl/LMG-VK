#!/usr/bin/env python3
"""Require the actual named gate/AudioSink JVM cases. XML accounting, not device verification."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent

def verify(reports: Path, required: Path = HERE / 'automix_cue_gate_required_tests.json') -> int:
    spec = json.loads(required.read_text())
    if spec.get('schemaVersion') != 1:
        raise ValueError('Unsupported required-test manifest')
    classes = spec['classes']
    expected = {(cls, name) for cls, names in classes.items() for name in names}
    if len(expected) != 78 or sum(map(len, classes.values())) != 78:
        raise ValueError('Expected exactly 78 uniquely named gate tests')
    if reports.is_symlink() or not reports.is_dir():
        raise ValueError('Test reports directory missing or symlink')
    seen = set()
    relevant_bytes = 0
    for path in sorted(reports.glob('TEST-*.xml')):
        # Other suites are verified by existing independent report guards.
        if not any(cls in path.name for cls in classes):
            continue
        if path.is_symlink() or not path.is_file() or path.stat().st_size > 4 * 1024 * 1024:
            raise ValueError('Invalid report file')
        raw = path.read_bytes()
        relevant_bytes += len(raw)
        if relevant_bytes > 8 * 1024 * 1024 or b'<!DOCTYPE' in raw or b'<!ENTITY' in raw:
            raise ValueError('Unsupported XML report')
        root = ET.fromstring(raw)
        for case in root.iter('testcase'):
            cls = case.attrib.get('classname', root.attrib.get('name', ''))
            if cls not in classes:
                continue
            key = (cls, case.attrib.get('name', ''))
            if key not in expected or key in seen:
                raise ValueError('Unexpected or duplicate gate test: ' + str(key))
            if any(case.find(tag) is not None for tag in ('failure','error','skipped')):
                raise ValueError('Gate test failed or skipped: ' + str(key))
            if case.attrib.get('status') in ('notrun','skipped','disabled'):
                raise ValueError('Gate test was not run')
            seen.add(key)
        for tag in ('failure','error'):
            if root.find(tag) is not None:
                raise ValueError('Suite-level failure')
    missing = expected - seen
    if missing:
        raise ValueError(f'{len(missing)} required tests missing: {sorted(missing)[:3]}')
    return len(seen)

def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--reports', type=Path, default=HERE.parent / 'app/build/test-results/testDebugUnitTest')
    args = p.parse_args()
    try:
        n = verify(args.reports)
    except (OSError,ValueError,KeyError,TypeError,ET.ParseError) as err:
        print('Cue gate report verification FAILED: ' + str(err))
        return 1
    print(f'CUE_GATE_JVM_REPORTS_VERIFIED ({n} tests: 70 gate + 8 AudioSink; canExecute=false)')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
