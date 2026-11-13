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
    if not manifest.get("name"):
        manifest["name"] = "ARB Buyer Extension"

    # Ensure GeckoView replaces any same-ID extension left behind by the
    # previous browser build. ensureBuiltIn skips equal versions.
    manifest["version"] = "4.5.2"
    manifest["version_name"] = "4.5.2"

    # Firefox/GeckoView on Android does not run MV3 background service workers.
    # Convert Chrome's service_worker entry to Firefox's MV3 event-page scripts.
    background = manifest.setdefault("background", {})
    worker = background.pop("service_worker", None)
    if worker:
        scripts = background.get("scripts", [])
        if worker not in scripts:
            scripts.append(worker)
        background["scripts"] = scripts
    background.pop("type", None)

    # Remove references to files absent from the supplied XPI. The supplied
    # package references license.js, but that file is not present; GeckoView
    # rejects content scripts whose referenced files cannot be resolved.
    for key in ("content_scripts",):
        entries = manifest.get(key, [])
        for entry in entries:
            entry["js"] = [name for name in entry.get("js", []) if (output / name).is_file()]
        manifest[key] = [entry for entry in entries if entry.get("js") or entry.get("css")]

    # Native messaging is used only for the browser's local toolbar controls.
    # This is available to bundled GeckoView extensions.
    permissions = manifest.setdefault("permissions", [])
    # The native app bridge is a privileged GeckoView API. Native messaging
    # from content scripts is not used by this build, so do not grant it.
    for permission in ("geckoViewAddons", "nativeMessaging"):
        if permission not in permissions:
            permissions.append(permission)
    # The browser UI replaces the original extension popup, which depends on
    # Chrome's identity sign-in flow and is not part of this GeckoView surface.
    manifest["permissions"] = [p for p in permissions if p != "identity"]

    # Bridge the browser's native toolbar to the extension's existing settings
    # and start/stop engine. Only explicitly supported settings are accepted.
    background = manifest.get("background", {})
    worker = background.get("service_worker") or next(iter(background.get("scripts", [])), None)
    if worker:
        worker_file = output / worker
        bridge = r"""
// ARB Browser native toolbar bridge (GeckoView built-in extension only).
try {
  const arbNativePort = chrome.runtime.connectNative("arb.browser");
  arbNativePort.onMessage.addListener((message) => {
    if (!message || message.type !== "ARB_BROWSER_SETTINGS") return;
    const allowed = [
      "running", "exactAmount", "minAmount", "maxAmount", "isAmountMode",
      "paymentMethod", "openPayment", "soundEnabled"
    ];
    const settings = {};
    for (const key of allowed) {
      if (Object.prototype.hasOwnProperty.call(message, key)) settings[key] = message[key];
    }
    chrome.storage.local.set(settings, () => {
      chrome.tabs.query({}, (tabs) => {
        for (const tab of tabs || []) {
          if (!tab.id) continue;
          const msg = settings.running
            ? { type: "SETTINGS_UPDATED", settings }
            : { type: "FORCE_STOP" };
          chrome.tabs.sendMessage(tab.id, msg, () => void chrome.runtime.lastError);
        }
      });
    });
  });
} catch (error) {
  console.error("[ARB Browser] Native toolbar bridge unavailable:", error);
}
"""
        if not worker_file.is_file():
            raise SystemExit(f"Background worker missing: {worker}")
        existing = worker_file.read_text(encoding="utf-8")
        if "ARB Browser native toolbar bridge" not in existing:
            worker_file.write_text(existing + "\n" + bridge, encoding="utf-8")

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
