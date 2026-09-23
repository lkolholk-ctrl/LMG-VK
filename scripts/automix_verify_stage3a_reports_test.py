#!/usr/bin/env python3
"""Reject absent/skipped/unrelated Stage 3a execution reports, without an APK."""
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from automix_verify_observation_apk import STAGE3A_CLASS, STAGE3A_CASES, verify_stage3a_report

class PreparationReportsTest(unittest.TestCase):
    def suite(self):
        root = ET.Element('testsuite', name=STAGE3A_CLASS, tests=str(len(STAGE3A_CASES)), failures='0', errors='0', skipped='0')
        for name in sorted(STAGE3A_CASES):
            ET.SubElement(root, 'testcase', name=name, classname=STAGE3A_CLASS)
        return root

    def verify(self, root):
        with tempfile.TemporaryDirectory() as d:
            if root is not None:
                ET.ElementTree(root).write(Path(d) / f'TEST-{STAGE3A_CLASS}.xml', encoding='utf-8')
            return verify_stage3a_report(Path(d))

    def test_valid(self):
        self.assertEqual(self.verify(self.suite()), 8)

    def test_required_suite(self):
        invalid = [None]
        root = self.suite(); root.set('name', 'Unrelated'); invalid.append(root)
        root = self.suite(); root[0].set('classname', 'Unrelated'); invalid.append(root)
        root = self.suite(); root[0].set('name', 'Unrelated'); invalid.append(root)
        root = self.suite(); root.remove(root[0]); root.set('tests', '7'); invalid.append(root)
        root = self.suite(); root[0].set('name', root[1].get('name')); invalid.append(root)
        root = self.suite(); root.set('tests', '9'); invalid.append(root)
        for tag in ('skipped', 'failure', 'error'):
            root = self.suite(); ET.SubElement(root[0], tag); invalid.append(root)
        for field in ('failures', 'errors', 'skipped', 'disabled'):
            root = self.suite(); root.set(field, '1'); invalid.append(root)
        for root in invalid:
            with self.subTest(root=ET.tostring(root) if root is not None else None):
                with self.assertRaises(Exception): self.verify(root)
        print(f'Stage 3a report guard: 1 positive, {len(invalid)} rejected reports')

if __name__ == '__main__':
    unittest.main()
