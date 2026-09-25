#!/usr/bin/env python3
"""Require non-skipped boundary JVM tests. These are NOT real-JNI or on-device tests."""
from __future__ import annotations
import argparse,json,pathlib,xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]

def verify(directory:pathlib.Path,expected:dict):
    if not directory.is_dir() or directory.is_symlink():raise ValueError('Test results directory missing')
    found={key:{} for key in expected};files=sorted(directory.glob('*.xml'))
    if not files or len(files)>4096:raise ValueError('Invalid report set')
    for path in files:
        if path.is_symlink() or path.stat().st_size>8*1024*1024:raise ValueError('Unsafe/oversized report')
        root=ET.parse(path).getroot()
        for case in root.iter('testcase'):
            cls=case.get('classname');name=case.get('name')
            if cls not in expected:continue
            if case.find('skipped') is not None or case.find('failure') is not None or case.find('error') is not None:
                raise ValueError('Failed/skipped required suite: '+str(cls))
            if name in found[cls]:raise ValueError('Duplicate required testcase')
            found[cls][name]=True
    for cls,names in expected.items():
        if not set(names).issubset(found[cls]):raise ValueError('Missing boundary tests: '+cls)
    return sum(len(names) for names in expected.values())

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--reports',type=pathlib.Path,default=ROOT/'app/build/test-results/testDebugUnitTest')
    p.add_argument('--fork-reports',type=pathlib.Path)
    a=p.parse_args()
    expected=json.loads((ROOT/'scripts/automix_render_boundary_required_tests.json').read_text())
    try:
        count=verify(a.reports,expected)
        fork=None
        if a.fork_reports:
            fork=verify(a.fork_reports,{'androidx.media3.exoplayer.mediacodec.LmgOutputBoundaryIdentityTest':[
                'protocol','capturedIdNotMutableInputId','capturedTimeline','originalOffsetUnchanged',
                'originalBoundaryUnchanged','differentOccurrencesHaveDifferentTokens','unsetDoesNotInventAnId',
                'capturedEntryInitiallyValid']})
        print(json.dumps(dict(status='BOUNDARY_JVM_REPORTS_VERIFIED',newAppJvmTests=count,
           forkConstructorTests=fork,realJniTestsAdded=0,devicePlaybackVerified=False,canExecute=False),indent=2))
    except (OSError,ValueError,ET.ParseError) as e:raise SystemExit(str(e))
