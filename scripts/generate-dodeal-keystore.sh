#!/usr/bin/env bash
# Generate a permanent production keystore for ONE Dodeal app. Run once per app.
# Each app (tracking, updater, acr, dialer) has its OWN permanent key — never shared, never
# regenerated. Do NOT commit the .jks or passwords. Store securely + keep backups.
#
# Usage: scripts/generate-dodeal-keystore.sh <alias> [out_dir]
#   e.g. scripts/generate-dodeal-keystore.sh dodeal-tracking ~/.dodeal-signing
#        scripts/generate-dodeal-keystore.sh dodeal-updater  ~/.dodeal-signing
set -euo pipefail

ALIAS="${1:?alias required, e.g. dodeal-tracking}"
OUT_DIR="${2:-$HOME/.dodeal-signing}"
KEYSTORE="$OUT_DIR/$ALIAS-release.jks"

mkdir -p "$OUT_DIR"
if [ -f "$KEYSTORE" ]; then
  echo "Refusing to overwrite existing keystore: $KEYSTORE"
  echo "Production keys must never be regenerated. Aborting."
  exit 1
fi

echo "Creating permanent keystore: $KEYSTORE  (alias: $ALIAS)"
echo "Use a real DN (NOT 'Android Debug'), e.g. CN=Dodeal, OU=Mobile, O=Dodeal, L=Cairo, C=EG"
echo
keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -sigalg SHA256withRSA \
  -validity 10000 -storetype PKCS12

chmod 600 "$KEYSTORE"
echo
echo "Done: $KEYSTORE"
echo "NEXT: back it up securely; add its entries to keystore.properties; then"
echo "      scripts/print-signing-info.sh $KEYSTORE $ALIAS   # to get the SHA-256 to pin"
