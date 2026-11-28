"""Check the delivered APK, including the secondary DEX that holds CheatMenu."""
from pathlib import Path
from zipfile import ZipFile
import sys

root = Path(__file__).resolve().parents[1]
with ZipFile(sys.argv[1]) as apk, ZipFile(root/'build/modified-dex.apk') as modified:
    dex_names = sorted(n for n in modified.namelist() if n.startswith('classes') and n.endswith('.dex'))
    assert 'classes2.dex' in dex_names
    for name in dex_names:
        assert apk.read(name) == modified.read(name), f'Outdated DEX shipped: {name}'
    assert apk.read('classes3.dex') == (root/'build/settings-dex/classes.dex').read_bytes()
    assert b'Lcom/nakpaaiyaa/rbo/PointCheats;' in apk.read('classes2.dex')
    assert b'Lcom/nakpaaiyaa/rbo/CheatVitalsAction;' in apk.read('classes2.dex')
    assert b'Stat Point' in apk.read('classes3.dex')
    library = apk.read('lib/arm64-v8a/librbo_vulkan.so')
    assert library == (root/'build/librbo_vulkan.so').read_bytes()
    for symbol in [b'Java_com_nakpaaiyaa_rbo_PointCheats_flagsNative',
                   b'Java_com_nakpaaiyaa_rbo_PointCheats_enableNative', b'dshow_make_graph',
                   b'[Movie audio] MPEG Layer I fallback=',
                   b'Java_com_nakpaaiyaa_rbo_RendererSettings_configurePerformanceNative',
                   b'[Asset cache]', b'[Vulkan perf]', b'[CPU hotspots]',
                   b'[Skill native]', b'[Asset I/O] connected_handlers=']:
        assert symbol in library, symbol
    assert apk.read('assets/licenses/minimp3.txt') == (root/'vendor/minimp3/LICENSE').read_bytes()
print('Final APK: DEX files, cheat menu, performance controls/cache/batching, movie audio and license verified.')
