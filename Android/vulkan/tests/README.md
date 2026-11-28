# Host checks

Run from Android/vulkan with a host C compiler and Python 3. Place generated
binaries under build/ (ignored). Substitute .dll or .dylib where appropriate.
The signature and interpreter checks require your own Expansion 3 executable;
movie checks require your own DATA/Movie/opening.mpg. Game files are not bundled.

```sh
mkdir -p build
cc -shared -fPIC -O2 native/companion_decode.c -o build/companion_decode.so
python tests/test_companion_decode.py build/companion_decode.so /path/to/RBO_EX3.exe
cc -shared -fPIC -O2 native/point_cheats.c -o build/point_cheats.so
python tests/test_point_cheats.py build/point_cheats.so /path/to/RBO_EX3.exe
cc -shared -fPIC -O2 -pthread native/asset_cache.c -o build/asset_cache.so
python tests/test_asset_cache.py build/asset_cache.so
cc -O2 -I native tests/test_draw_batch.c native/draw_batch.c -o build/test_draw_batch
build/test_draw_batch
cc -O2 -I native -I ../../RBOFFios_project tests/test_asset_io.c native/asset_io.c native/asset_cache.c -pthread -o build/test_asset_io
build/test_asset_io
cc -O2 -I native -I ../../RBOFFios_project tests/test_movie_audio.c native/movie_audio.c ../../RBOFFios_project/plm_impl.c -lm -o build/test_movie_audio
build/test_movie_audio /path/to/DATA/Movie/opening.mpg
cc -O2 -I native -I ../../RBOFFios_project tests/test_skill_quad.c native/skill_quad.c native/skill_runtime.c ../../RBOFFios_project/cpu.c -lm -o build/test_skill_quad
build/test_skill_quad /path/to/RBO_EX3.exe
```

The C test files explicitly enable assertions. The skill test executes the
original executable instructions in the repository interpreter and compares
memory, registers, flags and FPU state with the native path over 30,000 cases.
It also exercises the installed hook, disable, guard fallback and state restore.

LocalizationTest.java runs with AppLanguageCatalog.java and UiText.java in a
host JVM. test_packaged_apk.py accepts the final signed APK and checks it against
all rebuilt DEX and native files in build/. See each test source for arguments.
Android rendering and device speed should also be verified through actual play.


## LAN checks

Compile LanProtocol.java, LanPeer.java, LanFiles.java and tests/LanTest.java in a
host JVM (package com.nakpaaiyaa.rbo), then run com.nakpaaiyaa.rbo.LanTest.
It exercises real TCP loopback input exchange, P1/P2 assignment, room codes,
state-check mismatches, fragmented reads, single-write packets and temporary saves.

Build tests/test_lan_input.c with the native and RBOFFios_project include paths,
plus the host JDK's include and platform include directories. It tests the actual
native keyboard hook with a mock JVM transport, including solo bypass and disconnect.
These host checks do not measure Android audio or frame rate.
