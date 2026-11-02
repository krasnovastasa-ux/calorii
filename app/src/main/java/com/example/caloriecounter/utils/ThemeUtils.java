package com.example.caloriecounter.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import com.example.caloriecounter.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;

public class ThemeUtils {

    private static final String ACCENT_DEFAULT = "#6C63FF";
    private static final String HINT_COLOR_INACTIVE = "#757575";
    private static final String TAB_TEXT_INACTIVE = "#6B7280";
    private static final String STROKE_COLOR_INACTIVE = "#000000";

    private ThemeUtils() {
    }

    public static String getAccent(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString("accent_color", ACCENT_DEFAULT);
    }

    public static void applyAccent(View root, String accentHex) {
        if (root == null) {
            return;
        }
        try {
            int color = Color.parseColor(accentHex);
            applyToView(root, color);
            if (root instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) root;
                for (int i = 0; i < group.getChildCount(); i++) {
                    applyAccent(group.getChildAt(i), accentHex);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void applyToView(View view, int color) {
        if (view == null) {
            return;
        }

        Object tag = view.getTag();
        if (tag != null) {
            applyTag(view, tag, color);
        }

        if (view instanceof MaterialButton) {
            applyMaterialButton((MaterialButton) view, color);
        }

        if (view instanceof TextInputLayout) {
            applyTextInputLayout((TextInputLayout) view, color);
        }

        if (view instanceof TabLayout) {
            TabLayout tabs = (TabLayout) view;
            tabs.setTabTextColors(Color.parseColor(TAB_TEXT_INACTIVE), color);
            tabs.setSelectedTabIndicatorColor(color);
        }

        if (view instanceof RadioButton) {
            ((RadioButton) view).setButtonTintList(ColorStateList.valueOf(color));
        }

        if (view instanceof BottomNavigationView) {
            BottomNavigationView nav = (BottomNavigationView) view;
            nav.setItemIconTintList(ColorStateList.valueOf(color));
            nav.setItemTextColor(ColorStateList.valueOf(color));
        }
    }

    private static void applyTag(View view, Object tag, int color) {
        if ("accent".equals(tag) && view instanceof TextView && !(view instanceof MaterialButton)) {
            ((TextView) view).setTextColor(color);
        }

        if ("accent_circle".equals(tag) && view instanceof TextView) {
            view.setBackgroundResource(R.drawable.circle_selected);
            ((TextView) view).setTextColor(Color.WHITE);
            if (view.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) view.getBackground()).setColor(color);
            }
        }

        if ("accent_icon".equals(tag) && view instanceof ImageView) {
            ((ImageView) view).setImageTintList(ColorStateList.valueOf(color));
        }
    }

    private static void applyMaterialButton(MaterialButton button, int color) {
        if (button.getId() == R.id.btnLogout) {
            return;
        }

        button.setBackgroundTintList(ColorStateList.valueOf(color));
        button.setStrokeColor(ColorStateList.valueOf(color));
        button.setTextColor(Color.WHITE);
    }

    private static void applyTextInputLayout(TextInputLayout layout, int color) {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_focused},
                new int[]{}
        };

        int[] strokeColors = new int[]{
                color,
                Color.parseColor(STROKE_COLOR_INACTIVE)
        };
        layout.setBoxStrokeColorStateList(new ColorStateList(states, strokeColors));

        int[] hintColors = new int[]{
                color,
                Color.parseColor(HINT_COLOR_INACTIVE)
        };
        ColorStateList hintColorList = new ColorStateList(states, hintColors);
        layout.setHintTextColor(hintColorList);
        layout.setDefaultHintTextColor(hintColorList);
    }
}