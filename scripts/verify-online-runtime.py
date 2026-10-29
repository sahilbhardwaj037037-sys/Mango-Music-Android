#!/usr/bin/env python3
"""Verify the bundled ARM64 QuickJS binaries against reviewed runtime provenance."""
from pathlib import Path
import hashlib
import json

root = Path(__file__).resolve().parent.parent / 'online-runtime'
manifest = json.loads((root / 'runtime-provenance.json').read_text())
for name, expected in manifest['binaries'].items():
    binary = root / 'src/main/jniLibs/arm64-v8a' / name
    actual = hashlib.sha256(binary.read_bytes()).hexdigest()
    if actual != expected:
        raise SystemExit(f'Checksum mismatch: {name}')
    print(f'{name}: verified')
