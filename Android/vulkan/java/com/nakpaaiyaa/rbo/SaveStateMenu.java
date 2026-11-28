package com.nakpaaiyaa.rbo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;

final class SaveStateMenu {
    private static final int SLOT_COUNT = 10;
    private static final int SAVE = 1;
    private static final int LOAD = 2;

    private final Activity game;
    private final Button button;
    private final SurfaceView surface;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::checkResult;
    private AlertDialog dialog;
    private AlertDialog confirmation;
    private int pendingOperation;
    private int pendingSlot;
    private int selected;
    private boolean capturing;
    private File pendingFile;
    private File previousPreview;
    private Bitmap pendingPreview;

    SaveStateMenu(Activity game, Button button, SurfaceView surface) {
        this.game = game;
        this.button = button;
        this.surface = surface;
        button.setOnClickListener(view -> show());
    }

    private void show() {
        if (dialog != null || pendingOperation != 0) return;
        MoveListDisplay.GameHost host = (MoveListDisplay.GameHost) game;
        if (!host.setMoveListOpen(true)) return;

        File directory = game.getDir("save_states", Context.MODE_PRIVATE);
        File[] slots = new File[SLOT_COUNT];
        String[] labels = new String[SLOT_COUNT];
        boolean[] canLoad = new boolean[SLOT_COUNT];
        Bitmap[] previews = new Bitmap[SLOT_COUNT];
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new File(directory, "slot-" + (i + 1) + ".state");
            long time = savedAt(slots[i].getAbsolutePath());
            canLoad[i] = time > 0;
            if (canLoad[i]) {
                File preview = previewFile(slots[i]);
                if (preview.isFile()) previews[i] = BitmapFactory.decodeFile(preview.getAbsolutePath());
            }
            String detail = canLoad[i] ? dateFormat.format(new Date(time * 1000))
                    : slots[i].exists() ? "Unreadable" : "Empty";
            labels[i] = AppLanguage.text("Slot " + (i + 1) + " · " + detail);
        }

        dialog = new AlertDialog.Builder(game)
                .setTitle(AppLanguage.text("Save / Load State"))
                .setSingleChoiceItems(slotRows(labels, previews), selected, (window, slot) -> {
                    selected = slot;
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(canLoad[slot]);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(AppLanguage.text("Save State"), null)
                .setNeutralButton(AppLanguage.text("Load State"), null)
                .create();
        dialog.setOnDismissListener(window -> {
            capturing = false;
            dialog = null;
            host.setMoveListOpen(false);
        });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (capturing) return;
            if (slots[selected].exists()) confirm(SAVE, slots[selected], labels[selected]);
            else captureAndSave(slots[selected], selected);
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
            if (!capturing) confirm(LOAD, slots[selected], labels[selected]);
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(canLoad[selected]);
    }

    private ArrayAdapter<String> slotRows(String[] labels, Bitmap[] previews) {
        return new ArrayAdapter<String>(game, android.R.layout.simple_list_item_single_choice, labels) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                CheckedTextView row = (CheckedTextView) super.getView(position, recycled, parent);
                int width = dp(88);
                int height = dp(50);
                Drawable thumbnail;
                Bitmap preview = previews[position];
                if (preview != null) {
                    BitmapDrawable image = new BitmapDrawable(game.getResources(), preview);
                    // Keep the image within the same footprint for every slot.
                    int fittedWidth = Math.max(1, Math.min(width, height * preview.getWidth() / preview.getHeight()));
                    int fittedHeight = Math.max(1, Math.min(height, width * preview.getHeight() / preview.getWidth()));
                    thumbnail = new InsetDrawable(image,
                            (width - fittedWidth) / 2, (height - fittedHeight) / 2,
                            (width - fittedWidth + 1) / 2, (height - fittedHeight + 1) / 2);
                } else {
                    GradientDrawable empty = new GradientDrawable();
                    empty.setColor(0x22888888);
                    empty.setCornerRadius(dp(4));
                    thumbnail = empty;
                }
                thumbnail.setBounds(0, 0, width, height);
                row.setCompoundDrawablesRelative(thumbnail, null, null, null);
                row.setCompoundDrawablePadding(dp(12));
                row.setMinHeight(height + dp(10));
                return row;
            }
        };
    }

    private int dp(int value) {
        return Math.round(value * game.getResources().getDisplayMetrics().density);
    }

    private void confirm(int operation, File file, String label) {
        int slot = selected;
        boolean saving = operation == SAVE;
        confirmation = new AlertDialog.Builder(game)
                .setTitle(AppLanguage.text(saving ? "Overwrite save state?" : "Load save state?"))
                .setMessage(AppLanguage.text(label + "\n\n" + (saving ? "This replaces the state in this slot."
                        : "Progress since this state will be lost.")))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(AppLanguage.text(saving ? "Overwrite" : "Load State"), (window, which) -> {
                    if (saving) captureAndSave(file, slot);
                    else submit(operation, file, slot, null);
                })
                .create();
        confirmation.setOnDismissListener(window -> confirmation = null);
        confirmation.show();
    }

    private void captureAndSave(File file, int slot) {
        AlertDialog menu = dialog;
        Rect frame = surface.getHolder().getSurfaceFrame();
        if (!surface.getHolder().getSurface().isValid() || frame.isEmpty()) {
            submit(SAVE, file, slot, null);
            return;
        }
        // The game is paused while the dialog is open. Copy only the game surface.
        float scale = Math.min(192f / frame.width(), 108f / frame.height());
        Bitmap image = Bitmap.createBitmap(Math.max(1, Math.round(frame.width() * scale)),
                Math.max(1, Math.round(frame.height() * scale)), Bitmap.Config.ARGB_8888);
        capturing = true;
        try {
            PixelCopy.request(surface, image, status -> {
                if (dialog == menu && menu.isShowing() && capturing) {
                    capturing = false;
                    submit(SAVE, file, slot, status == PixelCopy.SUCCESS ? image : null);
                }
            }, handler);
        } catch (IllegalArgumentException surfaceChanged) {
            capturing = false;
            submit(SAVE, file, slot, null);
        }
    }

    private void submit(int operation, File file, int slot, Bitmap preview) {
        if (dialog == null || !dialog.isShowing()) return;
        File oldPreview = operation == SAVE ? previewFile(file) : null;
        int accepted = request(operation, file.getAbsolutePath());
        dialog.dismiss();
        if (accepted != 0) {
            showMessage(accepted == -2 ? "A save state operation is already running." : "Start the game before using save states.");
            return;
        }
        pendingOperation = operation;
        pendingSlot = slot + 1;
        pendingFile = file;
        previousPreview = oldPreview;
        pendingPreview = preview;
        button.setEnabled(false);
        showMessage((operation == SAVE ? "Saving slot " : "Loading slot ") + pendingSlot + "…");
        handler.postDelayed(poll, 150);
    }

    private void checkResult() {
        if (pendingOperation == 0) return;
        int code = result();
        if (code == 1) {
            handler.postDelayed(poll, 150);
            return;
        }
        if (pendingOperation == SAVE && code == 2) {
            File current = previewFile(pendingFile);
            if (pendingPreview != null) writePreview(current, pendingPreview);
            else current.delete();
            if (!current.equals(previousPreview)) previousPreview.delete();
        }
        showMessage(resultMessage(pendingOperation, code, pendingSlot));
        pendingOperation = 0;
        pendingFile = null;
        previousPreview = null;
        pendingPreview = null;
        button.setEnabled(true);
    }

    private static File previewFile(File state) {
        // Bind the preview to the saved revision so an interrupted overwrite cannot show an old frame.
        return new File(state.getParentFile(), state.getName() + "." + state.lastModified()
                + "." + state.length() + ".png");
    }

    private static void writePreview(File file, Bitmap image) {
        AtomicFile preview = new AtomicFile(file);
        FileOutputStream stream = null;
        try {
            stream = preview.startWrite();
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new IOException("PNG encoding failed");
            preview.finishWrite(stream);
        } catch (IOException error) {
            preview.failWrite(stream);
        }
    }

    private static String resultMessage(int operation, int code, int slot) {
        if (code == 2) return "Saved to slot " + slot + ".";
        if (code == 3) return "Loaded slot " + slot + ".";
        if (operation == SAVE) {
            switch (code) {
                case -2: return "Save states are unavailable during video playback.";
                case -3: return "Could not create the save state file.";
                case -4: return "Could not save. Check available storage.";
                default: return "Could not save slot " + slot + ".";
            }
        }
        switch (code) {
            case -2: return "This slot is empty.";
            case -3:
            case -5:
            case -6: return "This state is incompatible with the current game.";
            case -4: return "This save state file is damaged.";
            case -8: return "Load failed. Restart the game.";
            default: return "Could not load slot " + slot + ".";
        }
    }

    void close() {
        if (confirmation != null) confirmation.dismiss();
        if (dialog != null) dialog.dismiss();
    }

    void release() {
        close();
        handler.removeCallbacks(poll);
    }

    private void showMessage(String message) {
        if (!game.isFinishing() && !game.isDestroyed()) {
            Toast.makeText(game, AppLanguage.text(message), Toast.LENGTH_LONG).show();
        }
    }

    private static native int request(int operation, String file);
    private static native int result();
    private static native long savedAt(String file);
}
