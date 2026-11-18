#!/usr/bin/env bash
set -euo pipefail

project=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
core="$project/../RBOFFios_project"
build="$project/build"
platform="${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory}/platforms/android-35/android.jar"
tools="$ANDROID_HOME/build-tools/35.0.0"
ndk="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/27.3.13750724}/toolchains/llvm/prebuilt/linux-x86_64/bin"
ecj="${ECJ_JAR:?Set ECJ_JAR to the Eclipse compiler JAR}"
apk="$build/RBO-Odin3-Android-0.4-ARM64.apk"

mkdir -p "$build/package/lib/arm64-v8a"
rm -rf -- "$build/classes" "$build/dex"
mkdir -p "$build/classes" "$build/dex"
"$tools/aapt2" compile --dir "$project/res" -o "$build/resources.zip"
"$tools/aapt2" link -I "$platform" --manifest "$project/AndroidManifest.xml" \
    -o "$build/unsigned.apk" "$build/resources.zip"
mapfile -t java_sources < <(find "$project/src" -name '*.java' | sort)
java -jar "$ecj" -nowarn -proc:none -1.8 -bootclasspath "$platform:$tools/core-lambda-stubs.jar" \
    -d "$build/classes" "${java_sources[@]}"
mapfile -t java_classes < <(find "$build/classes" -name '*.class' | sort)
"$tools/d8" --min-api 28 --lib "$platform" --output "$build/dex" "${java_classes[@]}"
"$ndk/aarch64-linux-android28-clang" -std=gnu11 -O3 -fno-strict-aliasing -ffp-contract=off -fPIC -shared \
    -DRBO_LIBRARY=1 -DRBO_NO_GPU=1 -I"$core" -Wl,-z,max-page-size=16384 -Wl,--no-undefined \
    -o "$build/package/lib/arm64-v8a/librbo_android.so" "$project/native/bridge.c" \
    "$core"/{cpu,hle,win,dx,d3d,dshow,plm_impl,sched,pe,hooks,state,main,rbo_api}.c -landroid -lm -lz
"$ndk/llvm-strip" --strip-unneeded "$build/package/lib/arm64-v8a/librbo_android.so"
cp "$build/dex/classes.dex" "$build/package/"
(cd "$build/package" && zip -q -0 ../unsigned.apk classes.dex lib/arm64-v8a/librbo_android.so)
"$tools/zipalign" -P 16 -f 4 "$build/unsigned.apk" "$build/aligned.apk"
if [[ -n ${APK_KEYSTORE:-} ]]; then
    : "${APK_KEY_ALIAS:?Set APK_KEY_ALIAS}" "${APK_KEYSTORE_PASSWORD:?Set APK_KEYSTORE_PASSWORD}"
    "$tools/apksigner" sign --ks "$APK_KEYSTORE" --ks-key-alias "$APK_KEY_ALIAS" \
        --ks-pass env:APK_KEYSTORE_PASSWORD --v4-signing-enabled false --out "$apk" "$build/aligned.apk"
    "$tools/apksigner" verify "$apk"
    printf 'Signed APK: %s\n' "$apk"
else
    printf 'Unsigned APK: %s\n' "$build/aligned.apk"
fi
