#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
MODE="${1:-debug}"
VERSION_NAME="${QUIZGATE_VERSION_NAME:-1.6.7-VIDAA}"
VERSION_CODE="${QUIZGATE_VERSION_CODE:-10607}"
SDK="${ANDROID_HOME:-$HOME/android-dev/sdk}"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
BT="$SDK/build-tools/35.0.0"
AJ="$SDK/platforms/android-35/android.jar"
OUT="build"
KS="keystore/debug.keystore"
KS_ALIAS="androiddebugkey"
KS_PASS="android"
APK_NAME="quizgate-vidaa-pro-${VERSION_NAME}.apk"

mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex"
if [ ! -f "$KS" ]; then
    mkdir -p "$(dirname "$KS")"
    keytool -genkeypair -keystore "$KS" -alias "$KS_ALIAS" \
        -storepass "$KS_PASS" -keypass "$KS_PASS" -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=QuizGate VIDAA Pro,O=QuizGate,C=CN" >/dev/null 2>&1
fi
"$BT/aapt2" compile --dir app/src/main/res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$AJ" --manifest app/src/main/AndroidManifest.xml \
    --java "$OUT/gen" -A app/src/main/assets --min-sdk-version 26 --target-sdk-version 35 \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" "$OUT/res.zip"
find app/src/main/java "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac --release 11 -encoding UTF-8 -nowarn -cp "$AJ" -d "$OUT/classes" @"$OUT/sources.txt"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --lib "$AJ" --min-api 26 --output "$OUT/dex" @"$OUT/classes.txt"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -X ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-key-alias "$KS_ALIAS" \
    --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --out "$OUT/$APK_NAME" "$OUT/aligned.apk"
echo "Built $OUT/$APK_NAME"
