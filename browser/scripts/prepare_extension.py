#!/usr/bin/env python3
import argparse
import json
import pathlib
import shutil
import zipfile

FIXED_ID = "arbbuyer.bundle@arb-browser.local"

def safe_extract(zf: zipfile.ZipFile, root_prefix: str, target: pathlib.Path) -> None:
    target_resolved = target.resolve()
    for info in zf.infolist():
        name = info.filename
        if name.startswith("/") or "\\" in name:
            raise RuntimeError(f"Unsafe path in XPI: {name}")
        rel = pathlib.PurePosixPath(name)
        if any(part in ("..", "") for part in rel.parts):
            raise RuntimeError(f"Unsafe path in XPI: {name}")
        if root_prefix and not name.startswith(root_prefix):
            continue
        stripped = name[len(root_prefix):] if root_prefix else name
        if not stripped:
            continue
        destination = (target / pathlib.Path(*pathlib.PurePosixPath(stripped).parts)).resolve()
        if target_resolved not in destination.parents and destination != target_resolved:
            raise RuntimeError(f"Path escaped target: {name}")
        if info.is_dir():
            destination.mkdir(parents=True, exist_ok=True)
        else:
            destination.parent.mkdir(parents=True, exist_ok=True)
            with zf.open(info) as src, destination.open("wb") as dst:
                shutil.copyfileobj(src, dst)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--xpi", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    xpi = pathlib.Path(args.xpi)
    output = pathlib.Path(args.out)
    if not xpi.is_file():
        raise SystemExit(f"XPI not found: {xpi}")
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)

    with zipfile.ZipFile(xpi) as archive:
        manifests = [n for n in archive.namelist() if n.endswith("manifest.json") and not n.startswith("__MACOSX/")]
        if not manifests:
            raise SystemExit("XPI has no manifest.json")
        manifest_path = min(manifests, key=len)
        prefix = manifest_path[:-len("manifest.json")]
        safe_extract(archive, prefix, output)

    manifest_file = output / "manifest.json"
    if not manifest_file.is_file():
        raise SystemExit("Could not extract root manifest.json")
    manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
    if manifest.get("manifest_version") not in (2, 3):
        raise SystemExit("Only WebExtension manifest_version 2 or 3 is supported")
    if not manifest.get("version"):
        manifest["version"] = "1.0.0"
    if not manifest.get("name"):
        manifest["name"] = "ARB Buyer Extension"

    # Bundled GeckoView extensions can be installed without AMO signing.
    browser = manifest.setdefault("browser_specific_settings", {})
    gecko = browser.setdefault("gecko", {})
    gecko["id"] = FIXED_ID
    applications = manifest.setdefault("applications", {})
    old_gecko = applications.setdefault("gecko", {})
    old_gecko["id"] = FIXED_ID
    manifest_file.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (output.parent / "extension_id.txt").write_text(FIXED_ID + "\n", encoding="utf-8")
    print(f"Prepared bundled WebExtension: {manifest['name']} {manifest['version']} (MV{manifest['manifest_version']})")
    print(f"GeckoView extension id: {FIXED_ID}")

if __name__ == "__main__":
    main()
