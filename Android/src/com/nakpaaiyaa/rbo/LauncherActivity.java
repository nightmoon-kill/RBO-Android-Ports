package com.nakpaaiyaa.rbo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class LauncherActivity extends Activity {
    private static final int PICK_GAME = 1;
    private Button launch, settings, importButton, shareLog, testSound;
    private TextView status;
    private boolean importing, launchPending, testingSound;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        GameFiles.recover(getFilesDir());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int inset = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(inset, inset, inset, inset);
        scroll.addView(box, new ScrollView.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("Ragnarok Battle Offline");
        title.setTextSize(28);
        box.addView(title);
        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, inset / 2, 0, inset / 2);
        box.addView(status);

        launch = new Button(this);
        launch.setText("Play Expansion 3");
        launch.setOnClickListener(view -> {
            if (importing || launchPending) return;
            launchPending = true;
            launch.setEnabled(false);
            startActivity(new Intent(this, GameActivity.class));
        });
        box.addView(launch);
        settings = new Button(this);
        settings.setText("Control settings");
        settings.setOnClickListener(view -> startActivity(new Intent(this, ControlsActivity.class)));
        box.addView(settings);
        importButton = new Button(this);
        importButton.setOnClickListener(view -> {
            if (GameFiles.ready(GameFiles.root(getFilesDir()))) {
                new AlertDialog.Builder(this)
                    .setMessage("Import a different copy? The current game and its saves will be kept as the previous import.")
                    .setPositiveButton("Choose folder", (dialog, which) -> chooseFolder())
                    .setNegativeButton(android.R.string.cancel, null).show();
            } else chooseFolder();
        });
        box.addView(importButton);

        testSound = new Button(this);
        testSound.setText("Test audio");
        testSound.setOnClickListener(view -> {
            testingSound = true;
            refresh();
            GameAudio.playTest(new Handler(Looper.getMainLooper()), () -> {
                testingSound = false;
                if (!isDestroyed()) refresh();
            });
        });
        box.addView(testSound);

        shareLog = new Button(this);
        shareLog.setText("Share last game log");
        shareLog.setOnClickListener(view -> shareLog());
        box.addView(shareLog);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        launchPending = false;
        refresh();
    }

    private File log() { return new File(getFilesDir(), "rbo_log.txt"); }

    private void refresh() {
        boolean ready = GameFiles.ready(GameFiles.root(getFilesDir()));
        launch.setEnabled(ready && !importing && !launchPending && !testingSound);
        settings.setEnabled(!importing && !launchPending && !testingSound);
        importButton.setEnabled(!importing);
        shareLog.setEnabled(!importing && log().length() > 0);
        testSound.setEnabled(!importing && !launchPending && !testingSound);
        importButton.setText(ready ? "Import another copy" : "Import game folder");
        if (importing) {
            status.setText("Copying game files… Keep this screen open.");
        } else if (ready) {
            status.setText("Game ready.");
        } else {
            status.setText("Select the folder containing RBO_Ex3.exe and DATA.");
        }
    }

    private void chooseFolder() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(picker, PICK_GAME);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_GAME || result != RESULT_OK || data == null || data.getData() == null) return;
        importing = true;
        refresh();
        new Thread(() -> {
            String error = null;
            try {
                GameFiles.importFolder(getContentResolver(), data.getData(), getFilesDir());
            } catch (IOException | RuntimeException failure) {
                error = failure.getMessage();
                if (error == null) error = "The game folder could not be imported.";
            }
            final String problem = error;
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                importing = false;
                refresh();
                if (problem != null) {
                    new AlertDialog.Builder(this).setTitle("Import failed").setMessage(problem)
                        .setPositiveButton(android.R.string.ok, null).show();
                }
            });
        }, "rbo-import").start();
    }

    @Override public void onBackPressed() {
        if (!importing) super.onBackPressed();
    }

    private void shareLog() {
        try (FileInputStream input = new FileInputStream(log());
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.getChannel().position(Math.max(0, input.getChannel().size() - 24000));
            byte[] buffer = new byte[4096];
            int count;
            while (output.size() < 24000 &&
                (count = input.read(buffer, 0, Math.min(buffer.length, 24000 - output.size()))) > 0) {
                output.write(buffer, 0, count);
            }
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, new String(output.toByteArray(), StandardCharsets.UTF_8));
            startActivity(Intent.createChooser(send, "Share game log"));
        } catch (IOException problem) {
            status.setText("The last game log could not be read.");
        }
    }
}
