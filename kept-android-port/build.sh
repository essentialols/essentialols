#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD="$ROOT/build"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"

if [[ ! -d "$ANDROID_HOME" ]]; then
  echo "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
  exit 2
fi

# The temporary build branch stores MainActivity in transport-sized chunks.
# Reassemble it byte-for-byte before compilation. The downloadable source ZIP
# contains the normal single MainActivity.java file.
if [[ -d "$ROOT/v02parts" ]]; then
  cat "$ROOT/v02parts/MainActivity.part1" \
      "$ROOT/v02parts/MainActivity.part2" \
      "$ROOT/v02parts/MainActivity.part3" \
      "$ROOT/v02parts/MainActivity.part4" \
      > "$ROOT/src/main/java/com/essentialols/keptandroid/MainActivity.java"
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
  --version-code 2 \
  --version-name 0.2 \
  "${FLATS[@]}"

mapfile -t JAVA_SOURCES < <(find "$ROOT/src/main/java" -name '*.java' -type f | sort)
javac --release 8 \
  -classpath "$ANDROID_JAR" \
  -d "$BUILD/classes" \
  "${JAVA_SOURCES[@]}"

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

# Community development signing identity. The Base64-encoded keystore is kept
# with the source so v0.2+ APKs share one signer and can update in place.
# It is public by design and is NOT a secure production-distribution key.
KEYSTORE="$BUILD/kept-community.keystore"
base64 -d "$ROOT/signing/kept-community.keystore.b64" > "$KEYSTORE"

APK="$BUILD/kept-android-community-v0.2.apk"
"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias keptcommunity \
  --ks-pass pass:keptcommunity \
  --key-pass pass:keptcommunity \
  --out "$APK" \
  "$BUILD/aligned.apk"

"$APKSIGNER" verify --verbose --print-certs "$APK"
sha256sum "$APK" | tee "$BUILD/SHA256SUMS.txt"

printf '\nBuilt: %s\n' "$APK"
