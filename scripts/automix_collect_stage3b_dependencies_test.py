#!/usr/bin/env python3
"""Local tests for the bounded dependency collector; no network or real source edits."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import automix_collect_stage3b_dependencies as collector

A, B, C = '27222d51c', '27223550c', '2722378a0'


class CollectorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = self.base / 'research'
        self.root.mkdir()
        self.out = self.base / 'evidence.zip'
        self.request = {'schemaVersion': 1, 'required': [{'address': A}], 'originalEntryPoints': []}

    def source(self, address, text=None, suffix='.asm', directory=''):
        path = self.root / directory / f'{address}__FUN_{address}{suffix}'
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text or f'{address}: retab\n')
        return path

    def run_collect(self, **kwargs):
        return collector.collect([self.root], self.out, self.request, **kwargs)

    def test_direct_and_callback_dependency_closure(self):
        self.source(A, f'{A}: bl 0x{B}\n27222d520: retab\n')
        self.source(B)
        self.source(B, f'void FUN_{B}() {{ call(FUN_{C}); }}\n', '.c')
        self.source(C)
        result = self.run_collect()
        self.assertEqual(result['status'], 'REQUIRED_DISASSEMBLY_FOUND')
        self.assertEqual(set(result['functions']), {A, B, C})
        self.assertEqual(result['functions'][C]['depth'], 2)
        with zipfile.ZipFile(self.out) as archive:
            manifest = json.loads(archive.read('manifest.json'))
            for item in manifest['files']:
                data = archive.read(item['path'])
                self.assertEqual(hashlib.sha256(data).hexdigest(), item['sha256'])

    def test_depth_limit_is_explicit(self):
        self.source(A, f'{A}: bl 0x{B}\n')
        self.source(B)
        result = self.run_collect(depth=0)
        self.assertEqual(result['unvisitedAtDepthLimit'], [B])
        self.assertNotIn(B, result['functions'])

    def test_c_only_is_not_reported_as_disassembly(self):
        self.source(A, f'void FUN_{A}() {{}}\n', '.c')
        result = self.run_collect()
        self.assertEqual(result['missingRequiredDisassembly'], [A])
        self.assertEqual(result['status'], 'INCOMPLETE_REQUIRED_DISASSEMBLY')

    def test_duplicate_source_versions_are_preserved(self):
        self.source(A, directory='version1')
        self.source(A, f'{A}: nop\n', directory='version2')
        result = self.run_collect()
        self.assertEqual(len(result['functions'][A]['files']), 2)
        self.assertEqual(result['fileCount'], 2)

    def test_internal_branches_and_external_runtime_are_not_dependencies(self):
        self.source(A, f'{A}: b 0x27222d524\n27222d520: bl 0x2743dd380\n27222d524: retab\n')
        result = self.run_collect()
        self.assertEqual(result['functions'][A]['referencedFunctions'], [])

    def test_no_existing_output_is_overwritten(self):
        self.source(A)
        self.out.write_bytes(b'keep')
        with self.assertRaises(ValueError): self.run_collect()
        self.assertEqual(self.out.read_bytes(), b'keep')

    def test_links_and_unrelated_text_are_excluded(self):
        self.source(A, f'{A}: bl 0x{B}\n')
        secret = self.base / 'credentials.txt'
        secret.write_text('private-example')
        (self.root / f'{B}.asm').symlink_to(secret)
        (self.root / 'unrelated.txt').write_text('not included')
        result = self.run_collect()
        self.assertEqual(result['fileCount'], 1)
        self.assertEqual(result['missingTransitiveDisassembly'], [B])

    def test_source_tree_is_not_changed(self):
        path = self.source(A)
        before = path.read_bytes()
        self.run_collect()
        self.assertEqual(path.read_bytes(), before)
        self.assertEqual(list(self.root.iterdir()), [path])

    def test_binary_is_rejected_before_output_publication(self):
        path = self.source(A)
        path.write_bytes(b'\x00binary')
        with self.assertRaises(ValueError): self.run_collect()
        self.assertFalse(self.out.exists())

    def test_total_package_limit_is_enforced(self):
        self.source(A)
        with patch.object(collector, 'MAX_TOTAL', 1):
            with self.assertRaises(ValueError): self.run_collect()
        self.assertFalse(self.out.exists())

    def test_per_file_limit_is_enforced(self):
        self.source(A)
        with patch.object(collector, 'MAX_FILE', 1):
            with self.assertRaises(ValueError): self.run_collect()
        self.assertFalse(self.out.exists())

    def test_scan_limit_is_enforced(self):
        self.source(A)
        with patch.object(collector, 'MAX_SCAN', 0):
            with self.assertRaises(ValueError): self.run_collect()
        self.assertFalse(self.out.exists())

    def test_overlap_roots_are_rejected(self):
        child = self.root / 'child'; child.mkdir()
        with self.assertRaises(ValueError):
            collector.collect([self.root, child], self.out, self.request)

    def test_bad_address_and_no_matches_are_rejected(self):
        with self.assertRaises(ValueError): self.run_collect()
        self.request['required'][0]['address'] = '../secrets'
        with self.assertRaises(ValueError): self.run_collect()
        self.assertFalse(self.out.exists())

    def test_atomic_create_only_publication(self):
        self.source(A)
        def raced(source, destination):
            Path(destination).write_bytes(b'other-writer')
            raise FileExistsError('simulated race')
        with patch.object(collector.os, 'link', side_effect=raced):
            with self.assertRaises(FileExistsError): self.run_collect()
        self.assertEqual(self.out.read_bytes(), b'other-writer')
        self.assertFalse(list(self.base.glob('.automix-dependencies-*')))

    def test_symbol_tokens_do_not_match_longer_hex_or_binary(self):
        self.assertEqual(collector.addresses(f'{A}__FUN_{A}.asm'), {A})
        self.assertEqual(collector.addresses(f'0{A}.asm'), set())
        self.assertEqual(collector.addresses(f'{A}.bin'), set())


if __name__ == '__main__':
    unittest.main(verbosity=2)
