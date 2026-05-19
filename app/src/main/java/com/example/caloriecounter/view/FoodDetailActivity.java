package com.example.caloriecounter.view;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.caloriecounter.databinding.ActivityFoodDetailBinding;
import com.example.caloriecounter.model.FoodLog;
import com.example.caloriecounter.viewmodel.FoodSearchViewModel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FoodDetailActivity extends AppCompatActivity {
    private ActivityFoodDetailBinding binding;
    private FoodSearchViewModel vm;

    private int baseCal, basePro, baseFat, baseCarb, baseSugar, baseFiber;
    private String foodName, foodId, meal, userId, targetDate;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFoodDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = new ViewModelProvider(this).get(FoodSearchViewModel.class);

        foodName = getIntent().getStringExtra("foodName");
        foodId = getIntent().getStringExtra("foodId");
        meal = getIntent().getStringExtra("meal");
        userId = getIntent().getStringExtra("userId");

        String dateFromIntent = getIntent().getStringExtra("selected_date");
        targetDate = (dateFromIntent != null && !dateFromIntent.isEmpty())
                ? dateFromIntent
                : new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());

        Log.d("DEBUG_DATE", "Сохраняю в дату: " + targetDate + " | foodName: " + foodName);

        baseCal = getIntent().getIntExtra("calories", 0);
        basePro = getIntent().getIntExtra("protein", 0);
        baseFat = getIntent().getIntExtra("fat", 0);
        baseCarb = getIntent().getIntExtra("carbs", 0);
        baseSugar = getIntent().getIntExtra("sugar", 0);
        baseFiber = getIntent().getIntExtra("fiber", 0);

        binding.tvFoodName.setText(foodName);
        binding.tvPer100.setText(String.format("%d ккал / 100 г", baseCal));
        binding.etGrams.setText("100");

        updateCalculations(100);

        binding.etGrams.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                try {
                    int grams = Integer.parseInt(s.toString());
                    updateCalculations(grams);
                } catch (NumberFormatException e) {
                    updateCalculations(0);
                }
            }
        });

        binding.btnCancel.setOnClickListener(v -> finish());

        binding.btnSave.setOnClickListener(v -> {
            String gramsStr = binding.etGrams.getText().toString().trim();
            if (gramsStr.isEmpty()) {
                return;
            }

            int grams;
            try {
                grams = Integer.parseInt(gramsStr);
            } catch (NumberFormatException e) {
                return;
            }

            saveLog(grams);
        });
    }

    private void updateCalculations(int grams) {
        int totalCal = baseCal * grams / 100;
        int totalPro = basePro * grams / 100;
        int totalFat = baseFat * grams / 100;
        int totalCarb = baseCarb * grams / 100;
        int totalSugar = baseSugar * grams / 100;
        int totalFiber = baseFiber * grams / 100;

        binding.tvCalculated.setText(String.format("%d ккал / %d г", totalCal, grams));
        binding.tvMacrosCalc.setText(String.format(
                "Белки: %d г | Жиры: %d г | Углеводы: %d г | Сахар: %d г | Клетчатка: %d г",
                totalPro, totalFat, totalCarb, totalSugar, totalFiber
        ));
    }

    private void saveLog(int grams) {
        FoodLog log = new FoodLog();
        log.userId = userId;
        log.foodId = foodId;
        log.foodName = foodName;
        log.mealType = meal;
        log.grams = grams;
        log.logDate = targetDate;
        log.totalCalories = baseCal * grams / 100;
        log.totalProtein = basePro * grams / 100;
        log.totalFat = baseFat * grams / 100;
        log.totalCarbs = baseCarb * grams / 100;
        log.totalSugar = baseSugar * grams / 100;
        log.totalFiber = baseFiber * grams / 100;

        binding.btnSave.setEnabled(false);
        binding.btnSave.setText("Сохранение...");

        vm.saveLog(log, targetDate, new com.example.caloriecounter.repository.SupabaseRepository.VoidCallback() {
            @Override public void onSuccess() {
                setResult(RESULT_OK);
                finish();
            }
            @Override public void onError(String m) {
                runOnUiThread(() -> {
                    binding.btnSave.setEnabled(true);
                    binding.btnSave.setText("Сохранить");
                });
            }
        });
    }
}