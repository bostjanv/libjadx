#!/usr/bin/env bash
# Regenerate only the owned, unsigned resource fixture. No SDK download/install.
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "$0")" && pwd)
: "${AAPT2:?Set AAPT2 to the pinned aapt2 executable; see README.md}"
: "${ANDROID_JAR:?Set ANDROID_JAR to the pinned API 35 android.jar}"
WORK=$(mktemp -d)
trap 'rm -rf -- "$WORK"' EXIT
"$AAPT2" compile --dir "$ROOT/res" -o "$WORK/compiled.zip"
"$AAPT2" link -I "$ANDROID_JAR" --manifest "$ROOT/AndroidManifest.xml" \
    -A "$ROOT/assets" --emit-ids "$WORK/ids.txt" \
    -o "$WORK/resources.apk" "$WORK/compiled.zip"
# Exclude compiler metadata and normalize ZIP ordering/time/compression. This
# keeps the derived fixture reproducible across source paths and timezones.
python3 - "$WORK/resources.apk" "${1:-$ROOT/resources.apk}" <<'PY'
import sys
from zipfile import ZipFile, ZipInfo, ZIP_STORED
with ZipFile(sys.argv[1]) as source, ZipFile(sys.argv[2], "w") as target:
    for name in sorted(source.namelist()):
        if name.startswith("META-INF/"):
            continue
        entry = ZipInfo(name, (1980, 1, 1, 0, 0, 0))
        entry.compress_type = ZIP_STORED
        entry.external_attr = 0o100644 << 16
        target.writestr(entry, source.read(name))
PY
cat "$WORK/ids.txt"
