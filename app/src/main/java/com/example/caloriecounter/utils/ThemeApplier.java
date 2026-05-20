package com.example.caloriecounter.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.caloriecounter.R;

public class ThemeApplier {
    private static final int ORIGINAL_PRIMARY = Color.parseColor("#6C63FF");

    public static String getAccent(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx).getString("accent_color", "#6C63FF");
    }

    public static void applyAll(Context ctx, View root) {
        String hex = getAccent(ctx);
        try {
            int color = Color.parseColor(hex);
            applyById(root, color);
            applyRecursively(root, color);
        } catch (Exception ignored) {}
    }

    private static void applyById(View root, int color) {
        if (root == null) return;

        tintButtonIfFound(root, R.id.btnProfile, color);
        tintButtonIfFound(root, R.id.btnCalendar, color);
        tintButtonIfFound(root, R.id.btnToday, color);
        tintButtonIfFound(root, R.id.btnBreakfast, color);
        tintButtonIfFound(root, R.id.btnLunch, color);
        tintButtonIfFound(root, R.id.btnDinner, color);
        tintButtonIfFound(root, R.id.btnSnack, color);
        tintButtonIfFound(root, R.id.btnAddWater, color);
        tintButtonIfFound(root, R.id.btnReminderSettings, color);
        tintButtonIfFound(root, R.id.btnSave, color);
        tintButtonIfFound(root, R.id.btnAddRecipe, color);

        tintBottomNavIfFound(root, R.id.bottom_navigation, color);
        tintTextWithTag(root, color);
    }

    private static void tintButtonIfFound(View root, int id, int color) {
        View v = root.findViewById(id);
        if (v instanceof MaterialButton) {
            MaterialButton btn = (MaterialButton) v;
            btn.setStrokeColor(ColorStateList.valueOf(color));
            btn.setBackgroundTintList(ColorStateList.valueOf(color));
            boolean dark = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) < 186;
            btn.setTextColor(dark ? Color.WHITE : Color.BLACK);
        }
    }

    private static void tintBottomNavIfFound(View root, int id, int color) {
        View v = root.findViewById(id);
        if (v instanceof BottomNavigationView) {
            ((BottomNavigationView) v).setItemIconTintList(ColorStateList.valueOf(color));
            ((BottomNavigationView) v).setItemTextColor(ColorStateList.valueOf(color));
        }
    }

    private static void tintTextWithTag(View root, int color) {
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof TextView && !"btnLogout".equals(child.getResources().getResourceEntryName(child.getId()))) {
                    if ("primary".equals(child.getTag())) {
                        ((TextView) child).setTextColor(color);
                    }
                }
                if (child instanceof ViewGroup) {
                    tintTextWithTag(child, color);
                }
            }
        }
    }

    private static void applyRecursively(View view, int accent) {
        if (view == null) return;
        if (view instanceof TextView && !(view instanceof MaterialButton)) {
            TextView tv = (TextView) view;
            if (tv.getCurrentTextColor() == ORIGINAL_PRIMARY) {
                tv.setTextColor(accent);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyRecursively(group.getChildAt(i), accent);
            }
        }
    }
}