# RBO Android 0.5.3

This build uses the original `RBO-Android-0.5.2-ARM64.apk` as its input. It keeps
the original engine and assets, adds the Vulkan renderer and launcher menus, and
imports compatible Turnip driver ZIPs into the app's private files directory.
Choose the system driver or an installed driver under Options with Vulkan enabled.
If a selected Turnip driver fails to initialize, the system Vulkan driver is tried
first, then the original CPU renderer. The game log records the active driver.

The build uses the checked-in [0.5.2 APK](../../downloads/RBO-Android-0.5.2-ARM64.apk)
and checks its SHA-256. On Linux x86-64, install OpenJDK, Python 3, CMake, Ninja, Android Platform
35, Build Tools 35.0.0, NDK r27d, Eclipse Compiler for Java (ECJ), and Apktool
2.12.1. Sign with the same key used for the previous Vulkan test APK to update
that app in place.

```bash
export RBO_BUILD_TOOLS=/path/to/android-sdk/build-tools/35.0.0
export ANDROID_JAR=/path/to/android-sdk/platforms/android-35/android.jar
export ANDROID_NDK_HOME=/path/to/android-ndk-r27d
export ECJ_JAR=/path/to/ecj.jar
export APKTOOL_JAR=/path/to/apktool.jar
export APK_KEYSTORE=/path/to/test-keystore.p12
export APK_KEY_ALIAS=your-key-alias
read -rs APK_KEYSTORE_PASSWORD
export APK_KEYSTORE_PASSWORD
bash Android/vulkan/build.sh
```

The output is `Android/vulkan/build/RBO-Android-0.5.3-ARM64.apk`.
Supply your own signing material and compatible Turnip driver ZIPs.

The bundled AdrenoTools source is from revision
`8fae8ce254dfc1344527e05301e43f37dea2df80`, with liblinkernsbypass from
`aa3975893d83ef1bc84c321ec60c65fbf1287887`. Their licenses are in the source
directories and in the APK.
