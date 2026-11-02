package com.example.caloriecounter.view;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.example.caloriecounter.R;
import com.example.caloriecounter.databinding.ActivityWeightTrackerBinding;
import com.example.caloriecounter.model.FoodLog;
import com.example.caloriecounter.model.WeightLog;
import com.example.caloriecounter.repository.SupabaseRepository;
import com.example.caloriecounter.viewmodel.WeightTrackerViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class WeightTrackerActivity extends BaseActivity {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final double MIN_WEIGHT = 20;
    private static final double MAX_WEIGHT = 300;

    private static final String PERIOD_WEEK = "week";
    private static final String PERIOD_MONTH = "month";
    private static final String PERIOD_YEAR = "year";

    private ActivityWeightTrackerBinding binding;
    private WeightTrackerViewModel vm;
    private SupabaseRepository repo;

    private boolean caloriesLoaded;
    private boolean caloriesLoading;
    private boolean caloriesMode;

    private String selectedPeriod = PERIOD_WEEK;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        binding = ActivityWeightTrackerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String userId = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString("user_id", "");

        vm = new WeightTrackerViewModel(getApplication(), userId);
        repo = new SupabaseRepository(this);

        if (state != null) {
            caloriesMode = state.getBoolean("calories_mode", false);
            selectedPeriod = state.getString("period", PERIOD_WEEK);

            String date = state.getString("selected_date");
            if (date != null) {
                try {
                    vm.setSelectedDate(LocalDate.parse(date));
                } catch (Exception ignored) {
                }
            }
        }

        setupUI();
        setupBottomNavigation();
        observeData();

        vm.setPeriod(selectedPeriod);
        loadCaloriesData();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (binding == null) {
            return;
        }

        if (caloriesMode) {
            showCaloriesMode();
        } else {
            showWeightMode();
        }

        binding.weightChart.invalidate();
        binding.caloriesChart.invalidate();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean("calories_mode", caloriesMode);
        outState.putString("period", selectedPeriod);
        outState.putString("selected_date", vm.getSelectedDate().toString());
    }

    private void setupUI() {
        updateDateField(vm.getSelectedDate());

        binding.btnWeightMode.setOnClickListener(v -> showWeightMode());
        binding.btnCaloriesMode.setOnClickListener(v -> showCaloriesMode());

        binding.btnWeek.setOnClickListener(v -> changePeriod(PERIOD_WEEK));
        binding.btnMonth.setOnClickListener(v -> changePeriod(PERIOD_MONTH));
        binding.btnYear.setOnClickListener(v -> changePeriod(PERIOD_YEAR));

        binding.etDate.setOnClickListener(v -> pickDate());
        binding.btnSaveWeight.setOnClickListener(v -> saveWeightFromInput());

        binding.weightChart.setOnLogLongClickListener(this::showWeightActions);

        if (caloriesMode) {
            showCaloriesMode();
        } else {
            showWeightMode();
        }
    }

    private void changePeriod(String period) {
        selectedPeriod = period;
        highlightPeriodButton();

        if (caloriesMode) {
            binding.caloriesChart.setPeriod(period);
        } else {
            vm.setPeriod(period);
        }
    }

    private void pickDate() {
        LocalDate current = vm.getSelectedDate();

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, day) -> {
                    LocalDate picked = LocalDate.of(year, month + 1, day);

                    if (picked.isAfter(LocalDate.now())) {
                        toast("Будущие даты недоступны");
                        return;
                    }

                    vm.setSelectedDate(picked);
                    updateDateField(picked);
                },
                current.getYear(),
                current.getMonthValue() - 1,
                current.getDayOfMonth()
        );

        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private double parseWeight(String text) {
        double value = Double.parseDouble(text.trim().replace(',', '.'));

        if (!Double.isFinite(value) || value < MIN_WEIGHT || value > MAX_WEIGHT) {
            throw new IllegalArgumentException();
        }

        return value;
    }

    private void saveWeightFromInput() {
        String text = binding.etWeight.getText().toString().trim();

        if (text.isEmpty()) {
            binding.etWeight.setError("Введите вес");
            return;
        }

        try {
            double weight = parseWeight(text);
            binding.etWeight.setError(null);
            vm.saveWeight(weight);
        } catch (Exception ex) {
            binding.etWeight.setError("Вес должен быть от 20 до 300 кг");
        }
    }

    private void showWeightActions(WeightLog log) {
        if (log == null) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Вес за " + log.logDate)
                .setMessage("Записанный вес: " + log.weight + " кг")
                .setPositiveButton("Изменить", (dialog, which) -> editWeight(log))
                .setNegativeButton("Удалить", (dialog, which) -> confirmDeleteWeight(log))
                .setNeutralButton("Отмена", null)
                .show();
    }

    private void editWeight(WeightLog log) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setSingleLine(true);
        input.setText(String.valueOf(log.weight));
        input.setSelection(input.getText().length());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Новый вес, кг")
                .setView(input)
                .setPositiveButton("Сохранить", null)
                .setNegativeButton("Отмена", null)
                .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> handleEditWeightSave(dialog, input, log))
        );

        dialog.show();
    }

    private void handleEditWeightSave(AlertDialog dialog, EditText input, WeightLog log) {
        double newWeight;

        try {
            newWeight = parseWeight(input.getText().toString());
        } catch (Exception ex) {
            input.setError("Вес должен быть от 20 до 300 кг");
            return;
        }

        LocalDate logDate;

        try {
            logDate = LocalDate.parse(log.logDate);
        } catch (Exception ex) {
            toast("Не удалось определить дату записи");
            return;
        }

        if (logDate.isAfter(LocalDate.now())) {
            toast("Будущие даты недоступны");
            return;
        }

        vm.setSelectedDate(logDate);
        updateDateField(logDate);
        vm.saveWeight(newWeight);
        dialog.dismiss();
    }

    private void confirmDeleteWeight(WeightLog log) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить запись?")
                .setMessage(log.logDate + " — " + log.weight + " кг")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, which) -> deleteWeight(log))
                .show();
    }

    private void deleteWeight(WeightLog log) {
        if (log.id == null || log.id.isEmpty()) {
            toast("Не найден идентификатор записи");
            return;
        }

        repo.deleteWeightLog(log.id, new SupabaseRepository.VoidCallback() {
            @Override
            public void onSuccess() {
                if (!alive()) {
                    return;
                }

                vm.loadLogs();
                toast("Запись удалена");
            }

            @Override
            public void onError(String message) {
                if (alive()) {
                    toast(message);
                }
            }
        });
    }

    private void showWeightMode() {
        caloriesMode = false;

        binding.weightChart.setVisibility(View.VISIBLE);
        binding.caloriesChart.setVisibility(View.GONE);
        binding.weightInputContainer.setVisibility(View.VISIBLE);

        showPeriodButtons();
        highlightModeButton(binding.btnWeightMode);
        highlightPeriodButton();

        if (!selectedPeriod.equals(vm.getCurrentPeriod())) {
            vm.setPeriod(selectedPeriod);
        }
    }

    private void showCaloriesMode() {
        caloriesMode = true;

        binding.weightChart.setVisibility(View.GONE);
        binding.caloriesChart.setVisibility(View.VISIBLE);
        binding.weightInputContainer.setVisibility(View.GONE);

        showPeriodButtons();
        binding.caloriesChart.setPeriod(selectedPeriod);

        highlightModeButton(binding.btnCaloriesMode);
        highlightPeriodButton();

        if (!caloriesLoaded && !caloriesLoading) {
            loadCaloriesData();
        }
    }

    private void showPeriodButtons() {
        binding.btnWeek.setVisibility(View.VISIBLE);
        binding.btnMonth.setVisibility(View.VISIBLE);
        binding.btnYear.setVisibility(View.VISIBLE);

        binding.btnWeek.setText("Неделя");
        binding.btnMonth.setText("Месяц");
        binding.btnYear.setText("Год");
    }

    private void highlightModeButton(MaterialButton active) {
        styleButton(binding.btnWeightMode, active == binding.btnWeightMode);
        styleButton(binding.btnCaloriesMode, active == binding.btnCaloriesMode);
    }

    private void highlightPeriodButton() {
        styleButton(binding.btnWeek, PERIOD_WEEK.equals(selectedPeriod));
        styleButton(binding.btnMonth, PERIOD_MONTH.equals(selectedPeriod));
        styleButton(binding.btnYear, PERIOD_YEAR.equals(selectedPeriod));
    }

    private void styleButton(MaterialButton button, boolean selected) {
        int accent = ActivityPalette.accent(this);

        button.setCheckable(false);
        button.setBackgroundTintList(ColorStateList.valueOf(
                selected ? accent : ActivityPalette.surface(this)));
        button.setStrokeColor(ColorStateList.valueOf(accent));
        button.setTextColor(selected
                ? ActivityPalette.onAccent(this)
                : ActivityPalette.muted(this));
    }

    private void observeData() {
        vm.getLogs().observe(this, binding.weightChart::setData);

        vm.isLoading().observe(this, loading ->
                binding.progressBar.setVisibility(
                        Boolean.TRUE.equals(loading) || caloriesLoading
                                ? View.VISIBLE
                                : View.GONE));

        vm.getError().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                toast(error);
            }
        });

        vm.getSaveSuccess().observe(this, success -> {
            if (Boolean.TRUE.equals(success)) {
                binding.etWeight.setText("");
            }
        });
    }

    private void setupBottomNavigation() {
        BottomNavigationView nav = binding.bottomNavigation;
        nav.setSelectedItemId(R.id.nav_weight);

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_weight) {
                return true;
            }

            Class<?> target;

            if (id == R.id.nav_training) {
                target = TrainingActivity.class;
            } else if (id == R.id.nav_food) {
                target = MainActivity.class;
            } else if (id == R.id.nav_diets) {
                target = DietListActivity.class;
            } else if (id == R.id.nav_water) {
                target = WaterTrackerActivity.class;
            } else {
                return false;
            }

            startActivity(new Intent(this, target));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return false;
        });
    }

    private void updateDateField(LocalDate date) {
        binding.etDate.setText(date.format(DATE_FORMAT));
    }

    private void loadCaloriesData() {
        if (caloriesLoading) {
            return;
        }

        caloriesLoading = true;
        binding.progressBar.setVisibility(View.VISIBLE);

        repo.fetchAllFoodLogs(new SupabaseRepository.LogListCallback() {
            @Override
            public void onSuccess(List<FoodLog> logs) {
                if (!alive()) {
                    return;
                }

                Map<String, Integer> dailyCalories = new TreeMap<>();

                if (logs != null) {
                    for (FoodLog log : logs) {
                        if (log == null
                                || log.logDate == null
                                || log.logDate.trim().isEmpty()) {
                            continue;
                        }

                        String date = log.logDate.trim();

                        if (date.length() >= 10) {
                            date = date.substring(0, 10);
                        }

                        try {
                            LocalDate.parse(date);

                            dailyCalories.merge(
                                    date,
                                    Math.max(0, log.totalCalories),
                                    Integer::sum);
                        } catch (Exception ignored) {
                        }
                    }
                }

                binding.caloriesChart.setData(dailyCalories);
                binding.caloriesChart.setPeriod(selectedPeriod);

                caloriesLoaded = true;
                caloriesLoading = false;
                updateLoadingIndicator();
            }

            @Override
            public void onError(String message) {
                if (!alive()) {
                    return;
                }

                caloriesLoading = false;
                updateLoadingIndicator();
                toast("Не удалось загрузить калории: " + message);
            }
        });
    }

    private void updateLoadingIndicator() {
        boolean weightLoading = Boolean.TRUE.equals(vm.isLoading().getValue());

        binding.progressBar.setVisibility(
                caloriesLoading || weightLoading
                        ? View.VISIBLE
                        : View.GONE);
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}