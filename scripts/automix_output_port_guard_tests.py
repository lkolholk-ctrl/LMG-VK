#!/usr/bin/env python3
"""Negative tests for named report checking. Synthetic XML, no claimed JUnit execution."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
import automix_verify_output_port_reports as guard

class GuardTests(unittest.TestCase):
    def fixture(self):
        tmp=tempfile.TemporaryDirectory();self.addCleanup(tmp.cleanup);p=Path(tmp.name)
        spec=json.loads(guard.SPEC.read_text());self.cls=next(iter(spec['classes']));self.path=p/('TEST-'+self.cls+'.xml')
        for cls,names in {**spec['classes'],guard.FORK_CLASS:guard.FORK_NAMES}.items():
            root=ET.Element('testsuite',name=cls,tests=str(len(names)),failures='0',errors='0',skipped='0')
            for name in names:ET.SubElement(root,'testcase',classname=cls,name=name)
            (p/('TEST-'+cls+'.xml')).write_bytes(ET.tostring(root))
        return p
    def bad(self,mutate):
        p=self.fixture();root=ET.fromstring(self.path.read_bytes());mutate(root);self.path.write_bytes(ET.tostring(root))
        with self.assertRaises(ValueError):guard.verify(p,p)
    def test_positive(self):
        p=self.fixture();r=guard.verify(p,p);self.assertEqual(r['newAppJvm'],90);self.assertEqual(r['includedIngressJni'],18);self.assertEqual(r['forkGainTests'],10);self.assertFalse(r['canExecute'])
    def test_missing(self):
        p=self.fixture();self.path.unlink()
        with self.assertRaises(ValueError):guard.verify(p,p)
    def test_skip(self):self.bad(lambda r:ET.SubElement(r.find('testcase'),'skipped'))
    def test_failure(self):self.bad(lambda r:ET.SubElement(r.find('testcase'),'failure'))
    def test_error(self):self.bad(lambda r:ET.SubElement(r.find('testcase'),'error'))
    def test_duplicate(self):self.bad(lambda r:r.append(copy.deepcopy(r.find('testcase'))))
    def test_wrong_class(self):self.bad(lambda r:r.find('testcase').set('classname','other'))
    def test_wrong_name(self):self.bad(lambda r:r.find('testcase').set('name','other'))
    def test_wrong_suite(self):self.bad(lambda r:r.set('name','other'))
    def test_inconsistent_count(self):self.bad(lambda r:r.set('tests','0'))
    def test_summary_failure(self):self.bad(lambda r:r.set('failures','1'))
    def test_nested_failure(self):self.bad(lambda r:ET.SubElement(ET.SubElement(r,'extra'),'error'))
    def test_symlink(self):
        p=self.fixture();q=p/'other.xml';self.path.rename(q);self.path.symlink_to(q)
        with self.assertRaises(ValueError):guard.verify(p,p)
    def test_dtd(self):
        p=self.fixture();self.path.write_bytes(b'<!DOCTYPE testsuite []>'+self.path.read_bytes())
        with self.assertRaises(ValueError):guard.verify(p,p)
    def test_without_fork_does_not_claim_fork(self):self.assertIsNone(guard.verify(self.fixture())['forkGainTests'])
if __name__=='__main__':unittest.main(verbosity=2)
