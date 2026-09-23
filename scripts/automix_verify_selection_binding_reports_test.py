#!/usr/bin/env python3
"""Requires positive, named real-JNI and epoch binding executions; no APK needed."""
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from automix_verify_observation_apk import (
 BINDING_JNI_CLASS, BINDING_LIFECYCLE_CLASS, BINDING_JNI_CASES,
 BINDING_LIFECYCLE_CASES, verify_selection_binding_reports,
)
class ReportsTest(unittest.TestCase):
 def suites(self):
  result=[]
  for name,cases in ((BINDING_JNI_CLASS,BINDING_JNI_CASES),(BINDING_LIFECYCLE_CLASS,BINDING_LIFECYCLE_CASES)):
   root=ET.Element('testsuite',name=name,tests=str(len(cases)),failures='0',errors='0',skipped='0')
   for case in sorted(cases):ET.SubElement(root,'testcase',name=case,classname=name)
   result.append(root)
  return result
 def verify(self,roots,omit=None):
  with tempfile.TemporaryDirectory() as d:
   for i,(name,r) in enumerate(zip((BINDING_JNI_CLASS,BINDING_LIFECYCLE_CLASS),roots)):
    if i!=omit:ET.ElementTree(r).write(Path(d)/f'TEST-{name}.xml',encoding='utf-8')
   return verify_selection_binding_reports(Path(d))
 def test_positive(self):self.assertEqual(self.verify(self.suites()),(12,16))
 def test_corrupt(self):
  n=0
  for side in (0,1):
   with self.assertRaises(Exception):self.verify(self.suites(),omit=side)
   n+=1
   def change_root(fn):
    roots=self.suites();fn(roots[side]);return roots
   changes=[lambda s:s.set('name','wrong'),lambda s:s[0].set('name','wrong'),
     lambda s:s[0].set('classname','wrong'),lambda s:s[0].set('name',s[1].get('name')),
     lambda s:s.remove(s[0]),lambda s:s.set('tests','999')]
   for tag in ('skipped','failure','error'):changes.append(lambda s,tag=tag:ET.SubElement(s[0],tag))
   for field in ('skipped','disabled','failures','errors'):changes.append(lambda s,field=field:s.set(field,'1'))
   for change in changes:
    with self.assertRaises(Exception):self.verify(change_root(change))
    n+=1
  print(f'Selection binding report gate: 1 valid pair, {n} rejected variants')
if __name__=='__main__':unittest.main()
