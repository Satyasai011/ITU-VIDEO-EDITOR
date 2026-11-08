#!/usr/bin/env python3
"""Verify APK integrity and compare its public certificate with the delivered M1.1 baseline.

This never bypasses Android signature checks and never treats a public certificate as a private key.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--apk', required=True, type=Path)
parser.add_argument('--apksigner', required=True)
parser.add_argument('--aapt', required=True)
parser.add_argument('--baseline', default='config/m11-signing-certificate.sha256', type=Path)
parser.add_argument('--out', required=True, type=Path)
parser.add_argument('--require-match', action='store_true')
args = parser.parse_args()
verification = subprocess.check_output([args.apksigner, 'verify', '--verbose', '--print-certs', str(args.apk)], text=True)
certificates = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', verification)
if len(certificates) != 1:
    raise SystemExit('Expected exactly one verified APK signing certificate.')
certificate = certificates[0].lower()
baseline = args.baseline.read_text().strip().lower().replace(':', '')
if not re.fullmatch('[0-9a-f]{64}', baseline):
    raise SystemExit('Invalid reference certificate fingerprint.')
badging = subprocess.check_output([args.aapt, 'dump', 'badging', str(args.apk)], text=True)
package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
if package is None or package.group(1) != 'com.itu.videoeditor' or int(package.group(2)) < 3:
    raise SystemExit('Application ID or M1.2 version code is incorrect.')
match = certificate == baseline
report = {
    'applicationId': package.group(1), 'versionCode': int(package.group(2)), 'versionName': package.group(3),
    'apkSha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(), 'apkSignatureVerified': True,
    'signingCertificateSha256': certificate, 'deliveredM11CertificateSha256': baseline,
    'matchesDeliveredM11Certificate': match, 'installedDeviceInspected': False,
    'status': 'MATCHES_DELIVERED_M11_CERTIFICATE' if match else 'FRESH_INSTALL_ONLY_NOT_AN_M11_UPDATE',
    'dataProtection': 'Do not uninstall an existing app holding projects to resolve a signing conflict. No installed data has been modified. A device-level update is not verified.'
}
args.out.parent.mkdir(parents=True, exist_ok=True)
args.out.write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
if args.require_match and not match:
    raise SystemExit('The supplied signing key does not match the M1.1 baseline. Update artifact publication is blocked.')
