#!/usr/bin/env python3
"""Report and ELF fixtures. Compiled export-only functions are never called."""
from pathlib import Path
import copy
import os
import struct
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
from automix_verify_observation_apk import (SOURCE_JNI_CLASS, SOURCE_JNI_CASES,
    SOURCE_LIFECYCLE_CLASS, SOURCE_LIFECYCLE_CASES, verify_source_context_reports)
from automix_verify_jni_export import SYMBOL, SOURCE_SYMBOL, verify_jni_exports

class SourceReports(unittest.TestCase):
    def test_reports(self):
        valid = []
        for classname, names in ((SOURCE_JNI_CLASS, SOURCE_JNI_CASES), (SOURCE_LIFECYCLE_CLASS, SOURCE_LIFECYCLE_CASES)):
            root = ET.Element('testsuite', name=classname, tests=str(len(names)), failures='0', errors='0', skipped='0')
            for name in sorted(names): ET.SubElement(root, 'testcase', name=name, classname=classname)
            valid.append(root)
        def verify(roots, omit=None):
            with tempfile.TemporaryDirectory() as d:
                for i, root in enumerate(roots):
                    if i != omit: ET.ElementTree(root).write(Path(d) / f"TEST-{(SOURCE_JNI_CLASS,SOURCE_LIFECYCLE_CLASS)[i]}.xml")
                return verify_source_context_reports(Path(d))
        self.assertEqual(verify(valid), (12, 8))
        rejected = 0
        for side in (0, 1):
            for mutation in range(13):
                roots = copy.deepcopy(valid); r = roots[side]
                if mutation == 0:
                    with self.assertRaises((ValueError, OSError)): verify(roots, side)
                    rejected += 1; continue
                if mutation == 1: r.remove(r[0]); r.set('tests', str(len(r)))
                if mutation == 2: r[0].set('name', 'notRequired')
                if mutation == 3: r[0].set('classname', 'Other')
                if mutation == 4: r.set('name', 'Other')
                if mutation == 5: r[0].set('name', r[1].get('name'))
                if mutation == 6: ET.SubElement(r[0], 'skipped')
                if mutation == 7: ET.SubElement(r[0], 'failure')
                if mutation == 8: ET.SubElement(r[0], 'error')
                if mutation == 9: r.set('tests', '999')
                if mutation == 10: r.set('disabled', '1')
                if mutation == 11: r[0].set('name', '')
                if mutation == 12: r.tag = 'testsuites'
                with self.assertRaises(ValueError): verify(roots)
                rejected += 1
        print(f'Source-context report guard: 1 accepted set, {rejected} rejected variants')

    def test_requires_both_actual_exports(self):
        accepted = rejected = 0
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            flags = [[]]
            while flags:
                extra = flags.pop(0)
                def compile_symbols(symbols):
                    src = root/'exports.c'; so = root/'exports.so'
                    src.write_text(''.join('void '+symbol.decode()+'(void) {}\n' for symbol in symbols))
                    subprocess.run([os.environ.get('CC','cc'), *extra, '-shared','-fPIC', str(src), '-o', str(so)], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                    return so.read_bytes()
                data = compile_symbols((SYMBOL, SOURCE_SYMBOL))
                cls, machine = data[4], struct.unpack_from('<H', data, 18)[0]
                verify_jni_exports(data, cls, machine); accepted += 1
                if not extra and cls == 2 and machine == 62: flags.append(['-m32','-nostdlib'])
                for symbols in ((SYMBOL,), (SOURCE_SYMBOL,), (b'unrelated',)):
                    with self.assertRaises(ValueError): verify_jni_exports(compile_symbols(symbols),cls,machine)
                    rejected += 1
                for symbol in (SYMBOL, SOURCE_SYMBOL):
                    with self.assertRaises(ValueError): verify_jni_exports(data.replace(symbol,b'X'+symbol[1:]),cls,machine)
                    rejected += 1
                for length in (0,48,len(data)-1):
                    with self.assertRaises(ValueError): verify_jni_exports(data[:length],cls,machine)
                    rejected += 1
        print(f'Source-context ELF guard: {accepted} compiled dual-export fixtures accepted, {rejected} stale/corrupt variants rejected')

if __name__ == '__main__': unittest.main()
