# RBO Android 0.6.7 implementation notes

## Companion and cheats

The companion follows one player and exposes Move List, Monsters and Status.
The secondary display opens on Monsters; the single-screen paused overlay opens
on Move List. Auto follows the selected class and gender; manual browsing stops
following until Auto is selected again. Status includes level, EXP, HP/SP,
six attributes and unspent points.

Monsters shows only the total living, spawned enemies in the current area.
Future waves are excluded. Zero means Area clear, not completion of the stage.
Unavailable telemetry shows a waiting state. There are no monster pictures or
stage completion percentages. Guest memory is sampled only on the emulator
thread, at most once per six frames. The UI polls a copied snapshot at 5 Hz and
stops polling when detached. Signatures and bounded pointers guard decoding.

HP/SP cheats refill the current actor in a stage. Independent Stat Point and
Skill Point switches maintain 999 once a selected profile is initialized, even
before entering a stage. Disabling a switch stops refilling without removing
points already granted. Writes run on the emulator thread with EX3 signatures
and profile validation. Save files are not rewritten directly.

Options selects ENG or THAI, defaults to ENG, and persists the selection.
The catalog covers launcher, dialogs, controls, save slots and companion labels.
Move names retain their official spelling. This does not patch game language.

## Rendering and resource I/O

Performance mode defaults to ON and is selected before launch. Adjacent Vulkan
triangle lists are batched only with identical render state. Uploads, readbacks,
synchronization and releases flush pending work. Primitive order is preserved.

Read-only DATA assets use a 32 MiB LRU cache of raw 128 KiB file blocks. One
low-priority worker reads adjacent blocks with duplicate host descriptors and
an eight-job queue. It never touches guest memory. Existing resource handling
owns decoded images and GPU textures. First-use reads can still be synchronous.
Writable opens and SaveData bypass the cache. Original guest handles stay real,
and cached reads synchronize their cursors. State load discards mirror metadata;
restored handles use original reads until reopened.

The pinned base core inlines kernel registration in hle_init. Guarded ARM64
ADRP/ADD decoding locates its existing HLE table, verifies names, argument counts
and 32-byte entries, then replaces four file callbacks. No entries or names are
added, preserving the table hash. Asset I/O logs report connected handlers and
actual open/read activity.

A native path prepares and clips EX3 effect quads at 0x44f340 through 0x44f518.
It preserves x86 double operations, float-store boundaries, registers, flags and
physical x87 state. Guard failures resume original instructions. A reserved
wrapper dispatcher slot installs the hook after PE load and state restore;
Performance mode OFF restores the original prologue. A saved CPU stopped at
the second prologue instruction is advanced safely. States containing this hook
require this implementation or a compatible later build. Effect counts, game
logic, timing and rendering quality are unchanged.

Vulkan perf, Asset cache, Asset I/O, CPU hotspots and Skill native logs expose
batching, cache use and native-path activity. Recorded elapsed times can overlap;
they must not be summed as exclusive CPU/GPU costs.

## Intro audio and packaging

The opening movie contains MPEG Layer I audio. The minimp3 fallback demuxes
PES-spanning audio frames, resamples to stereo 44.1 kHz and feeds the existing
movie mixer using its clock and gain. Pause, rewind and close manage queued PCM.
The decoder source revision and CC0 license are included under vendor/minimp3.

APK assembly replaces every rebuilt DEX, including the CheatMenu in classes2.
The package is com.nakpaaiyaa.rbo.cmp0600, named RBO Android, version code 33.
Updating an installed app requires matching package ID and signing certificate.
Signing material and original game files are not distributed in the source.

## Validation

Host tests exercise production C modules, not copies of their algorithms.
The native quad path matches the real repository x86 interpreter over 30,000
randomized cases, including non-finite values, clipping, registers, flags and FPU
state. Tests cover installed dispatch, disable, fallback and prologue state load.
Cache/I/O tests cover reuse, concurrent reads, prefetch, eviction, EOF, cursor
behavior and state restore. Batch tests cover order and flush boundaries.
Decoder, point cheat, language and final APK contents are checked separately.
The actual opening movie decodes for approximately 96 seconds, including mute,
rewind and EOF checks. Native test assertions remain enabled in optimized builds.

The user confirmed satisfactory play on Snapdragon 7 Gen 4. Host checks do not
measure Android FPS; performance depends on the device, scene, heat and driver.
See tests/README.md for the host checks and releases/v0.6.7.md for user notes.
