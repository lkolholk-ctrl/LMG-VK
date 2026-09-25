#!/usr/bin/env python3
"""Required native owner admission tests + defined dynamic JNI exports, NOT hardware playback proof."""
from pathlib import Path
import json
import sys
import xml.etree.ElementTree as ET
from zipfile import ZipFile
from automix_verify_jni_export import ABI_IDENTITIES, OWNER_SYMBOLS, verify_jni_export
SPEC = json.loads(Path(__file__).with_name('automix_owner_required_tests.json').read_text())
CLASS = SPEC['class']
REQUIRED = frozenset(SPEC['tests'])
if len(REQUIRED) != 40:
    raise ValueError('Unexpected owner test specification')

def verify_owner_report(reports: Path) -> int:
    report = reports / ('TEST-' + CLASS + '.xml')
    if report.is_symlink() or report.stat().st_size > 2*1024*1024:
        raise ValueError('Unsafe owner report')
    root = ET.fromstring(report.read_bytes())
    tests = root.findall('testcase')
    names = [t.get('name') for t in tests]
    if root.tag != 'testsuite' or root.get('name') != CLASS:
        raise ValueError('Wrong owner testsuite')
    if set(names) != REQUIRED or len(names) != len(REQUIRED):
        raise ValueError('Missing/duplicate/unknown owner tests')
    if any(t.get('classname') != CLASS for t in tests):
        raise ValueError('Wrong owner testcase class')
    if any(root.findall('.//' + tag) for tag in ('failure','error','skipped','disabled')):
        raise ValueError('Failed or skipped owner test')
    if int(root.get('tests','-1')) != len(names):
        raise ValueError('Inconsistent owner test count')
    for field in ('failures','errors','skipped','disabled'):
        if int(root.get(field,'0')) != 0:
            raise ValueError('Unsuccessful owner suite')
    return len(names)

def verify_owner_exports(data: bytes, elf_class: int, machine: int) -> None:
    for symbol in OWNER_SYMBOLS:
        verify_jni_export(data,elf_class,machine,symbol)

def verify(root: Path) -> None:
    count=verify_owner_report(root/'app/build/test-results/testDebugUnitTest')
    apks=sorted((root/'app/build/outputs/apk/debug').glob('*.apk'))
    if not apks:
        raise ValueError('Fresh debug APK required')
    for apk in apks:
        with ZipFile(apk) as z:
            if len(z.namelist()) != len(set(z.namelist())):
                raise ValueError('Duplicate APK entries')
            for abi, identity in ABI_IDENTITIES.items():
                name=f'lib/{abi}/liblmg_automix_jni.so'
                if z.getinfo(name).file_size > 128*1024*1024:
                    raise ValueError('Library limit')
                verify_owner_exports(z.read(name),*identity)
    print(f'OWNER_INGRESS_JNI_VERIFIED: {count} real-JNI cases, 3 ABIs, 9 ingress exports; liveDspInstalled=false')

if __name__=='__main__':
    try: verify(Path(__file__).resolve().parents[1])
    except Exception as e:
        print('Owner admission verification failed: '+str(e),file=sys.stderr)
        raise SystemExit(1)
