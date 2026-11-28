from hashlib import sha256
from pathlib import Path
from struct import pack_into, unpack_from
from zipfile import ZIP_STORED, ZipFile

root = Path(__file__).resolve().parent
original = root.parent.parent / "downloads" / "RBO-Android-0.5.2-ARM64.apk"
build = root / "build"
expected = "2c08c144ce4b69011630d9224df516d18c599b8ae03518015c00ab8f17df990b"
if sha256(original.read_bytes()).hexdigest() != expected:
    raise SystemExit("Expected the exact RBO Android 0.5.2 release APK")


def replace_once(text, before, after):
    if text.count(before) != 1:
        raise ValueError(f"Expected one occurrence of {before!r}")
    return text.replace(before, after, 1)


def patch_smali():
    smali = root / "decoded/smali/com/nakpaaiyaa/rbo"
    native = smali / "NativeCore.smali"
    code = replace_once(native.read_text(), '"rbo_android"', '"rbo_vulkan"')
    native.write_text(code)

    launcher = smali / "LauncherActivity.smali"
    code = launcher.read_text()
    marker = "# instance fields"
    code = replace_once(code, marker, marker + """
.field private menus:Lcom/nakpaaiyaa/rbo/LauncherMenus;
""")
    anchor = """    const-class v0, Lcom/nakpaaiyaa/rbo/GameActivity;

    invoke-direct {p1, p0, v0}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V"""
    code = replace_once(code, anchor, anchor + """

    invoke-static {p0, p1}, Lcom/nakpaaiyaa/rbo/RendererSettings;->putSelection(Landroid/content/Context;Landroid/content/Intent;)V""")
    anchor = """    .line 164
    invoke-virtual {v1, p1}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V"""
    code = replace_once(code, anchor, anchor + """

    move-object v0, p0
    iget-object v2, p0, Lcom/nakpaaiyaa/rbo/LauncherActivity;->launch:Landroid/widget/Button;
    iget-object v3, p0, Lcom/nakpaaiyaa/rbo/LauncherActivity;->settings:Landroid/widget/Button;
    iget-object v4, p0, Lcom/nakpaaiyaa/rbo/LauncherActivity;->importButton:Landroid/widget/Button;
    iget-object v5, p0, Lcom/nakpaaiyaa/rbo/LauncherActivity;->installThai:Landroid/widget/Button;
    iget-object v6, p0, Lcom/nakpaaiyaa/rbo/LauncherActivity;->removeThai:Landroid/widget/Button;
    iget-object v7, p0, Lcom/nakpaaiyaa/rbo/LauncherActivity;->widescreen:Landroid/widget/Button;
    move-object v8, p1
    invoke-static/range {v0 .. v8}, Lcom/nakpaaiyaa/rbo/LauncherMenus;->install(Landroid/app/Activity;Landroid/widget/LinearLayout;Landroid/widget/Button;Landroid/widget/Button;Landroid/widget/Button;Landroid/widget/Button;Landroid/widget/Button;Landroid/widget/Button;Landroid/view/View;)Lcom/nakpaaiyaa/rbo/LauncherMenus;
    move-result-object v0
    iput-object v0, p0, Lcom/nakpaaiyaa/rbo/LauncherActivity;->menus:Lcom/nakpaaiyaa/rbo/LauncherMenus;""")
    anchor = "    invoke-super {p0, p1, p2, p3}, Landroid/app/Activity;->onActivityResult(IILandroid/content/Intent;)V"
    code = replace_once(code, anchor, anchor + """

    iget-object v0, p0, Lcom/nakpaaiyaa/rbo/LauncherActivity;->menus:Lcom/nakpaaiyaa/rbo/LauncherMenus;
    invoke-virtual {v0, p1, p2, p3}, Lcom/nakpaaiyaa/rbo/LauncherMenus;->onActivityResult(IILandroid/content/Intent;)Z
    move-result v0
    if-nez v0, :cond_1""")
    anchor = "    invoke-super {p0}, Landroid/app/Activity;->onBackPressed()V"
    code = replace_once(code, anchor, """    iget-object v0, p0, Lcom/nakpaaiyaa/rbo/LauncherActivity;->menus:Lcom/nakpaaiyaa/rbo/LauncherMenus;
    invoke-virtual {v0}, Lcom/nakpaaiyaa/rbo/LauncherMenus;->goBack()Z
    move-result v0
    if-nez v0, :cond_0

""" + anchor)
    for old, label in (
        ("ติดตั้งแพตช์ไทย v1.2 แล้ว", "Thai Language Installed"),
        ("ติดตั้งแพตช์ภาษาไทย v1.2", "Install Thai Language"),
        ("ลบแพตช์ Widescreen", "Remove Widescreen"),
        ("ติดตั้งแพตช์ Widescreen · 1280×720", r"Install Widescreen \u00b7 1280\u00d7720"),
    ):
        original_label = old.encode("unicode_escape").decode("ascii").replace(r"\x", r"\u00")
        anchor = f'const-string v8, "{original_label}"'
        code = replace_once(code, anchor, f'const-string v8, "{label}"')
    launcher.write_text(code)
    game = smali / "GameActivity.smali"
    anchor = ".method private startGame()V\n    .locals 5"
    game_code = replace_once(game.read_text(), anchor, anchor + """

    invoke-static {p0}, Lcom/nakpaaiyaa/rbo/RendererSettings;->configureForGame(Landroid/app/Activity;)V""")
    anchor = "    invoke-super {p0}, Landroid/app/Activity;->onResume()V"
    game_code = replace_once(game_code, anchor, anchor + """

    invoke-static {p0}, Lcom/nakpaaiyaa/rbo/MoveListDisplay;->attach(Landroid/app/Activity;)V""")
    anchor = "    invoke-super {p0}, Landroid/app/Activity;->onPause()V"
    game_code = replace_once(game_code, anchor, """    invoke-static {p0}, Lcom/nakpaaiyaa/rbo/MoveListDisplay;->detach(Landroid/app/Activity;)V

""" + anchor)
    game_code = replace_once(game_code,
        '.source "GameActivity.java"',
        '.source "GameActivity.java"\n\n.implements Lcom/nakpaaiyaa/rbo/MoveListDisplay$GameHost;')
    game_code = replace_once(game_code, "# instance fields", """# instance fields
.field private movesOpen:Z
""")

    anchor = "    invoke-static {p0, p1, v0}, Lcom/nakpaaiyaa/rbo/CheatMenu;->attach(Landroid/app/Activity;Landroid/widget/FrameLayout;Landroid/view/View;)V"
    game_code = replace_once(game_code, anchor, anchor + """

    iget-object v1, p0, Lcom/nakpaaiyaa/rbo/GameActivity;->display:Landroid/view/SurfaceView;
    invoke-static {p0, p1, v0, v1}, Lcom/nakpaaiyaa/rbo/MoveListDisplay;->install(Landroid/app/Activity;Landroid/widget/FrameLayout;Landroid/widget/Button;Landroid/view/SurfaceView;)V""")
    anchor = "    invoke-super {p0}, Landroid/app/Activity;->onDestroy()V"
    game_code = replace_once(game_code, anchor, """    invoke-static {p0}, Lcom/nakpaaiyaa/rbo/MoveListDisplay;->release(Landroid/app/Activity;)V

""" + anchor)
    anchor = ".method public onBackPressed()V\n    .locals 3"
    game_code = replace_once(game_code, anchor, anchor + """

    invoke-static {p0}, Lcom/nakpaaiyaa/rbo/MoveListDisplay;->closeInline(Landroid/app/Activity;)Z
    move-result v0
    if-eqz v0, :moves_not_open
    return-void
    :moves_not_open""")
    anchor = """    :cond_3
    xor-int/lit8 v1, v0, 0x1

    invoke-static {v1}, Lcom/nakpaaiyaa/rbo/NativeCore;->setPaused(Z)V"""
    game_code = replace_once(game_code, anchor, """    :cond_3
    iget-boolean v1, p0, Lcom/nakpaaiyaa/rbo/GameActivity;->movesOpen:Z
    if-eqz v1, :apply_pause
    const/4 v0, 0x0
    :apply_pause
    xor-int/lit8 v1, v0, 0x1

    invoke-static {v1}, Lcom/nakpaaiyaa/rbo/NativeCore;->setPaused(Z)V""")
    for signature, super_label in (
        (".method public dispatchKeyEvent(Landroid/view/KeyEvent;)Z\n    .locals 6", "cond_4"),
        (".method public onGenericMotionEvent(Landroid/view/MotionEvent;)Z\n    .locals 9", "cond_0"),
    ):
        game_code = replace_once(game_code, signature, signature + f"""

    iget-boolean v0, p0, Lcom/nakpaaiyaa/rbo/GameActivity;->movesOpen:Z
    if-nez v0, :{super_label}""")

    game_code += """

.method public setMoveListOpen(Z)Z
    .locals 1

    if-eqz p1, :apply
    iget-boolean v0, p0, Lcom/nakpaaiyaa/rbo/GameActivity;->closing:Z
    if-nez v0, :unavailable
    iget-boolean v0, p0, Lcom/nakpaaiyaa/rbo/GameActivity;->started:Z
    if-eqz v0, :unavailable
    iget-boolean v0, p0, Lcom/nakpaaiyaa/rbo/GameActivity;->resumed:Z
    if-eqz v0, :unavailable
    iget-boolean v0, p0, Lcom/nakpaaiyaa/rbo/GameActivity;->hasSurface:Z
    if-eqz v0, :unavailable

    iget-object v0, p0, Lcom/nakpaaiyaa/rbo/GameActivity;->touchDirections:Lcom/nakpaaiyaa/rbo/TouchDirectionPad;
    invoke-virtual {v0}, Lcom/nakpaaiyaa/rbo/TouchDirectionPad;->reset()V
    invoke-direct {p0}, Lcom/nakpaaiyaa/rbo/GameActivity;->clearAllKeys()V

    :apply
    iput-boolean p1, p0, Lcom/nakpaaiyaa/rbo/GameActivity;->movesOpen:Z
    invoke-direct {p0}, Lcom/nakpaaiyaa/rbo/GameActivity;->syncGameState()V
    const/4 v0, 0x1
    return v0

    :unavailable
    const/4 v0, 0x0
    return v0
.end method
"""
    # LAN setup runs off the UI thread; re-enter startGame once both are ready.
    entry = ".method private startGame()V\n    .locals 5"
    gate = """

    invoke-static {p0}, Lcom/nakpaaiyaa/rbo/LanGame;->awaitReady(Landroid/app/Activity;)Z
    move-result v0
    if-eqz v0, :lan_ready
    return-void
    :lan_ready"""
    game_code = replace_once(game_code, entry, entry + gate)
    game_code = game_code.replace("Lcom/nakpaaiyaa/rbo/NativeCore;->setKey(IZ)V", "Lcom/nakpaaiyaa/rbo/LanGame;->key(IZ)V")
    game_code = game_code.replace("Lcom/nakpaaiyaa/rbo/NativeCore;->start(Ljava/lang/String;Ljava/lang/String;Z)I", "Lcom/nakpaaiyaa/rbo/LanGame;->start(Ljava/lang/String;Ljava/lang/String;Z)I")
    pause = "    invoke-super {p0}, Landroid/app/Activity;->onPause()V"
    game_code = replace_once(game_code, pause, "    invoke-static {p0}, Lcom/nakpaaiyaa/rbo/LanGame;->background(Landroid/app/Activity;)V\n\n" + pause)
    destroy = "    invoke-super {p0}, Landroid/app/Activity;->onDestroy()V"
    game_code = replace_once(game_code, destroy, "    invoke-static {}, Lcom/nakpaaiyaa/rbo/LanGame;->destroy()V\n\n" + destroy)
    game.write_text(game_code)
    monitor=smali / "GameActivity$1.smali"
    monitor_code=monitor.read_text()
    # Keep a LAN disconnect dialog visible until acknowledged.
    anchor="    invoke-static {}, Lcom/nakpaaiyaa/rbo/NativeCore;->exitCode()I"
    monitor_code=replace_once(monitor_code,anchor,"""    invoke-static {}, Lcom/nakpaaiyaa/rbo/LanGame;->keepErrorVisible()Z
    move-result v0
    if-eqz v0, :lan_check_exit
    return-void
    :lan_check_exit
"""+anchor)
    monitor.write_text(monitor_code)

    # Retain the original HP/SP implementation behind the expanded cheat menu.
    cheat_dir = root / "decoded/smali_classes2/com/nakpaaiyaa/rbo"
    cheat = cheat_dir / "CheatMenu.smali"
    cheat_code = cheat.read_text()
    cheat_code = replace_once(cheat_code, ".method private static show(Landroid/app/Activity;)V",
                             ".method static show(Landroid/app/Activity;)V")
    old = """.method static synthetic lambda$0(Landroid/app/Activity;Landroid/view/View;)V
    .locals 0

    .line 40
    invoke-static {p0}, Lcom/nakpaaiyaa/rbo/CheatMenu;->show(Landroid/app/Activity;)V"""
    new = """.method static synthetic lambda$0(Landroid/app/Activity;Landroid/view/View;)V
    .locals 1
    new-instance v0, Lcom/nakpaaiyaa/rbo/CheatVitalsAction;
    invoke-direct {v0, p0}, Lcom/nakpaaiyaa/rbo/CheatVitalsAction;-><init>(Landroid/app/Activity;)V
    invoke-static {p0, v0}, Lcom/nakpaaiyaa/rbo/PointCheats;->show(Landroid/app/Activity;Ljava/lang/Runnable;)V"""
    cheat.write_text(replace_once(cheat_code, old, new))
    (cheat_dir / "CheatVitalsAction.smali").write_text(""".class final Lcom/nakpaaiyaa/rbo/CheatVitalsAction;
.super Ljava/lang/Object;
.implements Ljava/lang/Runnable;
.field private final activity:Landroid/app/Activity;
.method constructor <init>(Landroid/app/Activity;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Lcom/nakpaaiyaa/rbo/CheatVitalsAction;->activity:Landroid/app/Activity;
    return-void
.end method
.method public run()V
    .locals 1
    iget-object v0, p0, Lcom/nakpaaiyaa/rbo/CheatVitalsAction;->activity:Landroid/app/Activity;
    invoke-static {v0}, Lcom/nakpaaiyaa/rbo/CheatMenu;->show(Landroid/app/Activity;)V
    return-void
.end method
""")
    import runpy
    runpy.run_path(str(root / "generate_language.py"))["translate_smali"]()


def patch_manifest(data):
    for old, new in (("com.nakpaaiyaa.rbo.perftst", "com.nakpaaiyaa.rbo.cmp0600"),
                     ("0.5.2", "0.7.0")):
        old, new = old.encode("utf-16le"), new.encode("utf-16le")
        assert len(old) == len(new)
        data = replace_once(data, old, new)
    manifest = bytearray(data)
    resource_ids = ()
    offset, updated = 8, 0
    while offset < len(manifest):
        kind, header, size = unpack_from("<HHI", manifest, offset)
        assert 8 <= header <= size <= len(manifest) - offset
        if kind == 0x0180:
            resource_ids = unpack_from(f"<{(size - header) // 4}I", manifest, offset + header)
        if kind == 0x0102:
            start, stride, count = unpack_from("<HHH", manifest, offset + 24)
            for index in range(count):
                attr = offset + 16 + start + stride * index
                name, = unpack_from("<I", manifest, attr + 4)
                if name < len(resource_ids) and resource_ids[name] == 0x0101021B:
                    assert unpack_from("<I", manifest, attr + 16) == (17,)
                    pack_into("<I", manifest, attr + 16, 36)
                    updated += 1
        offset += size
    assert offset == len(manifest) and updated == 1
    import runpy
    return runpy.run_path(str(root / "lan_manifest.py"))["add_internet"](manifest)


def extract_libraries():
    folder = build / "original-libs"
    folder.mkdir(parents=True, exist_ok=True)
    with ZipFile(original) as source:
        for library in ("librbo_android.so", "librbo_core044.so"):
            (folder / library).write_bytes(source.read("lib/arm64-v8a/" + library))


def package():
    with (ZipFile(original) as source, ZipFile(build / "modified-dex.apk") as dex,
          ZipFile(build / "unsigned.apk", "w") as output):
        for entry in source.infolist():
            if entry.filename.startswith("META-INF/"):
                continue
            data = source.read(entry)
            if entry.filename == "AndroidManifest.xml":
                data = patch_manifest(data)
            if entry.filename.startswith("classes") and entry.filename.endswith(".dex"):
                data = dex.read(entry.filename)
            entry.extra = b""
            output.writestr(entry, data)
        output.write(build / "settings-dex/classes.dex", "classes3.dex", ZIP_STORED)
        output.write(root / "assets/rbo-moves.tsv", "assets/rbo-moves.tsv")
        for asset in sorted((root / "assets/move-backgrounds").iterdir()):
            if asset.suffix in (".webp", ".txt"):
                output.write(asset, f"assets/move-backgrounds/{asset.name}")
        for library in ("librbo_vulkan.so", "libadrenotools.so", "libhook_impl.so", "libmain_hook.so"):
            output.write(build / library, "lib/arm64-v8a/" + library, ZIP_STORED)
        output.write(root / "vendor/minimp3/LICENSE", "assets/licenses/minimp3.txt")
        output.write(root / "vendor/libadrenotools/LICENSE", "assets/licenses/libadrenotools.txt")
        output.write(root / "vendor/libadrenotools/lib/linkernsbypass/LICENSE",
                     "assets/licenses/liblinkernsbypass.txt")


if __name__ == "__main__":
    import sys

    if sys.argv[1:] == ["extract"]:
        extract_libraries()
    elif sys.argv[1:] == ["smali"]:
        patch_smali()
    elif sys.argv[1:] == ["apk"]:
        package()
    else:
        raise SystemExit("Usage: assemble.py extract|smali|apk")
