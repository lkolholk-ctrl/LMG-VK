#!/usr/bin/env python3
"""Verify named output/ingress JVM tests. A recording sink is NOT hardware playback proof."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
SPEC=Path(__file__).with_name('automix_output_port_required_tests.json')
FORK_CLASS='androidx.media3.exoplayer.audio.LmgTransitionGainMessageTest'
FORK_NAMES=['protocolKnown','combinedExactAcrossGrid','zeroMaster','signedZero','invalidComponentsFail',
 'extensionReceivesSeparateNotLegacy','legacyGetsSameProduct','originalExceptionPropagates',
 'normalPlayerMessageIsTyped','normalPlayerFallbackPreservesValue']

def verify_report(directory:Path,classname:str,names:list[str])->int:
    if not directory.is_dir() or directory.is_symlink():raise ValueError('Missing/unsafe report directory')
    path=directory/('TEST-'+classname+'.xml')
    if path.is_symlink() or not path.is_file() or path.stat().st_size>4*1024*1024:
        raise ValueError('Missing/unsafe required report: '+classname)
    raw=path.read_bytes()
    if b'<!DOCTYPE' in raw.upper() or b'<!ENTITY' in raw.upper():raise ValueError('DTD/entity is not a test report')
    root=ET.fromstring(raw)
    if root.tag!='testsuite' or root.get('name')!=classname:raise ValueError('Wrong suite')
    cases=root.findall('testcase');seen=[c.get('name') for c in cases]
    if len(names)!=len(set(names)) or len(seen)!=len(names) or set(seen)!=set(names):raise ValueError('Required test list differs')
    if any(c.get('classname')!=classname for c in cases):raise ValueError('Wrong testcase owner')
    if any(list(root.iter(tag)) for tag in ('failure','error','skipped','disabled')):raise ValueError('Failed/skipped test')
    if int(root.get('tests','-1'))!=len(names):raise ValueError('Inconsistent suite count')
    if any(int(root.get(k,'0')) for k in ('failures','errors','skipped','disabled')):raise ValueError('Unsuccessful suite')
    return len(names)

def verify(directory:Path,fork_directory:Path|None=None)->dict:
    spec=json.loads(SPEC.read_text());expected=spec['classes']
    counts={cls:verify_report(directory,cls,names) for cls,names in expected.items()}
    fork=verify_report(fork_directory,FORK_CLASS,FORK_NAMES) if fork_directory is not None else None
    return dict(status='SAME_SINK_OUTPUT_REPORTS_VERIFIED',newAppJvm=sum(counts.values()),
      includedIngressJni=counts[spec['realJniClass']],forkGainTests=fork,
      physicalAudioOutputVerified=False,completePlaybackLeaseInstalled=False,liveDspInstalled=False,canExecute=False)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reports',type=Path,default=ROOT/'app/build/test-results/testDebugUnitTest')
    parser.add_argument('--fork-reports',type=Path)
    args=parser.parse_args()
    try:print(json.dumps(verify(args.reports,args.fork_reports),indent=2))
    except (OSError,ValueError,ET.ParseError) as error:raise SystemExit(str(error))
