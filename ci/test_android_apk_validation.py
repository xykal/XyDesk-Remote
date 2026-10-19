"""Synthetic ZIP/ELF fixtures only; never produces a runnable APK."""
import tempfile
import unittest
import zipfile
from pathlib import Path
from verify_android_apks import verify, ELF_ABIS


def elf(abi):
    cls, machine, size = ELF_ABIS[abi]
    data = bytearray(size)
    data[:7] = b'\x7fELF' + bytes([cls, 1, 1])
    data[16:18] = (3).to_bytes(2, 'little')
    data[18:20] = machine.to_bytes(2, 'little')
    return bytes(data)


class AndroidApkValidationTest(unittest.TestCase):
    def check_fixture(self, entries, filename='XyDesk-Remote32bit.apk'):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / filename
            with zipfile.ZipFile(path, 'w') as archive:
                for name, data in entries.items():
                    archive.writestr(name, data)
            verify(path)

    def test_arm32_headers_accepted(self):
        self.check_fixture({'lib/armeabi-v7a/libcrypto.so': elf('armeabi-v7a')})

    def test_arm64_disguised_in_arm32_directory_rejected(self):
        with self.assertRaises(SystemExit):
            self.check_fixture({'lib/armeabi-v7a/libcrypto.so': elf('arm64-v8a')})

    def test_wrong_machine_even_with_correct_elf_class_rejected(self):
        data = bytearray(elf('armeabi-v7a')); data[18:20] = (3).to_bytes(2, 'little')
        with self.assertRaises(SystemExit):
            self.check_fixture({'lib/armeabi-v7a/libcrypto.so': bytes(data)})

    def test_additional_wrong_abi_payload_rejected(self):
        with self.assertRaises(SystemExit):
            self.check_fixture({'lib/armeabi-v7a/libcrypto.so': elf('armeabi-v7a'), 'lib/arm64-v8a/other.so': elf('arm64-v8a')})

    def test_every_library_checked_not_only_crypto(self):
        with self.assertRaises(SystemExit):
            self.check_fixture({'lib/armeabi-v7a/libcrypto.so': elf('armeabi-v7a'), 'lib/armeabi-v7a/engine.so': b'not ELF'})

    def test_universal_requires_three_matching_abis(self):
        entries = {f'lib/{abi}/libcrypto.so': elf(abi) for abi in ELF_ABIS}
        self.check_fixture(entries, 'XyDesk-RemoteUniversal.apk')
        del entries['lib/armeabi-v7a/libcrypto.so']
        with self.assertRaises(SystemExit):
            self.check_fixture(entries, 'XyDesk-RemoteUniversal.apk')
