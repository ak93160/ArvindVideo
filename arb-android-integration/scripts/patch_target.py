#!/usr/bin/env python3
import argparse
import json
import shutil
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', ANDROID)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--decoded', required=True)
    ap.add_argument('--xpi', required=True)
    ap.add_argument('--shim', required=True)
    args = ap.parse_args()
    decoded = Path(args.decoded)
    asset_dir = decoded / 'assets' / 'arb-bot'
    asset_dir.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(args.xpi) as zf:
        names = set(zf.namelist())
        required = ['content.js', 'background.js', 'page-hook.js', 'SERVER_ENDPOINT.js', 'alert.wav',
                    'icons/icon16.png', 'icons/icon48.png', 'icons/icon128.png', 'icons/icon_nobg.png']
        missing = [p for p in required if p not in names]
        if missing:
            raise SystemExit('XPI is missing required files: ' + ', '.join(missing))
        manifest = json.loads(zf.read('manifest.json'))
        if manifest.get('manifest_version') != 3:
            raise SystemExit('Expected Manifest V3 XPI')
        for name in required:
            dest = asset_dir / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(zf.read(name))
    shutil.copy2(args.shim, asset_dir / 'extension-shim.js')

    manifest_path = decoded / 'AndroidManifest.xml'
    tree = ET.parse(manifest_path)
    root = tree.getroot()
    app = root.find('application')
    if app is None:
        raise SystemExit('AndroidManifest.xml has no <application> element')
    provider_name = 'com.pt.ar.wallet.arbport.ArbBootstrapProvider'
    for node in app.findall('provider'):
        if node.get(f'{{{ANDROID}}}name') == provider_name:
            raise SystemExit('Bootstrap provider already exists in target manifest')
    provider = ET.Element('provider')
    provider.set(f'{{{ANDROID}}}name', provider_name)
    provider.set(f'{{{ANDROID}}}authorities', (root.get('package') or 'com.pt.ar.wallet') + '.arbport.init')
    provider.set(f'{{{ANDROID}}}exported', 'false')
    provider.set(f'{{{ANDROID}}}initOrder', '999')
    app.append(provider)

    permission_name = 'android.permission.POST_NOTIFICATIONS'
    if not any(el.get(f'{{{ANDROID}}}name') == permission_name for el in root.findall('uses-permission')):
        permission = ET.Element('uses-permission')
        permission.set(f'{{{ANDROID}}}name', permission_name)
        root.insert(0, permission)

    tree.write(manifest_path, encoding='utf-8', xml_declaration=True)
    print('Patched manifest and staged extension assets in', asset_dir)


if __name__ == '__main__':
    main()
