#!/usr/bin/env bash
# Dependency-free native build. Android SDK platform 35 and build-tools 35.0.0, JDK 17+.
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to the Android SDK directory}"
bf_tools="$ANDROID_SDK_ROOT/build-tools/35.0.0"
bf_android="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
mkdir -p out/classes out/generated out/dex
"$bf_tools/aapt2" compile --dir app/src/main/res -o out/resources.zip
"$bf_tools/aapt2" link -o out/base.apk -I "$bf_android" --manifest app/src/main/AndroidManifest.xml --java out/generated --min-sdk-version 26 --target-sdk-version 35 out/resources.zip
find app/src/main/java out/generated -name '*.java' -print > out/java-sources.txt
java com.sun.tools.javac.Main -encoding UTF-8 -source 8 -target 8 -bootclasspath "$bf_tools/core-lambda-stubs.jar:$bf_android" -d out/classes @out/java-sources.txt
java sun.tools.jar.Main cf out/classes.jar -C out/classes .
"$bf_tools/d8" --lib "$bf_android" --min-api 26 --output out/dex out/classes.jar
cp out/base.apk out/unsigned.apk
(cd out/dex && zip -q ../unsigned.apk classes*.dex)
"$bf_tools/zipalign" -f -p 4 out/unsigned.apk out/aligned.apk
if [[ -n "${BF_KEYSTORE:-}" ]]; then
  : "${BF_KEY_ALIAS:?Set BF_KEY_ALIAS}"
  : "${BF_KEY_PASSWORD:?Set BF_KEY_PASSWORD}"
  "$bf_tools/apksigner" sign --ks "$BF_KEYSTORE" --ks-key-alias "$BF_KEY_ALIAS" --ks-pass env:BF_KEY_PASSWORD --key-pass env:BF_KEY_PASSWORD --out out/Black-Follow-0.3.5-test.apk out/aligned.apk
  "$bf_tools/apksigner" verify --verbose out/Black-Follow-0.3.5-test.apk
else
  echo 'Built out/aligned.apk (unsigned). Configure BF_KEYSTORE, BF_KEY_ALIAS and BF_KEY_PASSWORD to sign.'
fi
