#!/usr/bin/env python3
"""Independent negative XML fixtures; ELF checks use compiled artifacts in the host tests."""
import copy
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
import automix_verify_owner_ingress as guard

class Reports(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.xml=ET.Element('testsuite',name=guard.CLASS,tests=str(len(guard.REQUIRED)),failures='0',errors='0',skipped='0')
        for name in sorted(guard.REQUIRED):ET.SubElement(self.xml,'testcase',name=name,classname=guard.CLASS)
    def tearDown(self):self.temp.cleanup()
    def write(self,x):ET.ElementTree(x).write(self.root/f'TEST-{guard.CLASS}.xml')
    def bad(self,x):
        self.write(x)
        with self.assertRaises((ValueError,ET.ParseError)):guard.verify_owner_report(self.root)
    def test_valid(self):self.write(self.xml);self.assertEqual(guard.verify_owner_report(self.root),40)
    def test_missing(self):self.xml.remove(self.xml[0]);self.bad(self.xml)
    def test_duplicate(self):self.xml.append(copy.deepcopy(self.xml[0]));self.bad(self.xml)
    def test_unknown(self):self.xml[0].set('name','madeUpSuccess');self.bad(self.xml)
    def test_wrong_class(self):self.xml[0].set('classname','Other');self.bad(self.xml)
    def test_wrong_suite(self):self.xml.set('name','Other');self.bad(self.xml)
    def test_count(self):self.xml.set('tests','39');self.bad(self.xml)
    def test_false_counts(self):
        for field in ('failures','errors','skipped','disabled'):
            with self.subTest(field=field):
                x=copy.deepcopy(self.xml);x.set(field,'1');self.bad(x)
    def test_failed_skipped_cases(self):
        for tag in ('failure','error','skipped','disabled'):
            with self.subTest(tag=tag):
                x=copy.deepcopy(self.xml);ET.SubElement(x[0],tag);self.bad(x)
    def test_missing_report(self):
        with self.assertRaises(FileNotFoundError):guard.verify_owner_report(self.root)
    def test_symlink_report(self):
        target=self.root/'alternate.xml';ET.ElementTree(self.xml).write(target)
        (self.root/f'TEST-{guard.CLASS}.xml').symlink_to(target)
        with self.assertRaises(ValueError):guard.verify_owner_report(self.root)
    def test_invalid_elf(self):
        with self.assertRaises(ValueError):guard.verify_owner_exports(b'not an ELF',2,183)
if __name__=='__main__':unittest.main()
