# RBO Android 0.7.0 — LAN Experimental

This is an experimental release. See [LAN.md](LAN.md) for setup and limitations.
LAN follows the renderer selection and enables performance acceleration. Cheats
and Save States are disabled in LAN; its saves are temporary copies of the host's data.

See the [release changelog](../../CHANGELOG.md) for the complete feature description.
The companion provides Move List / Monsters / Status for the active single
character. Secondary displays start on Monsters; single-screen devices can open
the same tabs from the in-game Move List control. Options includes ENG / THAI
(default ENG), renderer/driver selection, and Performance mode (default ON).

Cheat opens HP/SP and Stat Point / Skill Point controls. Point switches keep each
enabled point type at 999 and can be enabled before character selection.
MPEG Layer I intro audio is decoded in software and mixed with the game output.

Implementation and validation details are in [COMPANION.md](COMPANION.md).

This build uses the original `RBO-Android-0.5.2-ARM64.apk` as its input. It keeps
the original engine and assets, adds the Vulkan renderer and launcher menus, and
imports compatible Turnip driver ZIPs into the app's private files directory.
Choose the system driver or an installed driver under Options with Vulkan enabled.
If a selected Turnip driver fails to initialize, the system Vulkan driver is tried
first, then the original CPU renderer. The game log records the active driver.

The Move List appears on a compatible secondary display. On a single screen,
select **Moves** in-game and close the list with **×** or **Back**. The disk
icon opens the ten save-state slots with save times and gameplay thumbnails.

The build uses the checked-in [0.5.2 APK](../../downloads/RBO-Android-0.5.2-ARM64.apk)
and checks its SHA-256. On Linux x86-64, install OpenJDK, Python 3, CMake, Ninja, Android Platform
35, Build Tools 35.0.0, NDK r27d, Eclipse Compiler for Java (ECJ), and Apktool
2.12.1. The built app ID is `com.nakpaaiyaa.rbo.cmp0600`. An in-place update requires
that ID and the same certificate as the installed application. Signing keys are
not included in this repository.

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

The output is `Android/vulkan/build/RBO-Android-0.7.0-LAN-Experimental-ARM64.apk`.
Supply your own signing material and compatible Turnip driver ZIPs.

The bundled AdrenoTools source is from revision
`8fae8ce254dfc1344527e05301e43f37dea2df80`, with liblinkernsbypass from
`aa3975893d83ef1bc84c321ec60c65fbf1287887`. Their licenses are in the source
directories and in the APK.

The movie-audio fallback vendors minimp3 at the revision recorded in
[vendor/minimp3/SOURCE.txt](vendor/minimp3/SOURCE.txt), under CC0. Its license is
included in the source and APK. The original game files are not included.

For host-side tests, see [tests/README.md](tests/README.md).
