#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
project="$(pwd)"
tools="${RBO_BUILD_TOOLS:?Set RBO_BUILD_TOOLS}"
ndk="${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME}"
sdk="${ANDROID_JAR:?Set ANDROID_JAR}"
apktool="${APKTOOL_JAR:?Set APKTOOL_JAR}"
ecj="${ECJ_JAR:?Set ECJ_JAR}"
cc="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang"
shaders="$ndk/shader-tools/linux-x86_64/glslc"
engine="$project/../../RBOFFios_project"
native="$project/../native"
base="$project/../../downloads/RBO-Android-0.5.2-ARM64.apk"
output="$project/build/RBO-Android-0.5.7-Move-List-ARM64.apk"
mkdir -p build/classes build/settings-dex
python3 assemble.py extract

cmake="${CMAKE_BIN:-cmake}"
ninja="${NINJA_BIN:-ninja}"
vendor="$project/vendor/libadrenotools"
"$cmake" -S "$vendor" -B build/adrenotools -G Ninja \
    -DCMAKE_MAKE_PROGRAM="$(command -v "$ninja")" \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_static \
    -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON -DBUILD_SHARED_LIBS=ON -DCMAKE_BUILD_TYPE=Release
"$cmake" --build build/adrenotools --target adrenotools main_hook
cp build/adrenotools/libadrenotools.so build/
cp build/adrenotools/src/hook/{libhook_impl,libmain_hook}.so build/
python3 native/generate_dispatch.py "$native/vulkan_raster.c" build/vulkan_dispatch.h

for stage in vert frag; do
    "$shaders" -O --target-env=vulkan1.0 "$native/shaders/d3d.$stage" -o "$project/build/d3d.$stage.spv"
done
python3 "$native/shaders/embed_spirv.py" "$project/build"
common=(-std=gnu11 -O3 -fPIC -fvisibility=hidden -ffp-contract=off -fno-strict-aliasing -I"$engine" -I"$project/build" -I"$project/native" -I"$vendor/include")
"$cc" "${common[@]}" -DRBO_GPU=1 \
    -Dd3d_register=vulkan_d3d_register -Dd3d_get_object=vulkan_d3d_get_object \
    -Dd3d_state=vulkan_d3d_state -Dd3d_state_flush=vulkan_d3d_state_flush \
    -Dd3d_gpu_report=vulkan_d3d_gpu_report -Dgpu_surf_access=vulkan_gpu_surf_access \
    -Dgpu_surf_release=vulkan_gpu_surf_release -Dd3d_diag=vulkan_d3d_diag \
    -c "$engine/d3d.c" -o build/d3d.o
"$cc" "${common[@]}" -Wall -Wextra -Werror -shared -Wl,-z,max-page-size=16384 \
    -Wl,-soname,librbo_vulkan.so -Wl,--no-undefined -o build/librbo_vulkan.so \
    -DRBO_VULKAN_DRIVERS=1 native/renderer_bridge.c native/vulkan_driver.c "$native/vulkan_raster.c" build/d3d.o \
    -L"$project/build/original-libs" -Wl,--no-as-needed -l:librbo_android.so -l:librbo_core044.so \
    -ldl -lm

java -jar "$ecj" -nowarn -proc:none -1.8 \
    -bootclasspath "$sdk:$tools/core-lambda-stubs.jar" \
    -d build/classes java/com/nakpaaiyaa/rbo/*.java
mapfile -t classes < <(find build/classes -name '*.class' | sort)
"$tools/d8" --min-api 28 --lib "$sdk" --output build/settings-dex "${classes[@]}"
java -jar "$apktool" d -f -r "$base" -o decoded
python3 assemble.py smali
java -jar "$apktool" b -f decoded -o build/modified-dex.apk
python3 assemble.py apk
"$tools/zipalign" -P 16 -f 4 build/unsigned.apk build/aligned.apk
"$tools/apksigner" sign --ks "$APK_KEYSTORE" --ks-key-alias "$APK_KEY_ALIAS" \
    --ks-pass env:APK_KEYSTORE_PASSWORD --v4-signing-enabled false --out "$output" build/aligned.apk
"$tools/apksigner" verify --min-sdk-version 28 "$output"
"$tools/zipalign" -c -P 16 4 "$output"
