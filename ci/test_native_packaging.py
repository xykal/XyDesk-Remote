"""Exercise the real CMake alias guard without building Android/native code."""
import re
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LIBS = ('libfreerdp3.so', 'libfreerdp-client3.so', 'libwinpr3.so')
SCRIPT = ROOT / 'client/Android/cmake/VerifyNativeAliases.cmake'


class NativePackagingTest(unittest.TestCase):
    def run_guard(self, changed=None, missing=None, extra=False):
        with tempfile.TemporaryDirectory(prefix='native aliases ') as directory:
            source, packaged = Path(directory)/'source', Path(directory)/'packaged'
            source.mkdir(); packaged.mkdir()
            for name in LIBS:
                (source/name).write_bytes(b'test library '+name.encode())
                if name != missing:
                    (packaged/name).write_bytes(b'changed' if name==changed else (source/name).read_bytes())
            if extra:
                (source/'unrelated.so').write_bytes(b'first')
                (packaged/'unrelated.so').write_bytes(b'second')
            result = subprocess.run(['cmake', f'-DSOURCE_LIB_DIR={source}',
                                   f'-DPACKAGED_LIB_DIR={packaged}', '-P', str(SCRIPT)],
                                  capture_output=True, text=True)
            result.packaged_names = {p.name for p in packaged.iterdir()}
            result.source_names = {p.name for p in source.iterdir()}
            return result

    def test_identical_aliases_pass_with_space_in_paths(self):
        result = self.run_guard()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.count('VerifyNativeAliases: identical'), 3)
        self.assertTrue(set(LIBS).issubset(result.source_names))
        self.assertFalse(set(LIBS).intersection(result.packaged_names))

    def test_each_different_alias_fails(self):
        for name in LIBS:
            with self.subTest(name=name):
                result = self.run_guard(changed=name)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('differing binaries', result.stderr)
                self.assertTrue(set(LIBS).issubset(result.packaged_names))

    def test_each_missing_alias_fails(self):
        for name in LIBS:
            with self.subTest(name=name):
                result = self.run_guard(missing=name)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('missing', result.stderr)

    def test_no_gradle_collision_bypass(self):
        gradle = (ROOT/'client/Android/Studio/freeRDPCore/build.gradle').read_text()
        self.assertNotIn('pickFirst', gradle)
        self.assertNotIn("'**/*.so'", gradle)

    def test_unrelated_codecs_are_preserved(self):
        result = self.run_guard(extra=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('unrelated.so', result.packaged_names)
        self.assertFalse(set(LIBS).intersection(result.packaged_names))
