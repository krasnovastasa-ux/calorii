package com.example.caloriecounter.view;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import com.google.android.material.button.MaterialButton;
import android.widget.EditText;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;
import androidx.lifecycle.ViewModelProvider;
import com.example.caloriecounter.model.FoodLog;
import java.util.Map;
import java.util.TreeMap;
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
    private boolean caloriesLoaded = false;
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
        loadCaloriesData();
        vm.loadLogs();

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (binding != null) {
            showWeightMode();
        }
    }
    private boolean caloriesMode = false;

    private void setupUI() {

        updateDateField(vm.getSelectedDate());

        // Переключение графиков
        binding.btnWeightMode.setOnClickListener(v -> showWeightMode());

        binding.btnCaloriesMode.setOnClickListener(v -> showCaloriesMode());

        // Выбор периода

        binding.btnWeek.setOnClickListener(v -> {
            highlightBtn(binding.btnWeek);

            if (caloriesMode) {
                binding.caloriesChart.setPeriod("week");
            } else {
                vm.setPeriod("week");
            }
        });

        binding.btnMonth.setOnClickListener(v -> {
            highlightBtn(binding.btnMonth);

            if (caloriesMode) {
                binding.caloriesChart.setPeriod("month");
            } else {
                vm.setPeriod("month");
            }
        });

        binding.btnYear.setOnClickListener(v -> {
            highlightBtn(binding.btnYear);

            if (caloriesMode) {
                binding.caloriesChart.setPeriod("year");
            } else {
                vm.setPeriod("year");
            }
        });

        // Выбор даты
        binding.etDate.setOnClickListener(v -> {
            LocalDate d = vm.getSelectedDate();

            DatePickerDialog dlg = new DatePickerDialog(this,
                    (view, y, m, day) -> {
                        LocalDate picked = LocalDate.of(y, m + 1, day);

                        if (!picked.isAfter(LocalDate.now())) {
                            vm.setSelectedDate(picked);
                            updateDateField(picked);
                        } else {
                            Toast.makeText(this,
                                    "Future dates not available",
                                    Toast.LENGTH_SHORT).show();
                        }
                    },
                    d.getYear(),
                    d.getMonthValue() - 1,
                    d.getDayOfMonth());

            dlg.getDatePicker().setMaxDate(System.currentTimeMillis());
            dlg.show();
        });

        // Сохранение веса
        binding.btnSaveWeight.setOnClickListener(v -> {
            String w = binding.etWeight.getText().toString().trim();

            if (w.isEmpty()) {
                Toast.makeText(this, "Введите вес", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double weight = Double.parseDouble(w.replace(",", "."));

                if (weight < 20 || weight > 300) {
                    Toast.makeText(this,
                            "Вес должен быть от 20 до 300 кг",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                vm.saveWeight(weight);

            } catch (NumberFormatException e) {
                Toast.makeText(this, "Некорректный вес", Toast.LENGTH_SHORT).show();
            }
        });

        // Редактирование и удаление веса
        binding.weightChart.setOnLogLongClickListener(log -> {
            new AlertDialog.Builder(this)
                    .setTitle("Вес за " + log.logDate)
                    .setMessage("Текущий вес: " + log.weight + " кг")
                    .setPositiveButton("Изменить", (d, w) -> {
                        EditText input = new EditText(this);

                        input.setInputType(
                                android.text.InputType.TYPE_CLASS_NUMBER |
                                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

                        input.setText(String.valueOf(log.weight));

                        new AlertDialog.Builder(this)
                                .setTitle("Новый вес (кг)")
                                .setView(input)
                                .setPositiveButton("Сохранить", (d2, w2) -> {
                                    try {
                                        double newWeight = Double.parseDouble(
                                                input.getText().toString().replace(",", "."));

                                        if (newWeight < 20 || newWeight > 300) {
                                            Toast.makeText(this,
                                                    "Вес должен быть от 20 до 300 кг",
                                                    Toast.LENGTH_SHORT).show();
                                            return;
                                        }

                                        vm.setSelectedDate(LocalDate.parse(log.logDate));
                                        vm.saveWeight(newWeight);

                                    } catch (Exception e) {
                                        Toast.makeText(this,
                                                "Некорректный вес",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .setNegativeButton("Отмена", null)
                                .show();
                    })
                    .setNegativeButton("Удалить", (d, w) -> {
                        repo.deleteWeightLog(log.id,
                                new SupabaseRepository.VoidCallback() {
                                    @Override
                                    public void onSuccess() {
                                        vm.loadLogs();
                                    }

                                    @Override
                                    public void onError(String m) {
                                        Toast.makeText(
                                                WeightTrackerActivity.this,
                                                m,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    })
                    .setNeutralButton("Отмена", null)
                    .show();
        });

        // При открытии показываем вес
        showWeightMode();
    }



    private void showWeightMode() {
        caloriesMode = false;

        binding.weightChart.setVisibility(View.VISIBLE);
        binding.caloriesChart.setVisibility(View.GONE);
        binding.weightInputContainer.setVisibility(View.VISIBLE);

        binding.btnWeek.setVisibility(View.VISIBLE);
        binding.btnMonth.setVisibility(View.VISIBLE);
        binding.btnYear.setVisibility(View.VISIBLE);

        binding.btnWeek.setText("Неделя");
        binding.btnMonth.setText("Месяц");
        binding.btnYear.setText("Год");

        highlightModeButton(binding.btnWeightMode);
        highlightBtn(binding.btnWeek);
    }

    private void showCaloriesMode() {
        caloriesMode = true;

        binding.weightChart.setVisibility(View.GONE);
        binding.caloriesChart.setVisibility(View.VISIBLE);
        binding.weightInputContainer.setVisibility(View.GONE);

        binding.btnWeek.setVisibility(View.VISIBLE);
        binding.btnMonth.setVisibility(View.VISIBLE);
        binding.btnYear.setVisibility(View.VISIBLE);

        binding.btnWeek.setText("Неделя");
        binding.btnMonth.setText("Месяц");
        binding.btnYear.setText("Год");

        binding.caloriesChart.setPeriod("week");

        highlightModeButton(binding.btnCaloriesMode);
        highlightBtn(binding.btnWeek);

        if (!caloriesLoaded) {
            loadCaloriesData();
        }
    }

    private void highlightModeButton(MaterialButton active) {
        MaterialButton weight = binding.btnWeightMode;
        MaterialButton calories = binding.btnCaloriesMode;

        int activeColor = android.graphics.Color.parseColor("#7563F6");
        int inactiveColor = android.graphics.Color.parseColor("#F0EFF7");
        int activeText = android.graphics.Color.WHITE;
        int inactiveText = android.graphics.Color.parseColor("#777B8D");

        weight.setCheckable(false);
        calories.setCheckable(false);

        weight.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        active == weight ? activeColor : inactiveColor
                )
        );

        calories.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        active == calories ? activeColor : inactiveColor
                )
        );

        weight.setTextColor(active == weight ? activeText : inactiveText);
        calories.setTextColor(active == calories ? activeText : inactiveText);
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

    private void highlightBtn(MaterialButton active) {
        String accent = ThemeUtils.getAccent(this);
        int accentColor = android.graphics.Color.parseColor(accent);
        int grayColor = android.graphics.Color.parseColor("#E0E0E0");
        int inactiveText = android.graphics.Color.parseColor("#777B8D");

        MaterialButton[] buttons = {
                binding.btnWeek,
                binding.btnMonth,
                binding.btnYear
        };

        for (MaterialButton button : buttons) {
            boolean selected = button == active;

            button.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            selected ? accentColor : grayColor
                    )
            );

            button.setTextColor(
                    selected
                            ? android.graphics.Color.WHITE
                            : inactiveText
            );
        }
    }


    private void loadCaloriesData() {
        binding.progressBar.setVisibility(View.VISIBLE);

        repo.fetchAllFoodLogs(new SupabaseRepository.LogListCallback() {
            @Override
            public void onSuccess(java.util.List<FoodLog> logs) {
                Map<String, Integer> dailyCalories = new TreeMap<>();

                if (logs != null) {
                    for (FoodLog log : logs) {
                        if (log == null ||
                                log.logDate == null ||
                                log.logDate.isEmpty()) {
                            continue;
                        }

                        int calories = Math.max(0, log.totalCalories);

                        String date = log.logDate.trim();

                        if (date.length() >= 10) {
                            date = date.substring(0, 10);
                        }

                        try {
                            LocalDate.parse(date);

                            if (calories > 0) {
                                dailyCalories.merge(date, calories, Integer::sum);
                            }
                        } catch (Exception ignored) {
                            // Некорректную дату пропускаем
                        }
                    }
                }

                binding.caloriesChart.setData(dailyCalories);
                caloriesLoaded = true;
                binding.progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onError(String message) {
                binding.progressBar.setVisibility(View.GONE);

                Toast.makeText(
                        WeightTrackerActivity.this,
                        "Не удалось загрузить калории: " + message,
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }
}