package com.nakpaaiyaa.rbo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;

public final class RendererSettings {
    static final int PICK_DRIVER = 5053;
    private static final String VULKAN = "use_vulkan";
    private static final String DRIVER = "driver_id";
    private static final String SYSTEM = "System driver";
    private static final String PERFORMANCE = "performance_mode";

    private final Activity activity;
    private final Button renderer, install, driver, performance;
    private boolean importing;

    RendererSettings(Activity activity, LinearLayout parent) {
        this.activity = activity;
        renderer = new Button(activity);
        renderer.setOnClickListener(view -> chooseRenderer());
        parent.addView(renderer);
        install = new Button(activity);
        install.setText(AppLanguage.text("Install Turnip Driver"));
        install.setOnClickListener(view -> pickDriver());
        parent.addView(install);
        driver = new Button(activity);
        driver.setAllCaps(false);
        driver.setOnClickListener(view -> chooseDriver());
        parent.addView(driver);
        performance = new Button(activity);
        performance.setAllCaps(false);
        performance.setOnClickListener(view -> choosePerformance());
        parent.addView(performance);
        refresh();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("rendering", Context.MODE_PRIVATE);
    }

    public static void putSelection(Context context, Intent launch) {
        AppLanguage.putSelection(context, launch);
        SharedPreferences settings = preferences(context);
        launch.putExtra(VULKAN, settings.getBoolean(VULKAN, false));
        launch.putExtra(DRIVER, settings.getString(DRIVER, ""));
        launch.putExtra(PERFORMANCE, settings.getBoolean(PERFORMANCE, true));
    }

    public static void configureForGame(Activity activity) {
        System.loadLibrary("rbo_vulkan");
        Intent launch = activity.getIntent();
        configurePerformanceNative(launch.getBooleanExtra(PERFORMANCE, true));
        boolean useVulkan = launch.getBooleanExtra(VULKAN, false);
        DriverStore selection = useVulkan ? DriverStore.get(activity, launch.getStringExtra(DRIVER)) : null;
        configureNative(useVulkan,
                selection == null ? null : selection.directory.getAbsolutePath(),
                selection == null ? null : selection.library,
                activity.getApplicationInfo().nativeLibraryDir, activity.getCacheDir().getAbsolutePath());
    }

    private static native void configureNative(boolean vulkan, String directory, String library,
            String nativeLibraryDir, String cacheDir);
    private static native void configurePerformanceNative(boolean enabled);

    void refresh() {
        SharedPreferences settings = preferences(activity);
        boolean vulkan = settings.getBoolean(VULKAN, false);
        renderer.setText(AppLanguage.text(vulkan ? "Renderer · Vulkan" : "Renderer · CPU"));
        install.setText(AppLanguage.text("Install Turnip Driver"));
        DriverStore selected = DriverStore.get(activity, settings.getString(DRIVER, ""));
        driver.setText(AppLanguage.text("Vulkan driver") + " · " + (selected == null ? AppLanguage.text(SYSTEM) : selected.name));
        install.setVisibility(vulkan ? View.VISIBLE : View.GONE);
        driver.setVisibility(vulkan ? View.VISIBLE : View.GONE);
        renderer.setEnabled(!importing);
        install.setEnabled(!importing);
        driver.setEnabled(!importing);
        performance.setText(AppLanguage.text(settings.getBoolean(PERFORMANCE, true)
                ? "Performance mode · ON" : "Performance mode · OFF"));
        performance.setEnabled(!importing);
    }

    private void choosePerformance() {
        SharedPreferences settings = preferences(activity);
        new AlertDialog.Builder(activity).setTitle(AppLanguage.text("Performance mode"))
                .setSingleChoiceItems(AppLanguage.strings(new String[]{"On", "Off"}),
                        settings.getBoolean(PERFORMANCE, true) ? 0 : 1,
                        (dialog, item) -> {
                            settings.edit().putBoolean(PERFORMANCE, item == 0).apply();
                            refresh(); dialog.dismiss();
                        })
                .setNegativeButton(AppLanguage.text("Cancel"), null).show();
    }

    private void chooseRenderer() {
        SharedPreferences settings = preferences(activity);
        new AlertDialog.Builder(activity).setTitle(AppLanguage.text("Renderer"))
                .setSingleChoiceItems(new String[]{"CPU", "Vulkan"}, settings.getBoolean(VULKAN, false) ? 1 : 0,
                        (dialog, item) -> {
                            settings.edit().putBoolean(VULKAN, item == 1).apply();
                            refresh();
                            dialog.dismiss();
                        })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void pickDriver() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivityForResult(pick, PICK_DRIVER);
    }

    void onPickerResult(int result, Intent data) {
        if (result != Activity.RESULT_OK || data == null || data.getData() == null || importing) return;
        Uri zip = data.getData();
        importing = true;
        refresh();
        Toast.makeText(activity, AppLanguage.text("Installing Turnip driver…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                DriverStore added = DriverStore.install(activity.getApplicationContext(), zip);
                preferences(activity).edit().putString(DRIVER, added.id).apply();
                activity.runOnUiThread(() -> finishImport("Selected " + added.name, null));
            } catch (Exception error) {
                activity.runOnUiThread(() -> finishImport(null, error.getMessage()));
            }
        }, "rbo-driver-import").start();
    }

    private void finishImport(String selected, String error) {
        importing = false;
        if (activity.isFinishing() || activity.isDestroyed()) return;
        refresh();
        if (selected != null) {
            Toast.makeText(activity, AppLanguage.text(selected), Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(activity).setTitle(AppLanguage.text("Could not install driver"))
                .setMessage(AppLanguage.text(error == null ? "The selected file could not be imported." : error))
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void chooseDriver() {
        List<DriverStore> drivers = DriverStore.installed(activity);
        String[] labels = new String[drivers.size() + 1];
        labels[0] = AppLanguage.text(SYSTEM);
        SharedPreferences settings = preferences(activity);
        String selected = settings.getString(DRIVER, "");
        int checked = 0;
        for (int i = 0; i < drivers.size(); i++) {
            DriverStore candidate = drivers.get(i);
            labels[i + 1] = candidate.name;
            if (candidate.id.equals(selected)) checked = i + 1;
        }
        new AlertDialog.Builder(activity).setTitle(AppLanguage.text("Vulkan driver"))
                .setSingleChoiceItems(labels, checked, (dialog, item) -> {
                    settings.edit().putString(DRIVER, item == 0 ? "" : drivers.get(item - 1).id).apply();
                    refresh();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }
}
