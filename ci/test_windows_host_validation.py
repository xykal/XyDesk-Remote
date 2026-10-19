import struct
import unittest
from verify_windows_host import inspect_pe, validate_imports


def example():
    data = bytearray(256)
    data[:2] = b'MZ'
    struct.pack_into('<I', data, 60, 64)
    data[64:68] = b'PE\0\0'
    struct.pack_into('<H', data, 68, 0x8664)
    struct.pack_into('<HH', data, 84, 112, 2)
    struct.pack_into('<H', data, 88, 0x20b)
    struct.pack_into('<H', data, 156, 2)
    return data


class WindowsHostValidationTest(unittest.TestCase):
    def test_x64_gui_header(self):
        self.assertEqual(inspect_pe(example())['format'], 'PE32+')

    def test_invalid_truncated_or_dll_headers_rejected(self):
        for data in (b'', b'MZ', bytes(256)):
            with self.assertRaises(ValueError): inspect_pe(data)
        for offset, value in ((68, 0x14c), (88, 0x10b), (156, 3), (86, 0x2002)):
            data = example()
            struct.pack_into('<H', data, offset, value)
            with self.assertRaises(ValueError): inspect_pe(data)

    def test_only_expected_os_imports(self):
        validate_imports(['KERNEL32.dll', 'WS2_32.dll', 'msvcrt.dll'])
        for names in ([], ['xydesk_quic.dll'], ['libstdc++-6.dll'], ['libwinpthread-1.dll'], ['VCRUNTIME140D.dll']):
            with self.assertRaises(ValueError): validate_imports(names)
