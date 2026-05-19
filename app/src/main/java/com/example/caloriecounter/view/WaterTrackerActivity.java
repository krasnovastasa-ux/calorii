package com.example.caloriecounter.view;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import java.util.Calendar;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.caloriecounter.R;
import com.example.caloriecounter.databinding.ActivityWaterTrackerBinding;
import com.example.caloriecounter.model.FoodLog;
import com.example.caloriecounter.repository.SupabaseRepository;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WaterTrackerActivity extends AppCompatActivity {
    private boolean isDataLoaded = false;
    private ActivityWaterTrackerBinding binding;
    private SharedPreferences prefs, authPrefs;
    private SupabaseRepository repo;
    private String userId;
    private LocalDate selectedDate;
    private int currentGoal = 2000;
    private final Map<String, List<FoodLog>> allLogsByDate = new HashMap<>();
    private final String[] RU_DAYS = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWaterTrackerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences("WATER_PREFS", MODE_PRIVATE);
        authPrefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        userId = authPrefs.getString("user_id", "");
        repo = new SupabaseRepository(this);

        if (userId.isEmpty()) { finish(); return; }

        selectedDate = LocalDate.now();
        currentGoal = prefs.getInt("water_goal", authPrefs.getInt("weight", 70) * 30);

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

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = binding.bottomNavigation;
        bottomNav.setSelectedItemId(R.id.nav_water);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_water) {
                return true;
            } else if (id == R.id.nav_food) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            } else if (id == R.id.nav_diets) {
                startActivity(new Intent(this, DietListActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            }
            return false;
        });
    }

    private void showReminderDialog() {
        boolean isEnabled = prefs.getBoolean("water_reminders_enabled", false);
        int savedInterval = prefs.getInt("water_reminder_interval_min", 120);
        String intervalText = savedInterval < 60 ? savedInterval + " мин" : (savedInterval / 60) + " ч";

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Настройки напоминаний");
        String[] opts = isEnabled ?
                new String[]{"Изменить интервал ("+intervalText+")", "Установить время", "Выключить"} :
                new String[]{"Включить ("+intervalText+")", "Установить время", "Выключить"};

        builder.setItems(opts, (dialog, which) -> {
            if (which == 0) setupReminder(!isEnabled, savedInterval);
            else if (which == 1) showTimePickerDialog();
            else { setupReminder(false, savedInterval); }
        });
        builder.show();
    }

    private void showTimePickerDialog() {
        Calendar c = Calendar.getInstance();
        new android.app.TimePickerDialog(this, (v, h, m) -> {
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m);
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            Intent i = new Intent(this, WaterReminderReceiver.class);
            i.putExtra("is_one_time", true);
            PendingIntent pi = PendingIntent.getBroadcast(this, 101, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)); return;
            }
            am.cancel(pi); am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
    }

    private void setupReminder(boolean enabled, int mins) {
        if (enabled && !checkPermission()) return;
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent i = new Intent(this, WaterReminderReceiver.class);
        i.putExtra("interval_minutes", mins);
        PendingIntent pi = PendingIntent.getBroadcast(this, 100, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)); return;
            }
            am.cancel(pi);
            am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + (mins * 60000L), mins * 60000L, pi);
        } else { am.cancel(pi); }
        prefs.edit().putBoolean("water_reminders_enabled", enabled).putInt("water_reminder_interval_min", mins).apply();
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 102);
            return false;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isDataLoaded) {
            loadWaterHistory();
        } else {
            updateSelectedDayUI();
        }
    }

    private void loadWaterHistory() {
        Log.d("WATER_DEBUG", " Загружаю ВСЕ записи воды для пользователя: " + userId);
        repo.fetchWaterLogs(userId, null, new SupabaseRepository.LogListCallback() {
            @Override public void onSuccess(List<FoodLog> logs) {
                Log.d("WATER_DEBUG", " Загружено записей: " + (logs != null ? logs.size() : 0));

                allLogsByDate.clear();
                if (logs != null) {
                    for (FoodLog log : logs) {
                        if (log.logDate != null) {
                            allLogsByDate.computeIfAbsent(log.logDate, k -> new ArrayList<>()).add(log);
                        }
                    }
                }
                isDataLoaded = true;
                runOnUiThread(() -> {
                    renderWeek();
                    updateSelectedDayUI();
                });
            }
            @Override public void onError(String m) {
                Log.e("WATER_DEBUG", " Ошибка загрузки: " + m);

                runOnUiThread(() -> updateSelectedDayUI());
            }
        });
    }

    private void saveWater(int amount) {
        Log.d("WATER_DEBUG", " Сохраняю: " + amount + " мл, дата: " + selectedDate + ", пользователь: " + userId);
        FoodLog log = new FoodLog();
        log.id = java.util.UUID.randomUUID().toString();
        log.logDate = selectedDate.toString();
        log.foodName = amount + " мл";
        log.ml = amount;
        log.createdAt = new Date().toString();

        allLogsByDate.computeIfAbsent(log.logDate, k -> new ArrayList<>()).add(log);

        Log.d("WATER_DEBUG", " Отправляю запрос в Supabase...");
        repo.addWaterLog(userId, amount, log.logDate, new SupabaseRepository.VoidCallback() {
            @Override public void onSuccess() {
                Log.d("WATER_DEBUG", " Успешно сохранено в облако");
                runOnUiThread(() -> {
                    isDataLoaded = true;
                    updateSelectedDayUI();
                    renderWeek();
                });
            }
            @Override public void onError(String m) {
                Log.e("WATER_DEBUG", " Ошибка сохранения: " + m);
                runOnUiThread(() -> {
                    updateSelectedDayUI();
                    renderWeek();
                });
            }
        });
    }

    private void renderWeek() {
        if (binding.weekContainer == null) return;
        binding.weekContainer.removeAllViews();

        int sizePx = (int) (49 * getResources().getDisplayMetrics().density);
        LocalDate today = LocalDate.now();
        LocalDate mon = today.with(java.time.DayOfWeek.MONDAY);

        for (int i = 0; i < 7; i++) {
            LocalDate date = mon.plusDays(i);
            String dateStr = date.toString();
            List<FoodLog> logs = allLogsByDate.getOrDefault(dateStr, new ArrayList<>());
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
            circle.setTypeface(null, android.graphics.Typeface.BOLD);
            circle.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
            circle.setBackgroundResource(isSelected ? R.drawable.circle_selected : R.drawable.circle_bg);

            TextView dayLabel = new TextView(this);
            dayLabel.setText(RU_DAYS[i]);
            dayLabel.setTextSize(10);
            dayLabel.setGravity(Gravity.CENTER);
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
        Log.d("WATER_DEBUG", " updateSelectedDayUI() для даты: " + dateStr + ", записей: " + logs.size());

        int total = 0;
        for (FoodLog log : logs) {
            Log.d("WATER_DEBUG", "   Запись: ml=" + log.ml + ", foodName=" + log.foodName);

            if (log.ml != null) {
                total += log.ml;
            } else if (log.foodName != null && log.foodName.contains("мл")) {
                try {
                    total += Integer.parseInt(log.foodName.replace(" мл", "").trim());
                } catch (NumberFormatException e) {

                }
            }
        }
        Log.d("WATER_DEBUG", "   Итого: " + total + " мл");

        if (binding.tvConsumed != null) binding.tvConsumed.setText("Выпито: " + total + " мл");
        int remaining = currentGoal - total;
        if (binding.tvRemaining != null) {
            binding.tvRemaining.setText("Осталось: " + remaining + " мл");
            binding.tvRemaining.setTextColor(Color.parseColor("#666666"));
        }

        if (binding.llTodayLogs != null) {
            binding.llTodayLogs.removeAllViews();
            if (logs.isEmpty()) { binding.llTodayLogs.setVisibility(View.GONE); return; }
            binding.llTodayLogs.setVisibility(View.VISIBLE);

            List<FoodLog> displayLogs = new ArrayList<>(logs);
            Collections.reverse(displayLogs);
            for (FoodLog log : displayLogs) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, 6, 0, 6);

                TextView tv = new TextView(this);
                int mlToShow = (log.ml != null) ? log.ml : parseMl(log.foodName);
                tv.setText("💧 " + mlToShow + " мл");
                tv.setTextSize(13); tv.setTextColor(Color.parseColor("#212121"));
                tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                TextView del = new TextView(this);
                del.setText("✕"); del.setTextSize(15); del.setTextColor(Color.parseColor("#FF6B6B"));
                del.setClickable(true); del.setPadding(8,0,0,0);
                del.setOnClickListener(v -> deleteLogEntry(log));

                row.addView(tv); row.addView(del);
                binding.llTodayLogs.addView(row);

                View div = new View(this); div.setBackgroundColor(Color.parseColor("#EAEAEA"));
                div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                binding.llTodayLogs.addView(div);
            }
        }
    }

    private int parseMl(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s.replace(" мл", "").trim()); } catch (Exception e) { return 0; }
    }

    private void addWater() {
        String val = binding.etAmount.getText().toString().trim();
        if (val.isEmpty()) { return; }
        saveWater(Integer.parseInt(val));
        if (binding.etAmount != null) binding.etAmount.setText("");
    }

    private void deleteLogEntry(FoodLog log) {
        List<FoodLog> list = allLogsByDate.get(log.logDate);
        if (list != null) {
            boolean removed = list.removeIf(l -> l.id != null && l.id.equals(log.id));
            if (!removed) {
                Log.w("WATER_DEBUG", " Запись не найдена локально: " + log.id);
            }
        }

        if (log.id != null) {
            repo.deleteWaterLog(log.id, new SupabaseRepository.VoidCallback() {
                @Override public void onSuccess() {
                    Log.d("WATER_DEBUG", " Удалено из облака: " + log.id);
                }
                @Override public void onError(String m) {
                    Log.e("WATER_DEBUG", " Ошибка удаления: " + m);
                    runOnUiThread(() -> {});
                }
            });
        } else {
            Log.w("WATER_DEBUG", " Нет ID для удаления");
        }

        updateSelectedDayUI();
        renderWeek();
    }

}