package com.nakpaaiyaa.rbo;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.TypedValue;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class MoveListDisplay implements DisplayManager.DisplayListener {
    private static final String[] CLASSES = {
        "Swordman", "Archer", "Magician", "Merchant", "Acolyte", "Thief", "Novice"
    };
    public interface GameHost {
        boolean setMoveListOpen(boolean open);
    }

    private static MoveListDisplay active;
    private final Activity game;
    private final DisplayManager displays;
    private final FrameLayout gameFrame;
    private final Button movesButton;
    private final Button stateButton;
    private final SaveStateMenu saveStates;
    private MovePresentation panel;
    private CompanionPanel inline;
    private boolean listening;

    private MoveListDisplay(Activity game, FrameLayout gameFrame, Button touchButton, Button cheatButton,
            SurfaceView surface) {
        this.game = game;
        this.gameFrame = gameFrame;
        displays = game.getSystemService(DisplayManager.class);

        touchButton.setLayoutParams(HudIcons.position(touchButton, 0));
        cheatButton.setLayoutParams(HudIcons.position(touchButton, 1));
        HudIcons.apply(touchButton, HudIcons.Icon.TOUCH, "Toggle on-screen controls");
        HudIcons.apply(cheatButton, HudIcons.Icon.CHEAT, "Cheat");

        movesButton = new Button(game);
        HudIcons.apply(movesButton, HudIcons.Icon.MOVES, "Open Move List");
        movesButton.setVisibility(View.GONE);
        movesButton.setOnClickListener(view -> showInline());
        gameFrame.addView(movesButton, HudIcons.position(touchButton, 2));

        stateButton = new Button(game);
        HudIcons.apply(stateButton, HudIcons.Icon.STATE, "Save or Load State");
        gameFrame.addView(stateButton, HudIcons.position(touchButton, 3));
        saveStates = new SaveStateMenu(game, stateButton, surface);
        if(game.getIntent().getIntExtra("lan_role",0)!=0){
            cheatButton.setEnabled(false);stateButton.setEnabled(false);movesButton.setEnabled(false);
        }
    }

    public static void install(Activity game, FrameLayout gameFrame, Button touchButton, SurfaceView surface) {
        if (active != null) active.dispose();
        Button cheatButton = (Button) gameFrame.getChildAt(gameFrame.getChildCount() - 1);
        active = new MoveListDisplay(game, gameFrame, touchButton, cheatButton, surface);
        LanGame.badge(game, gameFrame);
    }

    public static void attach(Activity game) {
        if (active == null || active.game != game || active.listening) return;
        active.listening = true;
        active.displays.registerDisplayListener(active, null);
        active.update();
    }

    public static void detach(Activity game) {
        if (active != null && active.game == game) active.suspend();
    }

    public static void release(Activity game) {
        if (active != null && active.game == game) {
            active.dispose();
            active = null;
        }
    }

    public static boolean closeInline(Activity game) {
        return active != null && active.game == game && active.hideInline();
    }

    private void showInline() {
        if(LanGame.active()) return;
        if (inline != null || (panel != null && panel.isShowing())) return;
        CompanionPanel next = new CompanionPanel(game, () -> hideInline());
        if (!((GameHost) game).setMoveListOpen(true)) return;
        inline = next;
        syncMovesButton();
        gameFrame.addView(inline, new FrameLayout.LayoutParams(-1, -1));
    }

    private boolean hideInline() {
        if (inline == null) return false;
        gameFrame.removeView(inline);
        inline = null;
        ((GameHost) game).setMoveListOpen(false);
        syncMovesButton();
        return true;
    }

    private void syncMovesButton() {
        boolean show = listening && inline == null && (panel == null || !panel.isShowing());
        movesButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void suspend() {
        if (listening) {
            listening = false;
            displays.unregisterDisplayListener(this);
        }
        saveStates.close();
        dismiss();
        hideInline();
        syncMovesButton();
    }

    private void dispose() {
        suspend();
        saveStates.release();
        gameFrame.removeView(movesButton);
        gameFrame.removeView(stateButton);
    }

    private void dismiss() {
        if (panel != null) {
            panel.dismiss();
            panel = null;
        }
    }

    @Override public void onDisplayAdded(int id) { update(); }
    @Override public void onDisplayRemoved(int id) { update(); }
    @Override public void onDisplayChanged(int id) { update(); }

    private void update() {
        if (!listening || game.isFinishing() || game.isDestroyed()) {
            dismiss();
            return;
        }
        Display gameDisplay = Build.VERSION.SDK_INT >= 30
                ? game.getDisplay() : game.getWindowManager().getDefaultDisplay();
        int gameDisplayId = gameDisplay == null ? Display.DEFAULT_DISPLAY : gameDisplay.getDisplayId();
        Display target = onlySecondary(
                displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION), gameDisplayId);
        if (target == null) target = onlySecondary(displays.getDisplays(), gameDisplayId);

        if (panel != null && target != null && panel.getDisplay().getDisplayId() == target.getDisplayId()
                && panel.isShowing()) return;
        dismiss();
        if (target != null) {
            try {
                panel = new MovePresentation(game, target);
                panel.show();
                hideInline();
            } catch (WindowManager.InvalidDisplayException | WindowManager.BadTokenException unavailable) {
                dismiss();
            }
        }
        syncMovesButton();
    }

    private static Display onlySecondary(Display[] candidates, int gameDisplayId) {
        Display found = null;
        for (Display display : candidates) {
            int id = display.getDisplayId();
            if (id == Display.DEFAULT_DISPLAY || id == gameDisplayId || !display.isValid() ||
                    display.getState() == Display.STATE_OFF) continue;
            if (found != null) return null;
            found = display;
        }
        return found;
    }

    private static final class Move {
        final String role, variant, name, command, condition;

        Move(String[] fields) {
            role = fields[0];
            variant = fields[1];
            name = fields[2];
            command = fields[3];
            condition = fields.length > 4 ? fields[4] : "";
        }
    }

    private static final class PortraitBackground extends View {
        private final Paint portraitPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint fadePaint = new Paint();
        private final Rect crop = new Rect();
        private final RectF frame = new RectF();
        private final ColorMatrixColorFilter sepia = new ColorMatrixColorFilter(new float[] {
                .28f, .44f, .10f, 0, 8,
                .22f, .35f, .08f, 0, 5,
                .16f, .24f, .06f, 0, 4,
                0, 0, 0, 1, 0
        });
        private Bitmap portrait;
        private boolean fullArtwork;

        PortraitBackground(Context context) {
            super(context);
        }

        void setPortrait(Bitmap image, boolean fullArtwork) {
            portrait = image;
            this.fullArtwork = fullArtwork;
            invalidate();
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            fadePaint.setShader(width > 0 ? new LinearGradient(0, 0, width, 0,
                    new int[] {0x18120C09, 0x5517100C, 0xFF19110D},
                    new float[] {0, .48f, .89f}, Shader.TileMode.CLAMP) : null);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (portrait == null) return;
            float width = getWidth();
            float height = getHeight();
            if (width <= 0 || height <= 0) return;
            int sourceWidth = portrait.getWidth();
            int sourceHeight = portrait.getHeight();
            if (fullArtwork) {
                crop.set(0, 0, sourceWidth, sourceHeight);
                float scale = Math.max(width / sourceWidth, height / sourceHeight);
                float left = (width - sourceWidth * scale) / 2;
                float top = (height - sourceHeight * scale) / 2;
                frame.set(left, top, left + sourceWidth * scale, top + sourceHeight * scale);
            } else {
                // Exclude the frame and the caption in the original class banners.
                crop.set(5, 6, sourceWidth - 5, sourceHeight - 26);
                float scale = height / crop.height();
                frame.set(0, 0, crop.width() * scale, height);
            }
            portraitPaint.setColorFilter(fullArtwork ? null : sepia);
            canvas.drawBitmap(portrait, crop, frame, portraitPaint);
            if (!fullArtwork) canvas.drawRect(0, 0, width, height, fadePaint);
        }
    }

    private static final class MovePresentation extends Presentation {
        MovePresentation(Context context, Display display) {
            super(context, display, android.R.style.Theme_Material_NoActionBar);
        }

        @Override protected void onCreate(Bundle state) {
            super.onCreate(state);
            Window window = getWindow();
            if (window != null) {
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            setContentView(new CompanionPanel(getContext(), null));
            if (window != null) window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    static final class MovePanel extends FrameLayout {
        private final List<Move> moves = new ArrayList<>();
        private final SharedPreferences prefs;
        private int job;
        private boolean female;
        private TextView jobLabel, count;
        private Button variant;
        private PortraitBackground background;
        private String shownPortrait;
        private LinearLayout entries;
        private boolean followLive = true;
        private int liveCode = -1;
        private Button auto;

        MovePanel(Context context, Runnable close) {
            super(context);
            prefs = context.getSharedPreferences("second_screen_moves", Context.MODE_PRIVATE);
            job = Math.max(0, Math.min(CLASSES.length - 1, prefs.getInt("class", 0)));
            female = prefs.getBoolean("female", false);
            setBackgroundColor(Color.rgb(25, 17, 13));
            setClickable(close != null);
            loadMoves();
            background = new PortraitBackground(getContext());
            background.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(background, new FrameLayout.LayoutParams(-1, -1));

            LinearLayout root = new LinearLayout(getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(0x26120C09);
            root.setPadding(dp(10), dp(6), dp(10), dp(6));

            LinearLayout selector = new LinearLayout(getContext());
            selector.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout heading = new LinearLayout(getContext());
            heading.setOrientation(LinearLayout.VERTICAL);
            heading.addView(label("Move List", 18, Color.WHITE, true));
            count = label("", 11, 0xFFE0CBB4, false);
            heading.addView(count);
            selector.addView(heading, new LinearLayout.LayoutParams(0, -2, 1.25f));
            auto = button("Auto", "Follow the active character", () -> {
                followLive = true;
                applyCharacter(liveCode);
                refresh();
            });
            selector.addView(auto, new LinearLayout.LayoutParams(dp(46), -1));
            selector.addView(button("‹", "Previous class", () -> changeJob(-1)), new LinearLayout.LayoutParams(dp(37), -1));
            jobLabel = label("", 15, Color.WHITE, true);
            jobLabel.setGravity(Gravity.CENTER);
            fit(jobLabel, 8, 15);
            selector.addView(jobLabel, new LinearLayout.LayoutParams(0, -1, 1.4f));
            selector.addView(button("›", "Next class", () -> changeJob(1)), new LinearLayout.LayoutParams(dp(37), -1));
            variant = button("", "Switch character variant", () -> {
                followLive = false;
                female = !female;
                refresh();
            });
            selector.addView(variant, new LinearLayout.LayoutParams(0, -1, 1.1f));
            if (close != null) {
                LinearLayout.LayoutParams closeFrame = new LinearLayout.LayoutParams(dp(42), -1);
                closeFrame.leftMargin = dp(4);
                selector.addView(button("✕", "Close Move List", close), closeFrame);
            }
            root.addView(selector, new LinearLayout.LayoutParams(-1, dp(46)));

            entries = new LinearLayout(getContext());
            entries.setOrientation(LinearLayout.VERTICAL);
            root.addView(entries, new LinearLayout.LayoutParams(-1, 0, 1));

            TextView hint = label("A เบา   B หนัก   C ป้องกัน   D พิเศษ   •   ลูกศรอิงตอนหันขวา",
                    11, 0xFFE0CBB4, false);
            hint.setGravity(Gravity.CENTER_VERTICAL);
            fit(hint, 7, 11);
            root.addView(hint, new LinearLayout.LayoutParams(-1, dp(21)));
            addView(root, new FrameLayout.LayoutParams(-1, -1));
            refresh();
        }

        private void changeJob(int step) {
            followLive = false;
            job = (job + step + CLASSES.length) % CLASSES.length;
            refresh();
        }

        void applyCharacter(int code) {
            liveCode = code;
            if (!followLive || code < 0 || code >= 14) return;
            int next = CompanionData.MOVE_JOBS[code / 2];
            boolean nextFemale = (code & 1) != 0;
            if (job != next || female != nextFemale) {
                job = next;
                female = nextFemale;
                refresh();
            }
        }

        private void loadMoves() {
            try (BufferedReader data = new BufferedReader(new InputStreamReader(
                    getContext().getAssets().open("rbo-moves.tsv"), StandardCharsets.UTF_8))) {
                for (String line; (line = data.readLine()) != null; ) {
                    if (line.startsWith("#")) continue;
                    String[] fields = line.split("\\t", -1);
                    if (fields.length >= 4) moves.add(new Move(fields));
                }
            } catch (IOException failure) {
                android.util.Log.w("RBO", "Could not load the Move List", failure);
            }
        }

        private void refresh() {
            auto.setText(AppLanguage.text("Auto") + (followLive ? " ●" : ""));
            auto.setTextColor(followLive ? 0xFF8DE4CF : Color.WHITE);
            prefs.edit().putInt("class", job).putBoolean("female", female).apply();
            String role = CLASSES[job];
            String selected = female ? "F" : "M";
            jobLabel.setText(AppLanguage.text(role));
            variant.setText(AppLanguage.text(female ? "Female" : "Male"));
            variant.setContentDescription(AppLanguage.text(female ? "Female" : "Male") + ". " + AppLanguage.text("Switch character variant"));
            List<Move> selectedMoves = new ArrayList<>();
            for (Move move : moves) {
                if (role.equals(move.role) && ("-".equals(move.variant) || selected.equals(move.variant))) {
                    selectedMoves.add(move);
                }
            }
            count.setText(selectedMoves.size() + " " + AppLanguage.text("moves"));
            updateBackground(role);
            showGrid(selectedMoves);
        }

        private void updateBackground(String role) {
            String portrait = role.toLowerCase(java.util.Locale.ROOT) + (female ? "_female" : "_male");
            if (portrait.equals(shownPortrait)) return;
            shownPortrait = portrait;
            String asset = "move-backgrounds/" + portrait + ".webp";
            try (java.io.InputStream stream = getContext().getAssets().open(asset)) {
                background.setPortrait(BitmapFactory.decodeStream(stream), "magician_male".equals(portrait));
            } catch (IOException failure) {
                background.setPortrait(null, false);
                android.util.Log.w("RBO", "Could not load " + asset, failure);
            }
        }

        private void showGrid(List<Move> selectedMoves) {
            entries.removeAllViews();
            if (selectedMoves.isEmpty()) {
                entries.addView(label("ไม่มีรายการท่าสำหรับตัวละครนี้", 14, Color.WHITE, false));
                return;
            }
            int columns = 3;
            int rows = (selectedMoves.size() + columns - 1) / columns;
            for (int row = 0; row < rows; row++) {
                LinearLayout line = new LinearLayout(getContext());
                entries.addView(line, new LinearLayout.LayoutParams(-1, 0, 1));
                for (int col = 0; col < columns; col++) {
                    int index = col * rows + row;
                    View card = index < selectedMoves.size()
                            ? createCard(selectedMoves.get(index), row) : new View(getContext());
                    LinearLayout.LayoutParams frame = new LinearLayout.LayoutParams(0, -1, 1);
                    frame.setMargins(dp(2), dp(2), dp(2), dp(2));
                    line.addView(card, frame);
                }
            }
        }

        private View createCard(Move move, int row) {
            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(5), dp(2), dp(5), dp(2));
            card.setBackground(tile(row % 2 == 0 ? 0xAB191310 : 0xB316110F, 0x806B5140));
            String command = move.command.replace(" (also airborne)", " · Air OK")
                    .replace(" (airborne)", " · Air").replace("(airborne) ", "Air · ");

            TextView name = label(move.name, 16, Color.WHITE, true);
            name.setText(move.name);
            fit(name, 7, 16);
            card.addView(name, new LinearLayout.LayoutParams(-1, 0, 1));

            TextView input = label(command, 20, 0xFF8DE4EF, true);
            fit(input, 7, 20);
            card.addView(input, new LinearLayout.LayoutParams(-1, 0, 1.22f));

            if (!move.condition.isEmpty()) {
                TextView condition = label(move.condition, 11, 0xFFE0CBB4, false);
                fit(condition, 6, 11);
                card.addView(condition, new LinearLayout.LayoutParams(-1, 0, 0.82f));
            }
            return card;
        }

        private GradientDrawable tile(int fill, int border) {
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(fill);
            shape.setCornerRadius(dp(5));
            shape.setStroke(Math.max(1, dp(1)), border);
            return shape;
        }

        private static void fit(TextView label, int minSp, int maxSp) {
            label.setMaxLines(1);
            label.setAutoSizeTextTypeUniformWithConfiguration(minSp, maxSp, 1, TypedValue.COMPLEX_UNIT_SP);
        }

        private Button button(String text, String description, Runnable action) {
            Button control = new Button(getContext());
            control.setText(AppLanguage.text(text));
            control.setAllCaps(false);
            control.setMinWidth(0);
            control.setMinimumWidth(0);
            control.setPadding(dp(3), 0, dp(3), 0);
            control.setTextColor(Color.WHITE);
            control.setBackground(tile(0xFF342720, 0xFF6A5141));
            fit(control, 9, 14);
            control.setContentDescription(AppLanguage.text(description));
            control.setFocusable(false);
            control.setOnClickListener(view -> action.run());
            return control;
        }

        private TextView label(String text, int size, int color, boolean bold) {
            TextView view = new TextView(getContext());
            view.setText(AppLanguage.text(text));
            view.setTextColor(color);
            view.setTextSize(size);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setIncludeFontPadding(false);
            if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            return view;
        }

        private int dp(int value) {
            return Math.round(value * getContext().getResources().getDisplayMetrics().density);
        }
    }
}
