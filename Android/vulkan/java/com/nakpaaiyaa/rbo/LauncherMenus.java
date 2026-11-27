package com.nakpaaiyaa.rbo;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class LauncherMenus {
    private final Activity activity;
    private final LinearLayout root, main, options, patch, debugging;
    private final RendererSettings renderer;
    private LinearLayout current;

    private LauncherMenus(Activity activity, LinearLayout root) {
        this.activity = activity;
        this.root = root;
        main = panel();
        options = panel();
        patch = panel();
        debugging = panel();
        renderer = new RendererSettings(activity, options);
    }

    public static LauncherMenus install(Activity activity, LinearLayout root, Button play,
            Button controls, Button importGame, Button installThai, Button removeThai,
            Button widescreen, View debugRow) {
        LauncherMenus menus = new LauncherMenus(activity, root);
        TextView version = new TextView(activity);
        version.setTextSize(13);
        try {
            String name = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
            version.setText("Version " + name);
        } catch (PackageManager.NameNotFoundException ignored) {
            version.setText("Version 0.5.9");
        }
        root.addView(version, root.indexOfChild(play));

        play.setText("PLAY");
        move(root, menus.main, play);
        menus.addLink("OPTIONS", menus.options);
        menus.addLink("PATCH", menus.patch);
        menus.addLink("DEBUGGING", menus.debugging);

        controls.setText("CONTROL SETTINGS");
        move(root, menus.options, controls, 1);
        move(root, menus.options, importGame);

        installThai.setText("Install Thai Language");
        ViewParent creditRow = installThai.getParent().getParent();
        if (creditRow instanceof View && creditRow.getParent() == root) {
            move(root, menus.patch, (View) creditRow);
        }
        removeThai.setText("Remove Thai Language");
        move(root, menus.patch, removeThai);
        move(root, menus.patch, widescreen);
        move(root, menus.debugging, debugRow);

        menus.show(menus.main);
        return menus;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        root.addView(panel);
        return panel;
    }

    private void addLink(String title, LinearLayout target) {
        Button link = new Button(activity);
        link.setText(title);
        link.setOnClickListener(view -> show(target));
        main.addView(link);
        Button back = new Button(activity);
        back.setText("‹ " + title);
        back.setContentDescription("Back to main menu");
        back.setOnClickListener(view -> show(main));
        target.addView(back, 0);
    }

    private void show(LinearLayout selected) {
        current = selected;
        main.setVisibility(selected == main ? View.VISIBLE : View.GONE);
        options.setVisibility(selected == options ? View.VISIBLE : View.GONE);
        patch.setVisibility(selected == patch ? View.VISIBLE : View.GONE);
        debugging.setVisibility(selected == debugging ? View.VISIBLE : View.GONE);
        ViewParent parent = root.getParent();
        if (parent instanceof ScrollView) ((ScrollView) parent).scrollTo(0, 0);
    }

    public boolean goBack() {
        if (current == main) return false;
        show(main);
        return true;
    }

    public boolean onActivityResult(int request, int result, Intent data) {
        if (request != RendererSettings.PICK_DRIVER) return false;
        show(options);
        renderer.onPickerResult(result, data);
        return true;
    }

    private static void move(LinearLayout from, LinearLayout to, View view) {
        move(from, to, view, to.getChildCount());
    }

    private static void move(LinearLayout from, LinearLayout to, View view, int index) {
        from.removeView(view);
        to.addView(view, index);
    }
}
