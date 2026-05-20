package com.example.caloriecounter.view;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;
import androidx.lifecycle.ViewModelProvider;
import com.example.caloriecounter.R;
import com.example.caloriecounter.databinding.ActivityWeightTrackerBinding;
import com.example.caloriecounter.repository.SupabaseRepository;
import com.example.caloriecounter.utils.ThemeUtils;
import com.example.caloriecounter.viewmodel.WeightTrackerViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class WeightTrackerActivity extends BaseActivity {
    private ActivityWeightTrackerBinding binding;
    private WeightTrackerViewModel vm;
    private SupabaseRepository repo;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWeightTrackerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String userId = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString("user_id", "");

        vm = new WeightTrackerViewModel(getApplication(), userId);
        repo = new SupabaseRepository(this);

        setupUI();
        setupBottomNavigation();
        observeData();
        vm.loadLogs();

        binding.btnWeek.post(() -> {
            highlightBtn(binding.btnWeek);
            vm.setPeriod("week");
        });
    }

    private void setupUI() {
        updateDateField(vm.getSelectedDate());

        binding.btnWeek.setOnClickListener(v -> {
            highlightBtn(binding.btnWeek);
            vm.setPeriod("week");
            vm.loadLogs();
        });
        binding.btnMonth.setOnClickListener(v -> {
            highlightBtn(binding.btnMonth);
            vm.setPeriod("month");
            vm.loadLogs();
        });
        binding.btnYear.setOnClickListener(v -> {
            highlightBtn(binding.btnYear);
            vm.setPeriod("year");
            vm.loadLogs();
        });

        binding.etDate.setOnClickListener(v -> {
            LocalDate d = vm.getSelectedDate();
            DatePickerDialog dlg = new DatePickerDialog(this, (view, y, m, day) -> {
                LocalDate picked = LocalDate.of(y, m + 1, day);
                if (!picked.isAfter(LocalDate.now())) {
                    vm.setSelectedDate(picked);
                    updateDateField(picked);
                } else Toast.makeText(this, "Future dates not available", Toast.LENGTH_SHORT).show();
            }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth());
            dlg.getDatePicker().setMaxDate(System.currentTimeMillis());
            dlg.show();
        });

        binding.btnSaveWeight.setOnClickListener(v -> {
            String w = binding.etWeight.getText().toString().trim();
            if (w.isEmpty()) return;
            try {
                double weight = Double.parseDouble(w);
                if (weight < 20 || weight > 300) {
                    return;
                }
                vm.saveWeight(weight);
                binding.etWeight.setText("");
            } catch (NumberFormatException e) {
            }
        });

        binding.weightChart.setOnLogLongClickListener(log -> {
            new AlertDialog.Builder(this)
                    .setTitle("Edit weight for " + log.logDate)
                    .setMessage("Current weight: " + log.weight + " kg")
                    .setPositiveButton("Edit", (d, w) -> {
                        EditText input = new EditText(this);
                        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                        input.setText(String.valueOf(log.weight));

                        new AlertDialog.Builder(this)
                                .setTitle("New weight (kg)")
                                .setView(input)
                                .setPositiveButton("Save", (d2, w2) -> {
                                    try {
                                        double newWeight = Double.parseDouble(input.getText().toString());
                                        vm.setSelectedDate(LocalDate.parse(log.logDate));
                                        vm.saveWeight(newWeight);
                                    } catch (NumberFormatException e) {
                                        Toast.makeText(this, "Enter a number", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    })
                    .setNegativeButton("Delete", (d, w) -> {
                        repo.deleteWeightLog(log.id, new SupabaseRepository.VoidCallback() {
                            @Override
                            public void onSuccess() {
                                vm.loadLogs();
                            }
                            @Override
                            public void onError(String m) {
                                Toast.makeText(WeightTrackerActivity.this, m, Toast.LENGTH_SHORT).show();
                            }
                        });
                    })
                    .setNeutralButton("Cancel", null)
                    .show();
        });
    }

    private void observeData() {
        vm.getLogs().observe(this, binding.weightChart::setData);
        vm.isLoading().observe(this, l -> binding.progressBar.setVisibility(l ? View.VISIBLE : View.GONE));
        vm.getError().observe(this, e -> {
            if(e != null) Toast.makeText(this, e, Toast.LENGTH_LONG).show();
        });
        vm.getSaveSuccess().observe(this, s -> {
            if (s) {
                binding.etWeight.setText("");
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
        String accent = ThemeUtils.getAccent(this);
        int accentColor = android.graphics.Color.parseColor(accent);
        int grayColor = android.graphics.Color.parseColor("#E0E0E0");

        binding.btnWeek.setBackgroundTintList(android.content.res.ColorStateList.valueOf(grayColor));
        binding.btnMonth.setBackgroundTintList(android.content.res.ColorStateList.valueOf(grayColor));
        binding.btnYear.setBackgroundTintList(android.content.res.ColorStateList.valueOf(grayColor));

        active.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        active.setTextColor(0xFFFFFFFF);
    }
}