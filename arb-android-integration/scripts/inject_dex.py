#!/usr/bin/env python3
import re
import sys
import zipfile
from pathlib import Path

apk = Path(sys.argv[1])
dex = Path(sys.argv[2])
with zipfile.ZipFile(apk, 'a') as zf:
    existing = set(zf.namelist())
    indexes = [1]
    for name in existing:
        match = re.fullmatch(r'classes(\d*)\.dex', name)
        if match:
            indexes.append(int(match.group(1) or 1))
    entry = f'classes{max(indexes) + 1}.dex'
    if entry in existing:
        raise SystemExit(f'{entry} already exists')
    info = zipfile.ZipInfo(entry)
    info.compress_type = zipfile.ZIP_STORED
    zf.writestr(info, dex.read_bytes())
print(f'Injected bootstrap DEX as {entry}')
