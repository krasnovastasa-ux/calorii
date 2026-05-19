package com.example.caloriecounter.view;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import com.example.caloriecounter.R;
import com.example.caloriecounter.utils.ThemeUtils;

public abstract class BaseActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyAccentToAll();
        applyStatusBarColor();
    }

    private void applyTheme() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String theme = prefs.getString("theme_mode", "system");
        if ("light".equals(theme)) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        else if ("dark".equals(theme)) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    private void applyAccentToAll() {
        String accent = ThemeUtils.getAccent(this);
        View root = findViewById(android.R.id.content);
        if (root != null) ThemeUtils.applyAccent(root, accent);
    }

    private void applyStatusBarColor() {
        try {
            String accent = ThemeUtils.getAccent(this);
            int color = android.graphics.Color.parseColor(accent);
            Window window = getWindow();
            if (window != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    window.setStatusBarColor(color);
                    boolean isDark = (0.299 * android.graphics.Color.red(color) +
                            0.587 * android.graphics.Color.green(color) +
                            0.114 * android.graphics.Color.blue(color)) < 186;
                    if (isDark) {
                        window.getDecorView().setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                    } else {
                        window.getDecorView().setSystemUiVisibility(0);
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}