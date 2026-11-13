#!/usr/bin/env python3
"""Validate an XPI manifest and referenced content scripts without modifying it."""
import json
import sys
import zipfile

archive = sys.argv[1] if len(sys.argv) > 1 else "extension.xpi"
with zipfile.ZipFile(archive) as zf:
    names = set(zf.namelist())
    manifest = json.loads(zf.read("manifest.json"))
    missing = []
    for group in manifest.get("content_scripts", []):
        for relative_path in group.get("js", []):
            if relative_path not in names:
                missing.append(relative_path)
    print(f"Extension: {manifest.get('name')} {manifest.get('version')}")
    print(f"Manifest version: {manifest.get('manifest_version')}")
    print(f"Archive entries: {len(names)}")
    print("Missing script dependencies:", missing or "none")
    if missing:
        sys.exit(2)
