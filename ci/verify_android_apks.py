#!/usr/bin/env python3
"""Check APK ZIP integrity, ABI contents and native ELF headers (not an install/runtime test)."""

from __future__ import annotations

import sys
import zipfile
from pathlib import Path

ABIS = ("armeabi-v7a", "arm64-v8a", "x86_64")

# Sejak v1.1.8 nama aset memakai bit arsitektur tanpa token ABI.
BIT_NAMES = {
    "XyDesk-Remote32bit.apk": "armeabi-v7a",
    "XyDesk-Remote64bit.apk": "arm64-v8a",
    "XyDesk-Remote64bit-x86.apk": "x86_64",
}


UNIVERSAL_NAME = "XyDesk-RemoteUniversal.apk"
ELF_ABIS = {"armeabi-v7a": (1, 40, 52), "arm64-v8a": (2, 183, 64), "x86_64": (2, 62, 64)}


def verify_elf(header: bytes, abi: str, name: str) -> None:
    elf_class, machine, minimum = ELF_ABIS[abi]
    if len(header) < minimum or header[:4] != b"\x7fELF":
        raise SystemExit(f"FAIL: {name}: missing/truncated ELF header")
    if header[4] != elf_class or header[5] != 1 or header[6] != 1:
        raise SystemExit(f"FAIL: {name}: wrong ELF class/endianness/version for {abi}")
    if int.from_bytes(header[16:18], "little") != 3 or int.from_bytes(header[18:20], "little") != machine:
        raise SystemExit(f"FAIL: {name}: not a shared ELF library for {abi}")



def verify(path: Path) -> None:
    if path.name == UNIVERSAL_NAME:
        # APK universal memuat SEMUA ABI sekaligus.
        required_libraries = [f"lib/{abi}/libcrypto.so" for abi in ABIS]
    elif path.name in BIT_NAMES:
        required_libraries = [f"lib/{BIT_NAMES[path.name]}/libcrypto.so"]
    else:
        abi_matches = [abi for abi in ABIS if abi in path.name]
        if len(abi_matches) != 1:
            raise SystemExit(f"FAIL: cannot determine a single Android ABI from {path.name}")
        required_libraries = [f"lib/{abi_matches[0]}/libcrypto.so"]

    try:
        with zipfile.ZipFile(path) as apk:
            entries = apk.namelist()
            names = set(entries)
            if len(names) != len(entries):
                raise SystemExit(f"FAIL: {path}: duplicate ZIP entries")
            allowed = {name.split('/')[1] for name in required_libraries}
            for name in names:
                if not (name.startswith("lib/") and name.endswith(".so")):
                    continue
                parts = name.split('/')
                if len(parts) != 3 or parts[1] not in allowed:
                    raise SystemExit(f"FAIL: {path}: unexpected native ABI/path {name}")
                with apk.open(name) as library:
                    verify_elf(library.read(64), parts[1], name)
            for required_library in required_libraries:
                if required_library not in names:
                    raise SystemExit(f"FAIL: {path}: missing {required_library}")
            bad_entry = apk.testzip()
            if bad_entry is not None:
                raise SystemExit(f"FAIL: {path}: ZIP CRC/decompression error in {bad_entry}")
    except (OSError, zipfile.BadZipFile) as error:
        raise SystemExit(f"FAIL: {path}: invalid APK/ZIP: {error}") from error

    print(f"PASS: {path}: ZIP/ELF ABI checks passed (not installation proof); {', '.join(required_libraries)} present")


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit("Usage: verify_android_apks.py APK [APK ...]")
    for name in sys.argv[1:]:
        verify(Path(name))


if __name__ == "__main__":
    main()
