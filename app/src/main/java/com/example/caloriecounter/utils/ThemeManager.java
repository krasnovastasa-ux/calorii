package com.example.caloriecounter.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {
    private static final String PREFS = "app_theme";

    public static void apply(Context ctx, String theme, String accent) {
        if ("light".equals(theme))
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        else if ("dark".equals(theme))
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        else
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("theme_mode", theme)
                .putString("accent_color", accent)
                .apply();
    }

    public static String getAccent(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("accent_color", "#6C63FF");
    }
}