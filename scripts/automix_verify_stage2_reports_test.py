#!/usr/bin/env python3
"""Dependency-free regression tests for required real-JNI report verification."""
import tempfile
import unittest
from pathlib import Path
from automix_verify_observation_apk import verify_test_report

class RequiredReportsTest(unittest.TestCase):
    def run_xml(self, xml, minimum=2):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'test.xml'
            if xml is not None:
                report.write_text(xml, encoding='utf-8')
            return verify_test_report(report, minimum)

    def test_valid_report(self):
        self.assertEqual(self.run_xml('<testsuite tests="2"><testcase name="a"/><testcase name="b"/></testsuite>'), 2)

    def test_reject_invalid_reports(self):
        cases = [None, '<broken', '<testsuites/>', '<testsuite/>',
                 '<testsuite><testcase name="a"/></testsuite>',
                 '<testsuite><testcase/><testcase name="b"/></testsuite>',
                 '<testsuite><testcase name="a"/><testcase name="a"/></testsuite>',
                 '<testsuite tests="3"><testcase name="a"/><testcase name="b"/></testsuite>']
        for tag in ('skipped', 'failure', 'error'):
            cases.append(f'<testsuite><testcase name="a"><{tag}/></testcase><testcase name="b"/></testsuite>')
        for field in ('failures', 'errors', 'skipped', 'disabled'):
            cases.append(f'<testsuite {field}="1"><testcase name="a"/><testcase name="b"/></testsuite>')
        cases.append('<testsuite errors="oops"><testcase name="a"/><testcase name="b"/></testsuite>')
        for xml in cases:
            with self.subTest(xml=xml):
                with self.assertRaises(Exception):
                    self.run_xml(xml)
        print(f'Required XML reports: 1 positive and {len(cases)} rejection cases passed')

if __name__ == '__main__':
    unittest.main()
