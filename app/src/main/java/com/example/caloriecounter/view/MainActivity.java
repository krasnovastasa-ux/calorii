package com.example.caloriecounter.view;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.caloriecounter.R;
import com.example.caloriecounter.databinding.ActivityMainBinding;
import com.example.caloriecounter.model.FoodLog;
import com.example.caloriecounter.repository.SupabaseRepository;
import com.example.caloriecounter.viewmodel.MainViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.text.SimpleDateFormat;
import android.content.SharedPreferences;
import java.util.ArrayList;
import android.preference.PreferenceManager;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends BaseActivity {
    private ActivityMainBinding binding;
    private MainViewModel vm;
    private SupabaseRepository repo;
    private MainViewModel.TargetData target;
    private MainViewModel.ConsumedData consumed;
    private String selectedDate;

    private String userId;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
    private final SimpleDateFormat sdfShort = new SimpleDateFormat("EEE", new Locale("ru"));

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
        vm.refresh();
        vm.refreshTargets();
        renderWeek();

    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = binding.bottomNavigation;
        bottomNav.setSelectedItemId(R.id.nav_food);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_diets) {
                startActivity(new Intent(this, DietListActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            } else if (id == R.id.nav_food) {
                return true;
            } else if (id == R.id.nav_water) {
                startActivity(new Intent(this, WaterTrackerActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            }
            return false;
        });
    }

    private void setupObservers() {
        vm.getTargetData().observe(this, t -> { if (t != null) { target = t; updateUI(); } });
        vm.getConsumedData().observe(this, c -> { if (c != null) { consumed = c; updateUI(); } });
        vm.getAllLogs().observe(this, logs -> { if (logs != null) renderAllMeals(logs); });
    }

    private void setupClickListeners() {
        binding.btnBreakfast.setOnClickListener(v -> openFoodSearch("Завтрак"));
        binding.btnLunch.setOnClickListener(v -> openFoodSearch("Обед"));
        binding.btnDinner.setOnClickListener(v -> openFoodSearch("Ужин"));
        binding.btnSnack.setOnClickListener(v -> openFoodSearch("Перекус"));
        binding.btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        binding.btnCalendar.setOnClickListener(v -> startActivity(new Intent(this, CalendarActivity.class)));
        binding.btnToday.setOnClickListener(v -> {
            selectedDate = sdf.format(Calendar.getInstance().getTime());
            vm.setDate(selectedDate);
            renderWeek();
            updateUI();
        });
    }

    private void openFoodSearch(String meal) {
        Intent i = new Intent(this, FoodSearchActivity.class);
        i.putExtra("meal", meal);
        i.putExtra("selected_date", selectedDate);
        i.putExtra("userId", userId);
        startActivity(i);
    }

    private void renderWeek() {
        if (binding.weekContainer == null) return;
        binding.weekContainer.removeAllViews();
        Calendar cal = Calendar.getInstance();
        try { cal.setTime(Objects.requireNonNull(sdf.parse(selectedDate))); } catch (Exception e) { return; }
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        int circleSize = (int) (getResources().getDisplayMetrics().density * 47.5);
        for (int i = 0; i < 7; i++) {
            String date = sdf.format(cal.getTime());
            String dayNum = String.valueOf(cal.get(Calendar.DAY_OF_MONTH));
            String dayName = sdfShort.format(cal.getTime()).substring(0, 2).toUpperCase();
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
            circle.setTypeface(null, android.graphics.Typeface.BOLD);
            circle.setGravity(Gravity.CENTER);
            circle.setLayoutParams(new LinearLayout.LayoutParams(circleSize, circleSize));
            circle.setBackgroundResource(isSelected ? R.drawable.circle_selected : R.drawable.circle_bg);
            if (isSelected) {
                circle.setTag("accent_circle");
            }
            TextView dayLabel = new TextView(this);
            dayLabel.setText(dayName);
            dayLabel.setTextSize(9);
            dayLabel.setTextColor(Color.parseColor("#666666"));
            dayLabel.setGravity(Gravity.CENTER);
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
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    private void updateUI() {
        if (target == null || consumed == null) return;

        int warningColor = getResources().getColor(R.color.warning, getTheme());
        int dangerColor = getResources().getColor(R.color.danger, getTheme());
        int defaultColor = getResources().getColor(R.color.text_default, getTheme());

        int calPercent = target.cal > 0 ? (consumed.cal * 100 / target.cal) : 0;
        binding.tvConsumed.setText(String.format("%d ккал", consumed.cal));

        int remaining = target.cal - consumed.cal;
        binding.tvRemaining.setText(String.format("Осталось: %d ккал", remaining));
        if (remaining <= 0) binding.tvRemaining.setTextColor(dangerColor);
        else binding.tvRemaining.setTextColor(defaultColor);

        updateMacroText(binding.tvPro, "Белки", consumed.pro, target.pro, defaultColor, warningColor, dangerColor);
        updateMacroText(binding.tvFat, "Жиры", consumed.fat, target.fat, defaultColor, warningColor, dangerColor);
        updateMacroText(binding.tvCarbs, "Углеводы", consumed.carb, target.carb, defaultColor, warningColor, dangerColor);
        updateMacroText(binding.tvSugar, "Сахар", consumed.sugar, target.sugar, defaultColor, warningColor, dangerColor);
        updateMacroText(binding.tvFiber, "Клетчатка", consumed.fiber, target.fiber, defaultColor, warningColor, dangerColor);
    }

    private void updateMacroText(TextView tv, String label, int cons, int tgt, int defaultColor, int warningColor, int dangerColor) {
        tv.setText(String.format("%s: %dг / %dг", label, cons, tgt));
        int percent = tgt > 0 ? (cons * 100 / tgt) : 0;
        if (percent >= 100) tv.setTextColor(dangerColor);
        else if (percent >= 70) tv.setTextColor(warningColor);
        else tv.setTextColor(defaultColor);
    }

    private void renderAllMeals(List<FoodLog> logs) {
        renderMealList(binding.llBreakfastList, binding.tvBreakfastTotal, filterLogs(logs, "Завтрак"));
        renderMealList(binding.llLunchList, binding.tvLunchTotal, filterLogs(logs, "Обед"));
        renderMealList(binding.llDinnerList, binding.tvDinnerTotal, filterLogs(logs, "Ужин"));
        renderMealList(binding.llSnackList, binding.tvSnackTotal, filterLogs(logs, "Перекус"));
    }

    private List<FoodLog> filterLogs(List<FoodLog> logs, String mealType) {
        List<FoodLog> result = new ArrayList<>();
        if (logs != null) for (FoodLog log : logs) if (mealType.equals(log.mealType)) result.add(log);
        return result;
    }

    private void renderMealList(LinearLayout container, TextView tvTotal, List<FoodLog> items) {
        container.removeAllViews();
        int totalCal = 0;
        if (items != null) {
            for (final FoodLog log : items) {
                totalCal += log.totalCalories;
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(12, 6, 12, 6);
                TextView tv = new TextView(this);
                tv.setText(String.format("%s — %d ккал", log.foodName, log.totalCalories));
                tv.setTextSize(14);
                tv.setTextColor(Color.parseColor("#333333"));
                LinearLayout.LayoutParams txtParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                tv.setLayoutParams(txtParams);
                row.addView(tv);
                TextView btnDel = new TextView(this);
                btnDel.setText("✕");
                btnDel.setTextSize(18);
                btnDel.setTextColor(Color.parseColor("#D32F2F"));
                btnDel.setPadding(12, 0, 0, 0);
                btnDel.setClickable(true);
                btnDel.setOnClickListener(v -> deleteLog(log.id, log.logDate));
                row.addView(btnDel);
                container.addView(row);
                View divider = new View(this);
                divider.setBackgroundColor(Color.parseColor("#EAEAEA"));
                divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                container.addView(divider);
            }
        }
        tvTotal.setText(String.format("%d ккал", totalCal));
    }

    private void deleteLog(String logId, String date) {
        repo.deleteLog(logId, new SupabaseRepository.VoidCallback() {
            @Override public void onSuccess() { runOnUiThread(() -> vm.setDate(date)); }
            @Override public void onError(String m) {}
        });
    }
}