package com.example.caloriecounter.view;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.caloriecounter.R;
import com.example.caloriecounter.databinding.ActivityWaterTrackerBinding;
import com.example.caloriecounter.model.FoodLog;
import com.example.caloriecounter.repository.SupabaseRepository;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WaterTrackerActivity extends BaseActivity {

    private static final String WATER_PREFS = "WATER_PREFS";
    private static final String KEY_GOAL = "water_goal";
    private static final String KEY_REMINDERS_ENABLED = "water_reminders_enabled";
    private static final String KEY_REMINDER_INTERVAL = "water_reminder_interval_min";

    private static final int REMINDER_REQUEST_CODE = 100;
    private static final int ONE_TIME_REMINDER_REQUEST_CODE = 101;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 102;
    private static final int DEFAULT_REMINDER_INTERVAL = 120;
    private static final int DEFAULT_WEIGHT = 70;
    private static final int WEIGHT_TO_WATER_FACTOR = 30;
    private static final long ONE_MINUTE_MS = 60_000L;

    private static final String[] RU_DAYS = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

    private ActivityWaterTrackerBinding binding;
    private SharedPreferences prefs;
    private SupabaseRepository repo;

    private String userId;
    private LocalDate selectedDate;

    private int currentGoal = 2000;

    private final Map<String, List<FoodLog>> allLogsByDate = new HashMap<>();
    private boolean isDataLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWaterTrackerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences(WATER_PREFS, MODE_PRIVATE);
        SharedPreferences authPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        userId = authPrefs.getString("user_id", "");
        repo = new SupabaseRepository(this);

        if (userId.isEmpty()) {
            finish();
            return;
        }

        selectedDate = LocalDate.now();
        currentGoal = prefs.getInt(KEY_GOAL,
                authPrefs.getInt("weight", DEFAULT_WEIGHT) * WEIGHT_TO_WATER_FACTOR);

        binding.btnAddWater.setOnClickListener(v -> addWater());
        binding.btnReminderSettings.setOnClickListener(v -> showReminderDialog());
        binding.btnToday.setOnClickListener(v -> {
            selectedDate = LocalDate.now();
            renderWeek();
            updateSelectedDayUI();
        });

        setupBottomNavigation();
        loadWaterHistory();
        renderWeek();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderWeek();
        if (!isDataLoaded) {
            loadWaterHistory();
        } else {
            updateSelectedDayUI();
        }
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = binding.bottomNavigation;
        bottomNav.setSelectedItemId(R.id.nav_water);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_training) {
                startActivity(new Intent(this, TrainingActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return false;
            }
            if (id == R.id.nav_water) {
                return true;
            }
            if (id == R.id.nav_food) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            }
            if (id == R.id.nav_diets) {
                startActivity(new Intent(this, DietListActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            }
            if (id == R.id.nav_weight) {
                startActivity(new Intent(this, WeightTrackerActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            }
            return false;
        });
    }

    private void showReminderDialog() {
        boolean enabled = prefs.getBoolean(KEY_REMINDERS_ENABLED, false);
        int interval = prefs.getInt(KEY_REMINDER_INTERVAL, DEFAULT_REMINDER_INTERVAL);
        String intervalText = interval < 60
                ? interval + " мин"
                : (interval / 60) + " ч";

        String[] options = enabled
                ? new String[]{"Изменить интервал (" + intervalText + ")", "Установить время", "Выключить"}
                : new String[]{"Включить (" + intervalText + ")", "Установить время", "Выключить"};

        new AlertDialog.Builder(this)
                .setTitle("Настройки напоминаний")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        setupReminder(!enabled, interval);
                    } else if (which == 1) {
                        showTimePickerDialog();
                    } else {
                        setupReminder(false, interval);
                    }
                })
                .show();
    }

    private void showTimePickerDialog() {
        Calendar calendar = Calendar.getInstance();

        new TimePickerDialog(this, (view, hour, minute) -> {
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);

            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            Intent intent = new Intent(this, WaterReminderReceiver.class);
            intent.putExtra("is_one_time", true);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(this,
                    ONE_TIME_REMINDER_REQUEST_CODE, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && !alarmManager.canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
                return;
            }

            alarmManager.cancel(pendingIntent);
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
        },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true).show();
    }

    private void setupReminder(boolean enabled, int minutes) {
        if (enabled && !checkPermission()) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, WaterReminderReceiver.class);
        intent.putExtra("interval_minutes", minutes);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this,
                REMINDER_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && !alarmManager.canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
                return;
            }
            alarmManager.cancel(pendingIntent);
            long intervalMs = minutes * ONE_MINUTE_MS;
            alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + intervalMs,
                    intervalMs,
                    pendingIntent);
        } else {
            alarmManager.cancel(pendingIntent);
        }

        prefs.edit()
                .putBoolean(KEY_REMINDERS_ENABLED, enabled)
                .putInt(KEY_REMINDER_INTERVAL, minutes)
                .apply();
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    private void loadWaterHistory() {
        Log.d("WATER_DEBUG", "Загружаю все записи воды для пользователя: " + userId);

        repo.fetchWaterLogs(userId, null, new SupabaseRepository.LogListCallback() {
            @Override
            public void onSuccess(List<FoodLog> logs) {
                Log.d("WATER_DEBUG", "Загружено записей: " + (logs != null ? logs.size() : 0));

                allLogsByDate.clear();
                if (logs != null) {
                    for (FoodLog log : logs) {
                        if (log.logDate != null) {
                            allLogsByDate
                                    .computeIfAbsent(log.logDate, key -> new ArrayList<>())
                                    .add(log);
                        }
                    }
                }

                isDataLoaded = true;
                runOnUiThread(() -> {
                    renderWeek();
                    updateSelectedDayUI();
                });
            }

            @Override
            public void onError(String message) {
                Log.e("WATER_DEBUG", "Ошибка загрузки: " + message);
                runOnUiThread(WaterTrackerActivity.this::updateSelectedDayUI);
            }
        });
    }

    private void saveWater(int amount) {
        Log.d("WATER_DEBUG", "Сохраняю: " + amount + " мл, дата: " + selectedDate
                + ", пользователь: " + userId);

        FoodLog log = new FoodLog();
        log.id = UUID.randomUUID().toString();
        log.logDate = selectedDate.toString();
        log.foodName = amount + " мл";
        log.ml = amount;
        log.createdAt = new Date().toString();

        allLogsByDate
                .computeIfAbsent(log.logDate, key -> new ArrayList<>())
                .add(log);

        repo.addWaterLog(userId, amount, log.logDate, new SupabaseRepository.VoidCallback() {
            @Override
            public void onSuccess() {
                Log.d("WATER_DEBUG", "Успешно сохранено в облако");
                runOnUiThread(() -> {
                    isDataLoaded = true;
                    updateSelectedDayUI();
                    renderWeek();
                });
            }

            @Override
            public void onError(String message) {
                Log.e("WATER_DEBUG", "Ошибка сохранения: " + message);
                runOnUiThread(() -> {
                    updateSelectedDayUI();
                    renderWeek();
                });
            }
        });
    }

    private void renderWeek() {
        if (binding.weekContainer == null) {
            return;
        }
        binding.weekContainer.removeAllViews();

        int sizePx = (int) (49 * getResources().getDisplayMetrics().density);
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);

        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            String dateStr = date.toString();
            boolean isSelected = dateStr.equals(selectedDate.toString());

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(4, 4, 4, 4);
            item.setClickable(true);
            item.setOnClickListener(v -> {
                selectedDate = date;
                renderWeek();
                updateSelectedDayUI();
            });

            TextView circle = new TextView(this);
            circle.setText(String.valueOf(date.getDayOfMonth()));
            circle.setTextSize(15);
            circle.setTextColor(isSelected ? Color.WHITE : Color.parseColor("#333333"));
            circle.setGravity(Gravity.CENTER);
            circle.setTypeface(null, Typeface.BOLD);
            circle.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
            circle.setBackgroundResource(isSelected ? R.drawable.circle_selected : R.drawable.circle_bg);
            if (isSelected) {
                circle.setTag("accent_circle");
                circle.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(ActivityPalette.accent(this)));
            }

            TextView dayLabel = new TextView(this);
            dayLabel.setText(RU_DAYS[i]);
            dayLabel.setTextSize(10);
            dayLabel.setGravity(Gravity.CENTER);
            dayLabel.setLayoutParams(new LinearLayout.LayoutParams(sizePx, -2));
            dayLabel.setPadding(0, 2, 0, 0);
            dayLabel.setTextColor(Color.parseColor("#666666"));

            item.addView(circle);
            item.addView(dayLabel);
            binding.weekContainer.addView(item);
        }
    }

    private void updateSelectedDayUI() {
        String dateStr = selectedDate.toString();
        List<FoodLog> logs = allLogsByDate.getOrDefault(dateStr, new ArrayList<>());

        Log.d("WATER_DEBUG", "updateSelectedDayUI() для даты: " + dateStr + ", записей: " + logs.size());

        int total = sumMl(logs);

        if (binding.tvConsumed != null) {
            binding.tvConsumed.setText("Выпито: " + total + " мл");
        }

        int remaining = currentGoal - total;
        if (binding.tvRemaining != null) {
            binding.tvRemaining.setText("Осталось: " + remaining + " мл");
            binding.tvRemaining.setTextColor(Color.parseColor("#666666"));
        }

        if (binding.llTodayLogs != null) {
            renderLogsList(logs);
        }
    }

    private int sumMl(List<FoodLog> logs) {
        int total = 0;
        for (FoodLog log : logs) {
            Log.d("WATER_DEBUG", "  Запись: ml=" + log.ml + ", foodName=" + log.foodName);

            if (log.ml != null) {
                total += log.ml;
            } else if (log.foodName != null && log.foodName.contains("мл")) {
                total += parseMl(log.foodName);
            }
        }
        Log.d("WATER_DEBUG", "  Итого: " + total + " мл");
        return total;
    }

    private void renderLogsList(List<FoodLog> logs) {
        binding.llTodayLogs.removeAllViews();

        if (logs.isEmpty()) {
            binding.llTodayLogs.setVisibility(View.GONE);
            return;
        }
        binding.llTodayLogs.setVisibility(View.VISIBLE);

        List<FoodLog> displayLogs = new ArrayList<>(logs);
        Collections.reverse(displayLogs);

        for (FoodLog log : displayLogs) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 6, 0, 6);

            int mlToShow = (log.ml != null) ? log.ml : parseMl(log.foodName);

            TextView amountView = new TextView(this);
            amountView.setText("💧 " + mlToShow + " мл");
            amountView.setTextSize(13);
            amountView.setTextColor(Color.parseColor("#212121"));
            amountView.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView deleteView = new TextView(this);
            deleteView.setText("✕");
            deleteView.setTextSize(15);
            deleteView.setTextColor(Color.parseColor("#FF6B6B"));
            deleteView.setClickable(true);
            deleteView.setPadding(8, 0, 0, 0);
            deleteView.setOnClickListener(v -> deleteLogEntry(log));

            row.addView(amountView);
            row.addView(deleteView);
            binding.llTodayLogs.addView(row);

            View divider = new View(this);
            divider.setBackgroundColor(Color.parseColor("#EAEAEA"));
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            binding.llTodayLogs.addView(divider);
        }
    }

    private int parseMl(String text) {
        if (text == null) {
            return 0;
        }
        try {
            return Integer.parseInt(text.replace(" мл", "").trim());
        } catch (Exception ex) {
            return 0;
        }
    }

    private void addWater() {
        String value = binding.etAmount.getText().toString().trim();
        if (value.isEmpty()) {
            return;
        }
        saveWater(Integer.parseInt(value));
        if (binding.etAmount != null) {
            binding.etAmount.setText("");
        }
    }

    private void deleteLogEntry(FoodLog log) {
        List<FoodLog> list = allLogsByDate.get(log.logDate);
        if (list != null) {
            boolean removed = list.removeIf(item -> item.id != null && item.id.equals(log.id));
            if (!removed) {
                Log.w("WATER_DEBUG", "Запись не найдена локально: " + log.id);
            }
        }

        if (log.id != null) {
            repo.deleteWaterLog(log.id, new SupabaseRepository.VoidCallback() {
                @Override
                public void onSuccess() {
                    Log.d("WATER_DEBUG", "Удалено из облака: " + log.id);
                }

                @Override
                public void onError(String message) {
                    Log.e("WATER_DEBUG", "Ошибка удаления: " + message);
                }
            });
        } else {
            Log.w("WATER_DEBUG", "Нет ID для удаления");
        }

        updateSelectedDayUI();
        renderWeek();
    }
}