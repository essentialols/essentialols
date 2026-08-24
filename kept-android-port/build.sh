#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD="$ROOT/build"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"

if [[ ! -d "$ANDROID_HOME" ]]; then
  echo "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
  exit 2
fi

BT_DIR="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n1)"
PLATFORM_DIR="$(find "$ANDROID_HOME/platforms" -mindepth 1 -maxdepth 1 -type d -name 'android-*' | sort -V | tail -n1)"

AAPT2="$BT_DIR/aapt2"
D8="$BT_DIR/d8"
ZIPALIGN="$BT_DIR/zipalign"
APKSIGNER="$BT_DIR/apksigner"
ANDROID_JAR="$PLATFORM_DIR/android.jar"

for tool in "$AAPT2" "$D8" "$ZIPALIGN" "$APKSIGNER" "$ANDROID_JAR"; do
  [[ -e "$tool" ]] || { echo "Missing required Android build tool: $tool" >&2; exit 3; }
done

rm -rf "$BUILD"
mkdir -p "$BUILD/res" "$BUILD/classes" "$BUILD/dex"

echo "Android SDK: $ANDROID_HOME"
echo "Build tools: $BT_DIR"
echo "Platform:    $PLATFORM_DIR"

"$AAPT2" compile --dir "$ROOT/src/main/res" -o "$BUILD/res"
mapfile -t FLATS < <(find "$BUILD/res" -type f -name '*.flat' | sort)

"$AAPT2" link \
  -o "$BUILD/resources.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$ROOT/AndroidManifest.xml" \
  --min-sdk-version 24 \
  --target-sdk-version 35 \
  "${FLATS[@]}"

javac --release 8 \
  -classpath "$ANDROID_JAR" \
  -d "$BUILD/classes" \
  "$(find "$ROOT/src/main/java" -name '*.java' -print -quit)"

jar cf "$BUILD/classes.jar" -C "$BUILD/classes" .
"$D8" \
  --min-api 24 \
  --lib "$ANDROID_JAR" \
  --output "$BUILD/dex" \
  "$BUILD/classes.jar"

cp "$BUILD/resources.apk" "$BUILD/unsigned.apk"
(
  cd "$BUILD/dex"
  zip -q -j "$BUILD/unsigned.apk" classes.dex
)

"$ZIPALIGN" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

KEYSTORE="$BUILD/debug.keystore"
keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -storepass android \
  -alias androiddebugkey \
  -keypass android \
  -dname "CN=Kept Community Debug,O=Community,C=US" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  >/dev/null 2>&1

APK="$BUILD/kept-android-community-v0.1.apk"
"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$APK" \
  "$BUILD/aligned.apk"

"$APKSIGNER" verify --verbose --print-certs "$APK"
sha256sum "$APK" | tee "$BUILD/SHA256SUMS.txt"

printf '\nBuilt: %s\n' "$APK"
