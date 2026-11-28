package com.nakpaaiyaa.rbo;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;

/** Points are available before the character's in-stage actor is created. */
public final class PointCheats {
    private PointCheats() {}
    public static void show(Activity activity, Runnable vitals) {
        new AlertDialog.Builder(activity)
                .setTitle(AppLanguage.text("Cheats"))
                .setItems(AppLanguage.strings(new String[]{"HP / SP", "Stat Point / Skill Point"}),
                        (dialog, which) -> { if (which == 0) vitals.run(); else points(activity); })
                .setPositiveButton(AppLanguage.text("Done"), null).show();
    }
    private static void points(Activity activity) {
        int flags;
        try { flags = flagsNative(); }
        catch (UnsatisfiedLinkError unavailable) { flags = -1; }
        if (flags < 0) {
            Toast.makeText(activity, AppLanguage.text("Cheats are unavailable for this EX3 executable."), Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(AppLanguage.text("Stat Point / Skill Point"))
                .setMultiChoiceItems(AppLanguage.strings(new String[]{"Stat Point · Keep at 999", "Skill Point · Keep at 999"}),
                        new boolean[]{(flags & 1) != 0, (flags & 2) != 0},
                        (dialog, which, checked) -> enableNative(which, checked))
                .setPositiveButton(AppLanguage.text("Done"), null).show();
    }
    private static native int flagsNative();
    private static native void enableNative(int which, boolean enabled);
}
