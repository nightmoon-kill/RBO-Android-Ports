package com.nakpaaiyaa.rbo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.Locale;

public final class AppLanguage {
    private static final String PREFERENCES="app_language";
    private static final String LANGUAGE="language";
    private static final String EXTRA="rbo_ui_language";
    private AppLanguage() {}

    public static void initialize(Activity activity) {
        String language=activity.getIntent().getStringExtra(EXTRA);
        if(language==null)language=activity.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).getString(LANGUAGE,"ENG");
        apply(activity,"THAI".equals(language));
    }
    public static void putSelection(Context context,Intent launch) {
        launch.putExtra(EXTRA,context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).getString(LANGUAGE,"ENG"));
    }
    static boolean isThai() { return UiText.isThai(); }
    static void select(Activity activity,boolean thai) {
        activity.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).edit().putString(LANGUAGE,thai?"THAI":"ENG").commit();
        apply(activity,thai);
    }
    @SuppressWarnings("deprecation")
    private static void apply(Activity activity,boolean thai) {
        UiText.setThai(thai);
        Locale locale=thai?new Locale("th","TH"):Locale.ENGLISH;
        Locale.setDefault(locale);
        Resources resources=activity.getResources();
        Configuration configuration=new Configuration(resources.getConfiguration());
        configuration.setLocale(locale);
        resources.updateConfiguration(configuration,resources.getDisplayMetrics());
    }
    public static String text(String value) { return UiText.text(value); }
    public static String text(CharSequence value) {
        if(value==null)return null;
        return text(value.toString());
    }
    public static String[] strings(String[] values) {
        String[] translated=values.clone();
        for(int i=0;i<translated.length;++i)translated[i]=text(translated[i]);
        return translated;
    }
    public static void refreshTree(View view) {
        if(view instanceof TextView){
            TextView label=(TextView)view;
            label.setText(text(label.getText()));
        }
        CharSequence description=view.getContentDescription();
        if(description!=null)view.setContentDescription(text(description));
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();++i)refreshTree(group.getChildAt(i));
        }
        view.invalidate();
    }
}
