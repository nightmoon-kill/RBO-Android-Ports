package com.nakpaaiyaa.rbo;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One lifecycle-bound reader shared by all three companion pages. */
final class CompanionPanel extends LinearLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MoveListDisplay.MovePanel moves;
    private final CharacterStatusView status;
    private final MonsterView monsters;
    private final Button[] tabs = new Button[3];
    private final TextView connection;
    private final FrameLayout content;
    private CompanionData data = CompanionData.empty();
    private int selectedTab;
    private boolean attached;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!attached) return;
            data = CompanionData.read();
            updateData();
            handler.postDelayed(this, 200);
        }
    };

    CompanionPanel(Context context, Runnable close) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(0xFF13120F);
        setPadding(dp(8), dp(5), dp(8), dp(5));
        LinearLayout navigation = new LinearLayout(context);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < tabs.length; ++i) {
            final int page = i;
            tabs[i] = button(new String[] {"Move List", "Monsters", "Status"}[i], () -> select(page));
            LinearLayout.LayoutParams frame = new LinearLayout.LayoutParams(0, dp(40), 1);
            frame.setMargins(dp(2), 0, dp(2), 0);
            navigation.addView(tabs[i], frame);
        }
        if (close != null) {
            Button exit = button("×", close);
            exit.setContentDescription(AppLanguage.text("Close companion"));
            navigation.addView(exit, new LinearLayout.LayoutParams(dp(44), dp(40)));
        }
        addView(navigation, new LinearLayout.LayoutParams(-1, -2));
        content = new FrameLayout(context);
        LinearLayout.LayoutParams frame = new LinearLayout.LayoutParams(-1, 0, 1);
        frame.topMargin = dp(5);
        addView(content, frame);
        moves = new MoveListDisplay.MovePanel(context, null);
        status = new CharacterStatusView(context);
        monsters = new MonsterView(context);
        content.addView(moves, new FrameLayout.LayoutParams(-1, -1));
        content.addView(monsters, new FrameLayout.LayoutParams(-1, -1));
        content.addView(status, new FrameLayout.LayoutParams(-1, -1));
        connection = new TextView(context);
        connection.setTextSize(10);
        connection.setTextColor(0xFFB3A58C);
        connection.setGravity(Gravity.CENTER_VERTICAL);
        connection.setPadding(dp(6), 0, dp(6), 0);
        addView(connection, new LinearLayout.LayoutParams(-1, dp(20)));
        select(close == null ? 1 : 0);
        updateData();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        handler.post(poll);
    }
    @Override protected void onDetachedFromWindow() {
        attached = false;
        handler.removeCallbacks(poll);
        super.onDetachedFromWindow();
    }
    private void select(int page) {
        selectedTab = page;
        moves.setVisibility(page == 0 ? VISIBLE : GONE);
        monsters.setVisibility(page == 1 ? VISIBLE : GONE);
        status.setVisibility(page == 2 ? VISIBLE : GONE);
        for (int i = 0; i < 3; ++i) {
            tabs[i].setTextColor(i == page ? 0xFFFFE1A0 : 0xFFA99F8D);
            tabs[i].setBackground(tile(i == page ? 0xFF393021 : 0xFF201E19, i == page ? 0xFFB69250 : 0xFF3A352B));
            tabs[i].setSelected(i == page);
        }
        updateData();
    }
    private void updateData() {
        boolean ready = data.playerAvailable();
        moves.applyCharacter(ready ? data.player(1) : -1);
        status.update(data);
        monsters.update(data);
        connection.setText(ready ? "● " + AppLanguage.text("LIVE") + "   ·   " + AppLanguage.text(CompanionData.role(data.player(1)))
                + "   ·   " + AppLanguage.text(selectedTab == 1 ? "Only spawned enemies in this area are counted" : "Game data")
                : AppLanguage.text("Waiting for gameplay · Character data appears after starting a stage"));
        connection.setTextColor(ready ? 0xFF9DC3AD : 0xFFB3A58C);
    }
    private Button button(String title, Runnable action) {
        Button view = new Button(getContext());
        view.setText(AppLanguage.text(title));
        view.setTextSize(12);
        view.setAllCaps(false);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setMinWidth(0); view.setMinimumWidth(0);
        view.setPadding(dp(3), 0, dp(3), 0);
        view.setTextColor(Color.WHITE);
        view.setBackground(tile(0xFF201E19, 0xFF3A352B));
        view.setFocusable(false);
        view.setOnClickListener(v -> action.run());
        return view;
    }
    private GradientDrawable tile(int color, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(7));
        shape.setStroke(dp(1), stroke);
        return shape;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
