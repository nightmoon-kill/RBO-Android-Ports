package com.nakpaaiyaa.rbo;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

final class GameControls {
    enum Action {
        UP("Move up", 0xc8, KeyEvent.KEYCODE_DPAD_UP),
        DOWN("Move down", 0xd0, KeyEvent.KEYCODE_DPAD_DOWN),
        LEFT("Move left", 0xcb, KeyEvent.KEYCODE_DPAD_LEFT),
        RIGHT("Move right", 0xcd, KeyEvent.KEYCODE_DPAD_RIGHT),
        WEAK("Weak attack", 0x1e, KeyEvent.KEYCODE_BUTTON_A),
        STRONG("Strong attack", 0x1f, KeyEvent.KEYCODE_BUTTON_B),
        GUARD("Guard", 0x20, KeyEvent.KEYCODE_BUTTON_X),
        SPECIAL("Special", 0x21, KeyEvent.KEYCODE_BUTTON_Y),
        PAUSE("Pause", 0x02, KeyEvent.KEYCODE_BUTTON_START);

        final String label;
        final int defaultScan, defaultButton;

        Action(String label, int defaultScan, int defaultButton) {
            this.label = label;
            this.defaultScan = defaultScan;
            this.defaultButton = defaultButton;
        }
    }

    static final class Choice {
        final String label;
        final int scan, keyCode;

        Choice(String label, int scan, int keyCode) {
            this.label = label;
            this.scan = scan;
            this.keyCode = keyCode;
        }
    }

    static final Action[] ACTIONS = Action.values();
    static final Choice[] KEYS = keyboardChoices();
    static final Choice[] BUTTONS = {
        button("D-pad up", KeyEvent.KEYCODE_DPAD_UP),
        button("D-pad down", KeyEvent.KEYCODE_DPAD_DOWN),
        button("D-pad left", KeyEvent.KEYCODE_DPAD_LEFT),
        button("D-pad right", KeyEvent.KEYCODE_DPAD_RIGHT),
        button("A", KeyEvent.KEYCODE_BUTTON_A), button("B", KeyEvent.KEYCODE_BUTTON_B),
        button("X", KeyEvent.KEYCODE_BUTTON_X), button("Y", KeyEvent.KEYCODE_BUTTON_Y),
        button("L1", KeyEvent.KEYCODE_BUTTON_L1), button("R1", KeyEvent.KEYCODE_BUTTON_R1),
        button("L2", KeyEvent.KEYCODE_BUTTON_L2), button("R2", KeyEvent.KEYCODE_BUTTON_R2),
        button("L3", KeyEvent.KEYCODE_BUTTON_THUMBL), button("R3", KeyEvent.KEYCODE_BUTTON_THUMBR),
        button("Start", KeyEvent.KEYCODE_BUTTON_START), button("Select", KeyEvent.KEYCODE_BUTTON_SELECT)
    };

    private static final int FORMAT_VERSION = 1;
    private final int[] scans = new int[ACTIONS.length];
    private final int[] buttons = new int[ACTIONS.length];

    GameControls() {
        for (Action action : ACTIONS) {
            scans[action.ordinal()] = action.defaultScan;
            buttons[action.ordinal()] = action.defaultButton;
        }
    }

    int scan(Action action) { return scans[action.ordinal()]; }

    int controllerScan(int keyCode) {
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i] == keyCode) return scans[i];
        }
        return 0;
    }

    int keyboardScan(int keyCode) {
        for (Choice choice : KEYS) {
            if (choice.keyCode == keyCode && contains(scans, choice.scan)) return choice.scan;
        }
        return 0;
    }

    int selected(Action action, boolean controller) {
        return (controller ? buttons : scans)[action.ordinal()];
    }

    void assign(Action action, boolean controller, int value) {
        int[] bindings = controller ? buttons : scans;
        int target = action.ordinal();
        for (int i = 0; i < bindings.length; i++) {
            if (i != target && bindings[i] == value) bindings[i] = bindings[target];
        }
        bindings[target] = value;
    }

    static Choice[] choices(boolean controller) { return controller ? BUTTONS : KEYS; }

    static int value(Choice choice, boolean controller) {
        return controller ? choice.keyCode : choice.scan;
    }

    static int choiceIndex(boolean controller, int value) {
        Choice[] items = choices(controller);
        for (int i = 0; i < items.length; i++) {
            if (value(items[i], controller) == value) return i;
        }
        return -1;
    }

    static boolean isControllerKey(KeyEvent event) {
        int key = event.getKeyCode();
        if (KeyEvent.isGamepadButton(key)) return true;
        // A dedicated keyboard's arrow keys remain usable as keyboard bindings.
        if (key < KeyEvent.KEYCODE_DPAD_UP || key > KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        InputDevice device = event.getDevice();
        return event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_DPAD) || isController(device);
    }

    static boolean isController(InputDevice device) {
        return device != null && (device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
            device.supportsSource(InputDevice.SOURCE_JOYSTICK));
    }

    static boolean isControllerMotion(MotionEvent event) {
        return event.getActionMasked() == MotionEvent.ACTION_MOVE &&
            (event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.isFromSource(InputDevice.SOURCE_GAMEPAD));
    }

    static boolean triggerPressed(MotionEvent event, boolean left) {
        int trigger = left ? MotionEvent.AXIS_LTRIGGER : MotionEvent.AXIS_RTRIGGER;
        int pedal = left ? MotionEvent.AXIS_BRAKE : MotionEvent.AXIS_GAS;
        return Math.max(event.getAxisValue(trigger), event.getAxisValue(pedal)) > .5f;
    }

    static GameControls load(File files) {
        GameControls restored = new GameControls();
        Path path = settingsFile(files);
        try {
            if (Files.size(path) != byteCount()) return restored;
            ByteBuffer data = ByteBuffer.wrap(Files.readAllBytes(path));
            if (data.remaining() != byteCount() || data.getInt() != FORMAT_VERSION) return restored;
            for (int i = 0; i < ACTIONS.length; i++) {
                int scan = data.getInt(), button = data.getInt();
                if (choiceIndex(false, scan) < 0 || choiceIndex(true, button) < 0) return new GameControls();
                for (int j = 0; j < i; j++) {
                    if (restored.scans[j] == scan || restored.buttons[j] == button) return new GameControls();
                }
                restored.scans[i] = scan;
                restored.buttons[i] = button;
            }
        } catch (IOException unavailable) {
            return new GameControls();
        }
        return restored;
    }

    void save(File files) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(byteCount()).putInt(FORMAT_VERSION);
        for (int i = 0; i < ACTIONS.length; i++) data.putInt(scans[i]).putInt(buttons[i]);
        GameFiles.writeAtomically(settingsFile(files), data.array());
    }

    private static int byteCount() { return Integer.BYTES * (1 + 2 * ACTIONS.length); }

    private static Path settingsFile(File files) { return new File(files, "player-one-controls").toPath(); }

    private static boolean contains(int[] values, int requested) {
        for (int value : values) if (value == requested) return true;
        return false;
    }

    private static Choice button(String label, int code) { return new Choice(label, 0, code); }

    private static Choice[] keyboardChoices() {
        Choice[] keys = new Choice[40];
        int cursor = 0;
        for (Action action : new Action[]{Action.UP, Action.DOWN, Action.LEFT, Action.RIGHT}) {
            keys[cursor++] = new Choice(action.label.substring(5), action.defaultScan, action.defaultButton);
        }
        int[] letterScans = {
            0x1e, 0x30, 0x2e, 0x20, 0x12, 0x21, 0x22, 0x23, 0x17, 0x24, 0x25, 0x26, 0x32,
            0x31, 0x18, 0x19, 0x10, 0x13, 0x1f, 0x14, 0x16, 0x2f, 0x11, 0x2d, 0x15, 0x2c
        };
        for (int i = 0; i < letterScans.length; i++) {
            keys[cursor++] = new Choice(String.valueOf((char) ('A' + i)), letterScans[i], KeyEvent.KEYCODE_A + i);
        }
        for (int i = 0; i <= 9; i++) {
            keys[cursor++] = new Choice(Integer.toString(i), i == 0 ? 0x0b : i + 1, KeyEvent.KEYCODE_0 + i);
        }
        return keys;
    }
}
