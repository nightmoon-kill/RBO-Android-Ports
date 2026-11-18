package com.nakpaaiyaa.rbo;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

import com.nakpaaiyaa.rbo.GameControls.Action;
import com.nakpaaiyaa.rbo.GameControls.Choice;

public final class ControlsActivity extends Activity {
    private final Button[][] bindingButtons = new Button[GameControls.ACTIONS.length][2];
    private GameControls controls;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        controls = GameControls.load(getFilesDir());

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(24), dp(10), dp(24), dp(8));
        setContentView(screen);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        screen.addView(toolbar);
        TextView title = label("Player 1 controls", 24);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button reset = button("Reset defaults");
        reset.setOnClickListener(view -> new AlertDialog.Builder(this)
            .setMessage("Restore the default game keys and controller buttons?")
            .setPositiveButton("Reset", (dialog, which) -> {
                controls = new GameControls();
                persist();
            })
            .setNegativeButton(android.R.string.cancel, null).show());
        toolbar.addView(reset);
        Button done = button("Done");
        done.setOnClickListener(view -> finish());
        toolbar.addView(done);

        TextView hint = label("The left stick follows the D-pad. Selecting a binding already in use swaps the two.", 14);
        hint.setPadding(0, 0, 0, dp(4));
        screen.addView(hint);

        LinearLayout headings = new LinearLayout(this);
        headings.addView(label("Action", 14), column());
        headings.addView(centeredLabel("Game key"), column());
        headings.addView(centeredLabel("Controller"), column());
        screen.addView(headings);

        ScrollView scroll = new ScrollView(this);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        for (Action action : GameControls.ACTIONS) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(label(action.label, 16), column());
            for (int index = 0; index < 2; index++) {
                boolean controller = index == 1;
                Button binding = button("");
                binding.setOnClickListener(view -> {
                    if (controller) capture(action);
                    else choose(action, false);
                });
                row.addView(binding, new LinearLayout.LayoutParams(0, dp(48), 1));
                bindingButtons[action.ordinal()][index] = binding;
            }
            rows.addView(row);
        }
        refresh();
    }

    private LinearLayout.LayoutParams column() { return new LinearLayout.LayoutParams(0, -2, 1); }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        return view;
    }

    private TextView centeredLabel(String text) {
        TextView view = label(text, 14);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        return button;
    }

    private void capture(Action action) {
        AlertDialog dialog = new AlertDialog(this) {
            private int motionButton;

            @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
                if (!GameControls.isControllerMotion(event)) return super.dispatchGenericMotionEvent(event);
                int button = buttonFromMotion(event);
                if (motionButton == 0) motionButton = button;
                else if (button == 0) {
                    dismiss();
                    apply(action, true, motionButton);
                }
                return true;
            }
        };
        dialog.setTitle(action.label);
        dialog.setMessage("Press a controller button or move the D-pad or left stick.");
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Choose from list", (whichDialog, which) -> choose(action, true));
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getText(android.R.string.cancel),
            (whichDialog, which) -> whichDialog.dismiss());
        dialog.setOnKeyListener((whichDialog, code, event) -> {
            if (!GameControls.isControllerKey(event) || GameControls.choiceIndex(true, code) < 0) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) {
                whichDialog.dismiss();
                apply(action, true, code);
            }
            return true;
        });
        dialog.show();
    }

    private static int buttonFromMotion(MotionEvent event) {
        if (GameControls.triggerPressed(event, true)) return KeyEvent.KEYCODE_BUTTON_L2;
        if (GameControls.triggerPressed(event, false)) return KeyEvent.KEYCODE_BUTTON_R2;
        float x = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float y = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (x == 0 && y == 0) {
            x = event.getAxisValue(MotionEvent.AXIS_X);
            y = event.getAxisValue(MotionEvent.AXIS_Y);
        }
        if (Math.abs(x) > Math.abs(y) && Math.abs(x) > .6f) {
            return x < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
        }
        if (Math.abs(y) > .6f) return y < 0 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN;
        return 0;
    }

    private void choose(Action action, boolean controller) {
        Choice[] choices = GameControls.choices(controller);
        String[] names = new String[choices.length];
        for (int i = 0; i < choices.length; i++) names[i] = choices[i].label;
        int selection = GameControls.choiceIndex(controller, controls.selected(action, controller));
        new AlertDialog.Builder(this)
            .setTitle(action.label + (controller ? " controller button" : " game key"))
            .setSingleChoiceItems(names, selection, (dialog, which) -> {
                dialog.dismiss();
                apply(action, controller, GameControls.value(choices[which], controller));
            })
            .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void apply(Action action, boolean controller, int value) {
        controls.assign(action, controller, value);
        persist();
    }

    private void persist() {
        try {
            controls.save(getFilesDir());
        } catch (IOException failure) {
            controls = GameControls.load(getFilesDir());
            Toast.makeText(this, "Could not save the controls.", Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private void refresh() {
        for (Action action : GameControls.ACTIONS) {
            for (int index = 0; index < 2; index++) {
                boolean controller = index == 1;
                int selected = GameControls.choiceIndex(controller, controls.selected(action, controller));
                String binding = GameControls.choices(controller)[selected].label;
                Button view = bindingButtons[action.ordinal()][index];
                view.setText(binding);
                view.setContentDescription(action.label + (controller ? ", controller " : ", game key ") + binding);
            }
        }
    }

    private int dp(int size) { return Math.round(size * getResources().getDisplayMetrics().density); }
}
