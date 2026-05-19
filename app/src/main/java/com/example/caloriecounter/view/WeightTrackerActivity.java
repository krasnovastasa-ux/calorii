package com.example.caloriecounter.view;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import com.example.caloriecounter.R;
import android.view.View;
import android.content.Intent;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import android.preference.PreferenceManager;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.caloriecounter.databinding.ActivityWeightTrackerBinding;
import com.example.caloriecounter.utils.ThemeUtils;
import com.example.caloriecounter.viewmodel.WeightTrackerViewModel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class WeightTrackerActivity extends BaseActivity {
    private ActivityWeightTrackerBinding binding;
    private WeightTrackerViewModel vm;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWeightTrackerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String userId = android.preference.PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString("user_id", "");
        vm = new WeightTrackerViewModel(getApplication(), userId);

        setupUI();
        setupBottomNavigation();
        observeData();
        vm.loadLogs();
    }

    private void setupUI() {
        updateDateField(vm.getSelectedDate());

        binding.btnWeek.setOnClickListener(v -> { highlightBtn(binding.btnWeek); vm.setPeriod("week"); });
        binding.btnMonth.setOnClickListener(v -> { highlightBtn(binding.btnMonth); vm.setPeriod("month"); });
        binding.btnYear.setOnClickListener(v -> { highlightBtn(binding.btnYear); vm.setPeriod("year"); });

        binding.etDate.setOnClickListener(v -> {
            LocalDate d = vm.getSelectedDate();
            DatePickerDialog dlg = new DatePickerDialog(this, (view, y, m, day) -> {
                LocalDate picked = LocalDate.of(y, m + 1, day);
                if (!picked.isAfter(LocalDate.now())) {
                    vm.setSelectedDate(picked);
                    updateDateField(picked);
                } else Toast.makeText(this, "Будущие даты недоступны", Toast.LENGTH_SHORT).show();
            }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth());
            dlg.getDatePicker().setMaxDate(System.currentTimeMillis());
            dlg.show();
        });

        binding.btnSaveWeight.setOnClickListener(v -> {
            String w = binding.etWeight.getText().toString().trim();
            if (w.isEmpty()) return;
            try {
                vm.saveWeight(Double.parseDouble(w));
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Введите корректный вес", Toast.LENGTH_SHORT).show();
            }
        });
        highlightBtn(binding.btnWeek);
    }

    private void observeData() {
        vm.getLogs().observe(this, binding.weightChart::setData);
        vm.isLoading().observe(this, l -> binding.progressBar.setVisibility(l ? View.VISIBLE : View.GONE));
        vm.getError().observe(this, e -> { if(e!=null) Toast.makeText(this, e, Toast.LENGTH_LONG).show(); });
        vm.getSaveSuccess().observe(this, s -> {
            if (s) {
                Toast.makeText(this, "Вес сохранён и обновлён в профиле", Toast.LENGTH_SHORT).show();

                sendBroadcast(new Intent("com.example.caloriecounter.WEIGHT_UPDATED"));
            }
        });
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = binding.bottomNavigation;
        bottomNav.setSelectedItemId(R.id.nav_weight);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_weight) {
                return true;
            } else if (id == R.id.nav_food) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            } else if (id == R.id.nav_diets) {
                startActivity(new Intent(this, DietListActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            } else if (id == R.id.nav_water) {
                startActivity(new Intent(this, WaterTrackerActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            }
            return false;
        });
    }
    private void updateDateField(LocalDate d) {
        binding.etDate.setText(d.format(dtf));
    }

    private void highlightBtn(com.google.android.material.button.MaterialButton active) {
        binding.btnWeek.setBackgroundTintList(null); binding.btnWeek.setTextColor(getColor(com.example.caloriecounter.R.color.text_primary));
        binding.btnMonth.setBackgroundTintList(null); binding.btnMonth.setTextColor(getColor(com.example.caloriecounter.R.color.text_primary));
        binding.btnYear.setBackgroundTintList(null); binding.btnYear.setTextColor(getColor(com.example.caloriecounter.R.color.text_primary));

        String accent = ThemeUtils.getAccent(this);
        active.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(accent)));
        active.setTextColor(0xFFFFFFFF);
    }
}