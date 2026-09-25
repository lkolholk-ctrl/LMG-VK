#!/usr/bin/env python3
"""Synthetic report-guard tests. These do not claim the Android JUnit tests ran."""
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from automix_verify_cue_gate_reports import verify, HERE

class Reports(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.spec=json.loads((HERE/'automix_cue_gate_required_tests.json').read_text())['classes']
        for cls,names in self.spec.items():
            suite=ET.Element('testsuite',name=cls,tests=str(len(names)))
            for name in names: ET.SubElement(suite,'testcase',classname=cls,name=name,time='0.001')
            ET.ElementTree(suite).write(self.root/f'TEST-{cls}.xml')
        self.path=sorted(self.root.glob('TEST-*'))[0]
    def edit(self,mutate):
        tree=ET.parse(self.path);mutate(tree.getroot());tree.write(self.path)
    def rejected(self):
        with self.assertRaises((ValueError,ET.ParseError)): verify(self.root)
    def test_success(self): self.assertEqual(verify(self.root),78)
    def test_missing_file(self): self.path.unlink();self.rejected()
    def test_missing_case(self): self.edit(lambda r:r.remove(r.find('testcase')));self.rejected()
    def test_failed_case(self): self.edit(lambda r:ET.SubElement(r.find('testcase'),'failure'));self.rejected()
    def test_error_case(self): self.edit(lambda r:ET.SubElement(r.find('testcase'),'error'));self.rejected()
    def test_skipped_case(self): self.edit(lambda r:ET.SubElement(r.find('testcase'),'skipped'));self.rejected()
    def test_duplicate_case(self):
        import copy
        self.edit(lambda r:r.append(copy.deepcopy(r.find('testcase'))));self.rejected()
    def test_wrong_name(self): self.edit(lambda r:r.find('testcase').set('name','invented'));self.rejected()
    def test_not_run(self): self.edit(lambda r:r.find('testcase').set('status','notrun'));self.rejected()
    def test_entity(self): self.path.write_bytes(b'<!DOCTYPE testsuite [<!ENTITY x "x">]>'+self.path.read_bytes());self.rejected()
    def test_suite_error(self): self.edit(lambda r:ET.SubElement(r,'error'));self.rejected()
    def test_symlink(self):
        copy=self.root/'real.xml';self.path.rename(copy);self.path.symlink_to(copy);self.rejected()

if __name__=='__main__': unittest.main(verbosity=2)
