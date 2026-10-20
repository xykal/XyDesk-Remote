#!/usr/bin/env python3
"""Static PE/import checks; not Authenticode, Windows startup or game/runtime proof."""
from pathlib import Path
import hashlib
import json
import re
import struct
import subprocess
import sys

SYSTEM_IMPORTS = {
    'advapi32.dll', 'dxgi.dll', 'gdi32.dll', 'gdiplus.dll', 'kernel32.dll', 'msvcrt.dll',
    'ole32.dll', 'oleaut32.dll', 'shell32.dll', 'user32.dll', 'ws2_32.dll',
    'propsys.dll', 'dwmapi.dll', 'wtsapi32.dll', 'winhttp.dll', 'iphlpapi.dll',
    'comctl32.dll', 'version.dll', 'ntdll.dll', 'ucrtbase.dll',
}


def inspect_pe(data: bytes) -> dict:
    if len(data) < 64 or data[:2] != b'MZ':
        raise ValueError('Not a Windows executable')
    offset = struct.unpack_from('<I', data, 60)[0]
    if offset + 24 > len(data) or data[offset:offset + 4] != b'PE\0\0':
        raise ValueError('Invalid PE header')
    machine, = struct.unpack_from('<H', data, offset + 4)
    optional_size, flags = struct.unpack_from('<HH', data, offset + 20)
    optional = offset + 24
    if optional_size < 112 or optional + optional_size > len(data):
        raise ValueError('Truncated PE optional header')
    magic, = struct.unpack_from('<H', data, optional)
    subsystem, = struct.unpack_from('<H', data, optional + 68)
    if machine != 0x8664 or magic != 0x20b or subsystem != 2 or not flags & 2 or flags & 0x2000:
        raise ValueError('Expected an x64 PE32+ GUI application, not a DLL')
    return {'machine': 'x86_64', 'format': 'PE32+', 'subsystem': 'Windows GUI'}


def validate_imports(names: list[str]) -> None:
    if not names or any(name.lower() not in SYSTEM_IMPORTS for name in names):
        raise ValueError('Unexpected/missing imports: companion/runtime DLL dependency detected')


def verify(path: Path) -> dict:
    data = path.read_bytes()
    result = inspect_pe(data)
    output = subprocess.check_output(['x86_64-w64-mingw32-objdump', '-p', str(path.resolve())], text=True)
    imports = sorted(set(re.findall(r'DLL Name:\s*(\S+)', output)))
    validate_imports(imports)
    result.update(file=path.name, size=len(data), sha256=hashlib.sha256(data).hexdigest(),
                  imports=imports, static_import_check='PASS', authenticode_verified=False,
                  windows_runtime_tested=False, secure_direct_implemented=False)
    return result


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: verify_windows_host.py XyDeskRemoteHost.exe')
    print(json.dumps(verify(Path(sys.argv[1])), indent=2))
