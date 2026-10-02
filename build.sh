#!/bin/bash
# 构建 ClearAllPin 模块 APK
# 必须用 JDK8 javac，javac21 输出 d8 8.2 解析会崩；d8/apksigner/keytool 用 JDK21。
set -e

ROOT="/e/Downloads/clearall-module"
TC="$ROOT/toolchain"
JAVA="/c/Users/wangduofu/.jdks/corretto-21.0.10/bin"
JAVAC8="/c/Users/wangduofu/.jdks/corretto-1.8.0_504/bin"
export JAVA_HOME='C:\Users\wangduofu\.jdks\corretto-21.0.10'
export PATH="$JAVA:$PATH"

win() { echo "$1" | sed 's|^/e/|E:/|'; }

[ -x "$JAVAC8/javac.exe" ] || { echo "JDK8 not found at $JAVAC8"; exit 1; }
BT=""
PLT=""
for d in "$TC"/android-*; do
    [ -f "$d/aapt2.exe" ] && BT="$d"
    [ -f "$d/android.jar" ] && PLT="$d"
done
[ -n "$BT" ] && [ -n "$PLT" ] || { echo "toolchain missing: BT=$BT PLT=$PLT"; exit 1; }
echo "BT  = $BT"
echo "PLT = $PLT"

AJ="$PLT/android.jar"
OUT="$ROOT/out"
rm -rf "$OUT"
mkdir -p "$OUT/stubs" "$OUT/classes" "$OUT/dex"

echo "[1/6] compile stubs (javac8)"
find "$ROOT/src-stubs" -name '*.java' | sed 's|^/e/|E:/|' > "$OUT/stubs.list"
"$JAVAC8/javac.exe" -encoding UTF-8 -d "$(win "$OUT/stubs")" @"$OUT/stubs.list"

echo "[2/6] compile module (javac8)"
find "$ROOT/src" -name '*.java' | sed 's|^/e/|E:/|' > "$OUT/src.list"
"$JAVAC8/javac.exe" -encoding UTF-8 \
    -cp "$(win "$AJ");$(win "$OUT/stubs")" -d "$(win "$OUT/classes")" @"$OUT/src.list"

echo "[3/6] dex"
(cd "$OUT/classes" && "$JAVAC8/jar.exe" cf "$(win "$OUT/classes.jar")" io)
"$JAVA/java" -cp "$(win "$BT/lib/d8.jar")" com.android.tools.r8.D8 \
    --min-api 29 --lib "$(win "$AJ")" --output "$(win "$OUT/dex")" "$(win "$OUT/classes.jar")"

echo "[4/6] link resources"
"$BT/aapt2.exe" compile --dir "$(win "$ROOT/res")" -o "$(win "$OUT/res.zip")"
"$BT/aapt2.exe" link -o "$(win "$OUT/base.apk")" -I "$(win "$AJ")" \
    --manifest "$(win "$ROOT/AndroidManifest.xml")" "$(win "$OUT/res.zip")" \
    --min-sdk-version 29 --target-sdk-version 35

echo "[5/6] assemble apk"
cp "$OUT/base.apk" "$OUT/app.apk"
"$JAVAC8/jar.exe" uf "$(win "$OUT/app.apk")" -C "$(win "$OUT/dex")" classes.dex
"$JAVAC8/jar.exe" uf "$(win "$OUT/app.apk")" -C "$(win "$ROOT")" assets/xposed_init

echo "[6/6] zipalign + sign"
"$BT/zipalign.exe" -f 4 "$(win "$OUT/app.apk")" "$(win "$OUT/aligned.apk")"
KS="$ROOT/keystore/debug.jks"
if [ ! -f "$KS" ]; then
    mkdir -p "$ROOT/keystore"
    "$JAVA/keytool" -genkeypair -keystore "$(win "$KS")" -storepass android -keypass android \
        -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=debug"
fi
"$JAVA/java" -jar "$(win "$BT/lib/apksigner.jar")" sign \
    --ks "$(win "$KS")" --ks-pass pass:android --key-pass pass:android \
    --out "$(win "$ROOT/ClearAllPin.apk")" "$(win "$OUT/aligned.apk")"
"$JAVA/java" -jar "$(win "$BT/lib/apksigner.jar")" verify --print-certs "$(win "$ROOT/ClearAllPin.apk")"

ls -la "$ROOT/ClearAllPin.apk"
echo "BUILD OK"
