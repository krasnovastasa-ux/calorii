package com.example.caloriecounter.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;
import com.example.caloriecounter.R;

public class ThemeUtils {
    private static final int ORIGINAL_PRIMARY = Color.parseColor("#6C63FF");

    public static String getAccent(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx).getString("accent_color", "#6C63FF");
    }

    public static void applyAccent(View root, String accentHex) {
        if (root == null) return;
        try {
            int color = Color.parseColor(accentHex);
            applyToView(root, color);
            if (root instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) root;
                for (int i = 0; i < group.getChildCount(); i++) {
                    applyAccent(group.getChildAt(i), accentHex);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void applyToView(View view, int color) {
        if (view == null) return;
        Object tag = view.getTag();

        if (tag != null && "accent".equals(tag) && view instanceof TextView && !(view instanceof MaterialButton)) {
            ((TextView) view).setTextColor(color);
        }

        if (tag != null && "accent_circle".equals(tag) && view instanceof TextView) {
            view.setBackgroundResource(R.drawable.circle_selected);
            ((TextView) view).setTextColor(Color.WHITE);
            if (view.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) view.getBackground()).setColor(color);
            }
        }

        if (view instanceof MaterialButton) {
            MaterialButton btn = (MaterialButton) view;
            if (btn.getId() != R.id.btnLogout) {
                btn.setBackgroundTintList(ColorStateList.valueOf(color));
                btn.setStrokeColor(ColorStateList.valueOf(color));
                boolean dark = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) < 186;
                btn.setTextColor(Color.WHITE);;
            }
        }

        if (view instanceof TextInputLayout) {
            TextInputLayout til = (TextInputLayout) view;

            int[][] strokeStates = new int[][] {
                    new int[] { android.R.attr.state_focused },
                    new int[] {}
            };
            int[] strokeColors = new int[] {
                    color,
                    Color.parseColor("#000000")
            };
            til.setBoxStrokeColorStateList(new ColorStateList(strokeStates, strokeColors));

            int[][] hintStates = new int[][] {
                    new int[] { android.R.attr.state_focused },
                    new int[] {}
            };
            int[] hintColors = new int[] {
                    color,
                    Color.parseColor("#757575")
            };
            ColorStateList hintColorList = new ColorStateList(hintStates, hintColors);
            til.setHintTextColor(hintColorList);
            til.setDefaultHintTextColor(hintColorList);
        }

        if (view instanceof TabLayout) {
            TabLayout tabs = (TabLayout) view;
            tabs.setTabTextColors(Color.parseColor("#6B7280"), color);
            tabs.setSelectedTabIndicatorColor(color);
        }

        if (view instanceof RadioButton) {
            ((RadioButton) view).setButtonTintList(ColorStateList.valueOf(color));
        }

        if (view instanceof BottomNavigationView) {
            ((BottomNavigationView) view).setItemIconTintList(ColorStateList.valueOf(color));
            ((BottomNavigationView) view).setItemTextColor(ColorStateList.valueOf(color));
        }
    }
}