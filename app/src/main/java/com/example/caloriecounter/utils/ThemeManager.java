package com.example.caloriecounter.utils;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {

    private static final String ACCENT_DEFAULT = "#6C63FF";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";

    private ThemeManager() {
    }

    public static void apply(Context context, String theme, String accent) {
        if (THEME_LIGHT.equals(theme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (THEME_DARK.equals(theme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }

        android.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString("theme_mode", theme)
                .putString("accent_color", accent)
                .apply();
    }

    public static String getAccent(Context context) {
        return android.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getString("accent_color", ACCENT_DEFAULT);
    }
}