package com.nakpaaiyaa.rbo;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.nakpaaiyaa.rbo.GameControls.Action;

public final class GameActivity extends Activity implements SurfaceHolder.Callback, InputManager.InputDeviceListener {
    private static final int TOUCH_DEVICE = Integer.MIN_VALUE;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Long, Integer> keySources = new HashMap<>();
    private final int[] keyCounts = new int[256];

    private SurfaceView display;
    private View touchPad;
    private TextView loading;
    private InputManager inputs;
    private GameControls controls;
    private GameAudio sound;
    private boolean started, resumed, hasSurface, closing;
    private long stopDeadline;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        controls = GameControls.load(getFilesDir());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        hideBars();

        FrameLayout root = new FrameLayout(this) {
            @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
                super.onSizeChanged(w, h, oldW, oldH);
                if (w == 0 || h == 0) return;
                int width = Math.min(w, h * 4 / 3);
                display.setLayoutParams(new FrameLayout.LayoutParams(width, width * 3 / 4, Gravity.CENTER));
            }
        };
        root.setBackgroundColor(Color.BLACK);
        display = new SurfaceView(this);
        display.getHolder().addCallback(this);
        root.addView(display, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        loading = new TextView(this);
        loading.setText("Starting Expansion 3…");
        loading.setPadding(dp(10), dp(6), dp(10), dp(6));
        loading.setBackgroundColor(0xa010161c);
        root.addView(loading, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        touchPad = buildTouchPad();
        root.addView(touchPad, new FrameLayout.LayoutParams(-1, -1));
        Button toggle = new Button(this);
        toggle.setText("Pad");
        toggle.setContentDescription("Toggle on-screen controls");
        toggle.setAlpha(.7f);
        toggle.setOnClickListener(view -> {
            boolean visible = touchPad.getVisibility() == View.VISIBLE;
            if (visible) clearSources(TOUCH_DEVICE);
            touchPad.setVisibility(visible ? View.GONE : View.VISIBLE);
        });
        root.addView(toggle, new FrameLayout.LayoutParams(dp(66), dp(45), Gravity.TOP | Gravity.RIGHT));
        setContentView(root);

        inputs = (InputManager) getSystemService(INPUT_SERVICE);
        inputs.registerInputDeviceListener(this, handler);
        if (hasController()) touchPad.setVisibility(View.GONE);
    }

    private View buildTouchPad() {
        FrameLayout overlay = new FrameLayout(this);
        int size = dp(48);
        TextView directions = new TextView(this);
        directions.setText("▲\n◀     ▶\n▼");
        directions.setGravity(Gravity.CENTER);
        directions.setTextSize(20);
        directions.setBackgroundColor(0x602d3d4b);
        directions.setContentDescription("Directional pad");
        FrameLayout.LayoutParams pad = new FrameLayout.LayoutParams(dp(110), dp(110),
            Gravity.BOTTOM | Gravity.LEFT);
        pad.setMargins(dp(12), 0, 0, dp(12));
        overlay.addView(directions, pad);
        directions.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    float x = (event.getX() / view.getWidth() - .5f) * 2;
                    float y = (event.getY() / view.getHeight() - .5f) * 2;
                    direction(TOUCH_DEVICE, 16, x, y, .22f);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    direction(TOUCH_DEVICE, 16, 0, 0, .22f);
                    break;
                default:
                    break;
            }
            return true;
        });

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(0, 0, dp(12), dp(12));
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(-2, -2,
            Gravity.RIGHT | Gravity.BOTTOM);
        overlay.addView(actions, actionParams);
        addRow(actions, size, new String[]{"X", "Y"}, new Action[]{Action.GUARD, Action.SPECIAL});
        addRow(actions, size, new String[]{"A", "B"}, new Action[]{Action.WEAK, Action.STRONG});

        Button start = makeTouchButton("Start", Action.PAUSE);
        FrameLayout.LayoutParams startParams = new FrameLayout.LayoutParams(dp(72), size,
            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        overlay.addView(start, startParams);
        return overlay;
    }

    private void addRow(LinearLayout box, int size, String[] names, Action[] actions) {
        LinearLayout row = new LinearLayout(this);
        for (int i = 0; i < names.length; i++) {
            row.addView(makeTouchButton(names[i], actions[i]), new LinearLayout.LayoutParams(size, size));
        }
        box.addView(row);
    }

    private Button makeTouchButton(String label, Action action) {
        Button button = new Button(this);
        button.setText(label);
        button.setContentDescription(action.label);
        button.setPadding(0, 0, 0, 0);
        button.setAlpha(.75f);
        long token = source(TOUCH_DEVICE, 32 + action.ordinal());
        int scan = controls.scan(action);
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    setKey(token, scan, true);
                    view.setPressed(true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    setKey(token, scan, false);
                    view.setPressed(false);
                    break;
                default:
                    break;
            }
            return true;
        });
        return button;
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        hideBars();
        syncGameState();
    }

    @Override protected void onPause() {
        resumed = false;
        clearAllKeys();
        syncGameState();
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) hideBars();
        else clearAllKeys();
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        NativeCore.setSurface(holder.getSurface());
        hasSurface = true;
        syncGameState();
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
        syncGameState();
        NativeCore.setSurface(null);
    }

    private void syncGameState() {
        if (closing) return;
        boolean active = resumed && hasSurface;
        if (!started && active) startGame();
        if (!started) return;
        NativeCore.setPaused(!active);
        sound.setActive(active);
    }

    private void startGame() {
        File game = GameFiles.root(getFilesDir());
        if (GameFiles.ready(game)) {
            try {
                GameFiles.configurePlayerOne(game, controls);
            } catch (IOException failure) {
                android.widget.Toast.makeText(this, "Player 1 setup: " + failure.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
            }
        }
        File log = new File(getFilesDir(), "rbo_log.txt");
        if (!GameFiles.ready(game) || NativeCore.start(game.getAbsolutePath(), log.getAbsolutePath()) != 0) {
            new AlertDialog.Builder(this).setTitle("Could not start the game")
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish()).show();
            closing = true;
            return;
        }
        started = true;
        sound = new GameAudio(this);
        handler.postDelayed(() -> loading.setVisibility(View.GONE), 1300);
        handler.postDelayed(checkGame, 80);
    }

    private final Runnable checkGame = new Runnable() {
        @Override public void run() {
            if (NativeCore.exitCode() != -1 || (closing && SystemClock.uptimeMillis() >= stopDeadline)) {
                finish();
            } else if (!isFinishing()) {
                sound.pollMusic();
                handler.postDelayed(this, 80);
            }
        }
    };

    @Override public void onBackPressed() {
        if (closing) return;
        new AlertDialog.Builder(this).setMessage("Return to the launcher?")
            .setPositiveButton("Exit game", (dialog, which) -> {
                if (!started) { finish(); return; }
                closing = true;
                stopDeadline = SystemClock.uptimeMillis() + 600;
                clearAllKeys();
                NativeCore.stop();
                NativeCore.setPaused(false);
            })
            .setNegativeButton(android.R.string.cancel, null).show();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        inputs.unregisterInputDeviceListener(this);
        if (sound != null) sound.close();
        if (started) NativeCore.stop();
        NativeCore.setSurface(null);
        super.onDestroy();
        // The upstream engine supports one run per process. Only this activity uses :game.
        if (started) android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (started && !closing &&
                (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP)) {
            int key = event.getKeyCode();
            boolean controller = GameControls.isControllerKey(event);
            int scan = controller ? controls.controllerScan(key) : controls.keyboardScan(key);
            if (scan != 0) {
                setKey(source(event.getDeviceId(), key), scan, event.getAction() == KeyEvent.ACTION_DOWN);
                return true;
            }
            if (controller && GameControls.choiceIndex(true, key) >= 0) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if (started && !closing && GameControls.isControllerMotion(event)) {
            int id = event.getDeviceId();
            direction(id, 512, event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), .4f);
            direction(id, 520, event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y), .4f);
            setKey(source(id, 528), controls.controllerScan(KeyEvent.KEYCODE_BUTTON_L2),
                GameControls.triggerPressed(event, true));
            setKey(source(id, 529), controls.controllerScan(KeyEvent.KEYCODE_BUTTON_R2),
                GameControls.triggerPressed(event, false));
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private void direction(int device, int offset, float x, float y, float threshold) {
        setDirection(device, offset, Action.LEFT, x < -threshold);
        setDirection(device, offset + 1, Action.RIGHT, x > threshold);
        setDirection(device, offset + 2, Action.UP, y < -threshold);
        setDirection(device, offset + 3, Action.DOWN, y > threshold);
    }

    private void setDirection(int device, int token, Action direction, boolean pressed) {
        int scan = device == TOUCH_DEVICE ? controls.scan(direction) : controls.controllerScan(direction.defaultButton);
        setKey(source(device, token), scan, pressed);
    }

    private static long source(int device, int key) { return ((long) device << 32) | (key & 0xffffffffL); }

    private void setKey(long source, int scan, boolean pressed) {
        if (!started) return;
        if (pressed) {
            if (scan == 0 || keySources.containsKey(source)) return;
            keySources.put(source, scan);
            if (++keyCounts[scan] == 1) NativeCore.setKey(scan, true);
        } else {
            Integer released = keySources.remove(source);
            if (released != null && --keyCounts[released] == 0) NativeCore.setKey(released, false);
        }
    }

    private void clearSources(int device) {
        keySources.entrySet().removeIf(entry -> {
            if ((int) (entry.getKey() >> 32) != device) return false;
            int scan = entry.getValue();
            if (--keyCounts[scan] == 0) NativeCore.setKey(scan, false);
            return true;
        });
    }

    private void clearAllKeys() {
        keySources.clear();
        for (int i = 0; i < keyCounts.length; i++) {
            if (keyCounts[i] != 0) NativeCore.setKey(i, false);
            keyCounts[i] = 0;
        }
    }

    private boolean hasController() {
        for (int id : inputs.getInputDeviceIds()) {
            InputDevice device = inputs.getInputDevice(id);
            if (GameControls.isController(device)) return true;
        }
        return false;
    }

    @Override public void onInputDeviceAdded(int id) {}
    @Override public void onInputDeviceChanged(int id) { clearSources(id); }
    @Override public void onInputDeviceRemoved(int id) {
        clearSources(id);
        if (!hasController()) touchPad.setVisibility(View.VISIBLE);
    }

    private int dp(int dp) { return Math.round(dp * getResources().getDisplayMetrics().density); }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
}
