#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_JAR:?set ANDROID_JAR to android.jar}"
: "${BUILD_TOOLS:?set BUILD_TOOLS to the build-tools directory}"
: "${KS:?set KS to your keystore path}"
: "${KS_PASS:?set KS_PASS}"
: "${KS_ALIAS:?set KS_ALIAS}"
: "${KS_KEY_PASS:=$KS_PASS}"

OUT=FixOw.apk

rm -rf gen classes dexout res.zip base.apk unsigned.apk aligned.apk "$OUT"
mkdir -p gen classes dexout

"$BUILD_TOOLS/aapt2" compile --dir res -o res.zip
"$BUILD_TOOLS/aapt2" link -o base.apk --manifest AndroidManifest.xml \
  -I "$ANDROID_JAR" --java gen -A assets res.zip

javac --release 11 -classpath "$ANDROID_JAR" -sourcepath stubs -d classes \
  src/cn/classfruit/fixow/Hook.java \
  src/cn/classfruit/fixow/MainActivity.java \
  gen/cn/classfruit/fixow/R.java

"$BUILD_TOOLS/d8" --release --lib "$ANDROID_JAR" --output dexout \
  classes/cn/classfruit/fixow/*.class

python3 - <<'PY'
import zipfile
zin = zipfile.ZipFile('base.apk')
zout = zipfile.ZipFile('unsigned.apk', 'w', zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    if it.filename == 'classes.dex':
        continue
    zout.writestr(it, zin.read(it.filename))
zout.write('dexout/classes.dex', 'classes.dex')
zout.close()
zin.close()
PY

"$BUILD_TOOLS/zipalign" -f 4 unsigned.apk aligned.apk
"$BUILD_TOOLS/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" \
  --key-pass "pass:$KS_KEY_PASS" --ks-key-alias "$KS_ALIAS" \
  --out "$OUT" aligned.apk

echo "built: $OUT"
