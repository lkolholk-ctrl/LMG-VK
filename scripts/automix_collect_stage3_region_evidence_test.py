#!/usr/bin/env python3
"""Read-only collector boundaries, using temporary synthetic inputs only."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile

import automix_collect_stage3_region_evidence as collector


class CollectorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = self.base / "research"
        self.root.mkdir()
        self.out = self.base / "evidence.zip"
        self.text = self.root / "2722307ec__region.asm"
        self.text.write_text("synthetic input, not Apple evidence\n")

    def test_allowlist_and_unrelated_content(self):
        (self.root / "tokens.txt").write_text("not for collection")
        (self.root / "song.json").write_text("not for collection")
        report = self.root / "AUTOMIX_CANDIDATE_GENERATION.md"
        report.write_text("synthetic report")
        manifest = collector.collect([self.root], self.out)
        self.assertEqual(manifest["fileCount"], 2)
        with ZipFile(self.out) as z:
            self.assertEqual(set(z.namelist()), {"root0/" + self.text.name, "root0/" + report.name, "manifest.json"})
            self.assertEqual(json.loads(z.read("manifest.json")), manifest)
        self.assertTrue(self.text.is_file())

    def test_no_overwrite(self):
        self.out.write_bytes(b"original")
        with self.assertRaises(FileExistsError):
            collector.collect([self.root], self.out)
        self.assertEqual(self.out.read_bytes(), b"original")

    def test_symlinks_and_excluded_directories(self):
        outside = self.base / "outside"
        outside.mkdir()
        (outside / "2722315f8.asm").write_text("excluded")
        (self.root / "linked").symlink_to(outside, target_is_directory=True)
        (self.root / "272231b68.asm").symlink_to(outside / "2722315f8.asm")
        (self.root / ".git").mkdir()
        (self.root / ".git" / "2722290a0.asm").write_text("excluded")
        m = collector.collect([self.root], self.out)
        self.assertEqual(m["fileCount"], 1)
        self.assertEqual(m["rejected"][0]["reason"], "NOT_REGULAR_FILE")

    def test_oversize_and_binary_reported(self):
        (self.root / "2722290a0.txt").write_bytes(b"x" * 100)
        (self.root / "27222919c.asm").write_bytes(b"binary\0")
        with patch.object(collector, "MAX_FILE_BYTES", 64):
            m = collector.collect([self.root], self.out)
        self.assertEqual({x["reason"] for x in m["rejected"]}, {"FILE_TOO_LARGE", "NOT_UTF8_TEXT"})
        self.assertEqual(m["fileCount"], 1)

    def test_empty_failure(self):
        self.text.unlink()
        with self.assertRaises(ValueError):
            collector.collect([self.root], self.out)
        self.assertFalse(self.out.exists())

    def test_total_and_scan_caps_fail_without_output(self):
        for setting in ("MAX_TOTAL_BYTES", "MAX_SCANNED", "MAX_FILES"):
            with patch.object(collector, setting, 0):
                with self.assertRaises(ValueError):
                    collector.collect([self.root], self.out)
            self.assertFalse(self.out.exists())

    def test_exact_address_not_long_hex_substring(self):
        self.assertTrue(collector.is_target("FUN_0x2722307ec.c"))
        self.assertTrue(collector.is_target("2722307ec__a.asm"))
        self.assertFalse(collector.is_target("a2722307ecf.asm"))
        self.assertFalse(collector.is_target("2722307ec.bin"))

    def test_root_validation(self):
        with self.assertRaises(ValueError):
            collector.collect([self.root, self.root], self.out)
        symlink = self.base / "symlink"
        symlink.symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(ValueError):
            collector.collect([symlink], self.out)


if __name__ == "__main__":
    unittest.main()
