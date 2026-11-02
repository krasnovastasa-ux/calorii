package com.example.caloriecounter.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import com.example.caloriecounter.R;
import com.example.caloriecounter.databinding.ActivityMainBinding;
import com.example.caloriecounter.model.FoodLog;
import com.example.caloriecounter.repository.SupabaseRepository;
import com.example.caloriecounter.viewmodel.MainViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends BaseActivity {

    private static final Locale RU = new Locale("ru");
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String WEEKDAY_PATTERN = "EEE";

    private ActivityMainBinding binding;
    private MainViewModel vm;
    private SupabaseRepository repo;

    private MainViewModel.TargetData target;
    private MainViewModel.ConsumedData consumed;

    private String selectedDate;
    private String userId;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN, Locale.ROOT);
    private final SimpleDateFormat weekdayFormat = new SimpleDateFormat(WEEKDAY_PATTERN, RU);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repo = new SupabaseRepository(this);
        vm = new ViewModelProvider(this).get(MainViewModel.class);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        userId = prefs.getString("user_id", null);

        selectedDate = vm.getCurrentDate();

        setupObservers();
        setupClickListeners();
        setupBottomNavigation();
        renderWeek();
    }

    @Override
    protected void onResume() {
        super.onResume();
        vm.refreshTargets();
        vm.refresh();
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = binding.bottomNavigation;
        bottomNav.setSelectedItemId(R.id.nav_food);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_training) {
                startActivity(new Intent(this, TrainingActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return false;
            }
            if (id == R.id.nav_diets) {
                startActivity(new Intent(this, DietListActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            }
            if (id == R.id.nav_food) {
                return true;
            }
            if (id == R.id.nav_water) {
                startActivity(new Intent(this, WaterTrackerActivity.class));
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

    private void setupObservers() {
        vm.getTargetData().observe(this, data -> {
            if (data != null) {
                target = data;
                updateUI();
            }
        });
        vm.getConsumedData().observe(this, data -> {
            if (data != null) {
                consumed = data;
                updateUI();
            }
        });
        vm.getAllLogs().observe(this, logs -> {
            if (logs != null) {
                renderAllMeals(logs);
            }
        });
    }

    private void setupClickListeners() {
        binding.btnBreakfast.setOnClickListener(v -> openFoodSearch("Завтрак"));
        binding.btnLunch.setOnClickListener(v -> openFoodSearch("Обед"));
        binding.btnDinner.setOnClickListener(v -> openFoodSearch("Ужин"));
        binding.btnSnack.setOnClickListener(v -> openFoodSearch("Перекус"));
        binding.btnProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
        binding.btnCalendar.setOnClickListener(v ->
                startActivity(new Intent(this, CalendarActivity.class)));
        binding.btnToday.setOnClickListener(v -> {
            selectedDate = dateFormat.format(Calendar.getInstance().getTime());
            vm.setDate(selectedDate);
            renderWeek();
            updateUI();
        });
    }

    private void openFoodSearch(String meal) {
        Intent intent = new Intent(this, FoodSearchActivity.class);
        intent.putExtra("meal", meal);
        intent.putExtra("selected_date", selectedDate);
        intent.putExtra("userId", userId);
        startActivity(intent);
    }

    private void renderWeek() {
        if (binding.weekContainer == null) {
            return;
        }
        binding.weekContainer.removeAllViews();

        Calendar calendar = Calendar.getInstance();
        try {
            calendar.setTime(Objects.requireNonNull(dateFormat.parse(selectedDate)));
        } catch (Exception ex) {
            return;
        }
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);

        int circleSize = (int) (getResources().getDisplayMetrics().density * 47.5);

        for (int i = 0; i < 7; i++) {
            String date = dateFormat.format(calendar.getTime());
            String dayNum = String.valueOf(calendar.get(Calendar.DAY_OF_MONTH));
            String dayName = weekdayFormat.format(calendar.getTime())
                    .substring(0, 2)
                    .toUpperCase();

            boolean isSelected = date.equals(selectedDate);

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(4, 2, 4, 2);
            item.setClickable(true);

            TextView circle = new TextView(this);
            circle.setText(dayNum);
            circle.setTextSize(15);
            circle.setTextColor(isSelected ? Color.WHITE : Color.parseColor("#333333"));
            circle.setTypeface(null, Typeface.BOLD);
            circle.setGravity(Gravity.CENTER);
            circle.setLayoutParams(new LinearLayout.LayoutParams(circleSize, circleSize));
            circle.setBackgroundResource(isSelected ? R.drawable.circle_selected : R.drawable.circle_bg);
            if (isSelected) {
                circle.setTag("accent_circle");
                circle.setBackgroundTintList(ColorStateList.valueOf(ActivityPalette.accent(this)));
            }

            TextView dayLabel = new TextView(this);
            dayLabel.setText(dayName);
            dayLabel.setTextSize(9);
            dayLabel.setTextColor(Color.parseColor("#666666"));
            dayLabel.setGravity(Gravity.CENTER);
            dayLabel.setLayoutParams(new LinearLayout.LayoutParams(circleSize, -2));
            dayLabel.setPadding(0, 1, 0, 0);

            item.addView(circle);
            item.addView(dayLabel);

            final String currentDateStr = date;
            item.setOnClickListener(v -> {
                selectedDate = currentDateStr;
                vm.setDate(selectedDate);
                renderWeek();
                updateUI();
            });

            binding.weekContainer.addView(item);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    private void updateUI() {
        if (target == null || consumed == null) {
            return;
        }

        int warningColor = getResources().getColor(R.color.warning, getTheme());
        int dangerColor = getResources().getColor(R.color.danger, getTheme());
        int defaultColor = getResources().getColor(R.color.text_default, getTheme());

        binding.tvConsumed.setText(String.format("%d ккал", consumed.cal));

        int remaining = target.cal - consumed.cal;
        binding.tvRemaining.setText(String.format("Осталось: %d ккал", remaining));
        binding.tvRemaining.setTextColor(remaining <= 0 ? dangerColor : defaultColor);

        updateMacroText(binding.tvPro, "Белки", consumed.pro, target.pro, defaultColor, warningColor, dangerColor);
        updateMacroText(binding.tvFat, "Жиры", consumed.fat, target.fat, defaultColor, warningColor, dangerColor);
        updateMacroText(binding.tvCarbs, "Углеводы", consumed.carb, target.carb, defaultColor, warningColor, dangerColor);
        updateMacroText(binding.tvSugar, "Сахар", consumed.sugar, target.sugar, defaultColor, warningColor, dangerColor);
        updateMacroText(binding.tvFiber, "Клетчатка", consumed.fiber, target.fiber, defaultColor, warningColor, dangerColor);
    }

    private void updateMacroText(TextView view,
                                 String label,
                                 int consumedValue,
                                 int targetValue,
                                 int defaultColor,
                                 int warningColor,
                                 int dangerColor) {
        view.setText(String.format("%s: %dг / %dг", label, consumedValue, targetValue));

        int percent = targetValue > 0 ? (consumedValue * 100 / targetValue) : 0;
        if (percent >= 100) {
            view.setTextColor(dangerColor);
        } else if (percent >= 70) {
            view.setTextColor(warningColor);
        } else {
            view.setTextColor(defaultColor);
        }
    }

    private void renderAllMeals(List<FoodLog> logs) {
        renderMealList(binding.llBreakfastList, binding.tvBreakfastTotal, filterLogs(logs, "Завтрак"));
        renderMealList(binding.llLunchList, binding.tvLunchTotal, filterLogs(logs, "Обед"));
        renderMealList(binding.llDinnerList, binding.tvDinnerTotal, filterLogs(logs, "Ужин"));
        renderMealList(binding.llSnackList, binding.tvSnackTotal, filterLogs(logs, "Перекус"));
    }

    private List<FoodLog> filterLogs(List<FoodLog> logs, String mealType) {
        List<FoodLog> result = new ArrayList<>();
        if (logs != null) {
            for (FoodLog log : logs) {
                if (mealType.equals(log.mealType)) {
                    result.add(log);
                }
            }
        }
        return result;
    }

    private void renderMealList(LinearLayout container, TextView totalView, List<FoodLog> items) {
        container.removeAllViews();

        int totalCal = 0;

        if (items != null) {
            for (FoodLog log : items) {
                totalCal += log.totalCalories;
                container.addView(mealRow(log));
                container.addView(mealDivider());
            }
        }

        totalView.setText(String.format("%d ккал", totalCal));
    }

    private LinearLayout mealRow(FoodLog log) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(12, 6, 12, 6);

        TextView name = new TextView(this);
        name.setText(String.format("%s — %d ккал", log.foodName, log.totalCalories));
        name.setTextSize(14);
        name.setTextColor(Color.parseColor("#333333"));
        name.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(name);

        TextView delete = new TextView(this);
        delete.setText("✕");
        delete.setTextSize(18);
        delete.setTextColor(Color.parseColor("#D32F2F"));
        delete.setPadding(12, 0, 0, 0);
        delete.setClickable(true);
        delete.setOnClickListener(v -> deleteLog(log.id, log.logDate));
        row.addView(delete);

        return row;
    }

    private View mealDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#EAEAEA"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return divider;
    }

    private void deleteLog(String logId, String date) {
        repo.deleteLog(logId, new SupabaseRepository.VoidCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> vm.setDate(date));
            }

            @Override
            public void onError(String message) {
            }
        });
    }
}