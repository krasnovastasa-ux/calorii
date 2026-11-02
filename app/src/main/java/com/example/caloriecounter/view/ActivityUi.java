package com.example.caloriecounter.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import java.util.Locale;

final class ActivityUi {

    static final int BG = 0xff111111;
    static final int CARD = 0xff202020;
    static final int TEXT = 0xffe0e3d9;
    static final int MUTED = 0xffb9bdb1;
    static final int GREEN = 0xff568d2c;
    static final int HERO = 0xff4e8018;

    private static final Locale RU = new Locale("ru");
    private static final int PADDING_DP = 16;
    private static final int CARD_RADIUS_DP = 20;
    private static final int CARD_MARGIN_DP = 8;
    private static final int INPUT_MIN_HEIGHT_DP = 56;

    private ActivityUi() {
    }

    static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static String num(double value) {
        return String.format(RU, "%.1f", value);
    }

    static GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static TextView text(Context context, String text, int sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        return view;
    }

    static LinearLayout card(Context context, LinearLayout parent) {
        LinearLayout layout = column(context);
        int padding = dp(context, PADDING_DP);
        layout.setPadding(padding, padding, padding, padding);
        layout.setBackground(shape(ActivityPalette.surface(context), dp(context, CARD_RADIUS_DP)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        int margin = dp(context, CARD_MARGIN_DP);
        params.setMargins(0, margin, 0, margin);
        parent.addView(layout, params);
        return layout;
    }

    static Button button(Context context, String label) {
        Button button = new MaterialButton(context);
        button.setText(label);
        button.setTextColor(ActivityPalette.onAccent(context));
        button.setAllCaps(false);
        button.setBackgroundTintList(ColorStateList.valueOf(ActivityPalette.accent(context)));
        return button;
    }

    static EditText number(Context context, String hint) {
        EditText edit = new EditText(context);
        edit.setTextColor(ActivityPalette.text(context));
        edit.setHintTextColor(ActivityPalette.muted(context));
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edit.setMinHeight(dp(context, INPUT_MIN_HEIGHT_DP));
        edit.setBackgroundTintList(ColorStateList.valueOf(ActivityPalette.accent(context)));
        return edit;
    }
}