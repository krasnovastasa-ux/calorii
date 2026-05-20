package com.example.caloriecounter.view;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.example.caloriecounter.databinding.ActivityRecipeAddBinding;
import com.example.caloriecounter.model.FoodLog;
import com.example.caloriecounter.model.Recipe;
import com.example.caloriecounter.repository.SupabaseRepository;
import java.util.List;

public class RecipeAddActivity extends BaseActivity {
    private ActivityRecipeAddBinding binding;
    private SupabaseRepository repo;
    private String recipeId, meal, userId, selectedDate, recipeName;
    private List<Recipe.RecipeFood> recipeFoods;
    private double totalRecipeWeight = 0;
    private int recipeServings = 1;
    private boolean isGramsMode = true;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecipeAddBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repo = new SupabaseRepository(this);

        recipeId = getIntent().getStringExtra("recipe_id");
        meal = getIntent().getStringExtra("meal");
        userId = getIntent().getStringExtra("userId");
        selectedDate = getIntent().getStringExtra("selected_date");
        recipeName = getIntent().getStringExtra("recipe_name");
        recipeServings = getIntent().getIntExtra("servings", 1);

        binding.tvRecipeName.setText(recipeName);
        binding.etValue.setText("1");

        binding.rgUnit.setOnCheckedChangeListener((group, checkedId) -> {
            isGramsMode = checkedId == com.example.caloriecounter.R.id.rbGrams;
            binding.etValue.setHint(isGramsMode ? "Вес в граммах" : "Количество порций");
            updateCalculation();
        });

        binding.etValue.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateCalculation(); }
        });

        binding.btnCancel.setOnClickListener(v -> finish());
        binding.btnSave.setOnClickListener(v -> saveToDiary());

        loadRecipeFoods();
    }

    private void loadRecipeFoods() {
        binding.tvPerServing.setText("Загрузка...");
        repo.fetchRecipeFoods("eq." + recipeId, new SupabaseRepository.RecipeFoodListCallback() {
            @Override public void onSuccess(List<Recipe.RecipeFood> list) {
                recipeFoods = list;
                totalRecipeWeight = 0;
                for (Recipe.RecipeFood rf : list) totalRecipeWeight += rf.grams;
                if (totalRecipeWeight <= 0) totalRecipeWeight = 1;
                updateCalculation();
            }
            @Override public void onError(String m) {
                binding.tvPerServing.setText("~0 ккал / 1 порция");
            }
        });
    }

    private void updateCalculation() {
        if (recipeFoods == null || recipeFoods.isEmpty()) return;
        try {
            double fullCal = 0, fullPro = 0, fullFat = 0, fullCarb = 0;
            double totalWeight = 0;
            for (Recipe.RecipeFood rf : recipeFoods) {
                double g = rf.grams > 0 ? rf.grams : 0;
                totalWeight += g;
                double scale = g / 100.0;
                fullCal += rf.caloriesPer100g * scale;
                fullPro += rf.proteinPer100g * scale;
                fullFat += rf.fatPer100g * scale;
                fullCarb += rf.carbsPer100g * scale;
            }
            if (totalWeight <= 0) totalWeight = 1;

            int servings = recipeServings > 0 ? recipeServings : 1;
            int calPerServing = (int) Math.round(fullCal / servings);
            binding.tvPerServing.setText(String.format("~%d ккал / 1 порция", calPerServing));

            String inputStr = binding.etValue.getText().toString().trim();
            double val = inputStr.isEmpty() ? 1.0 : Double.parseDouble(inputStr);
            if (val <= 0) val = 1.0;

            double resCal, resPro, resFat, resCarb;
            String unit;
            if (isGramsMode) {
                resCal = (fullCal / totalWeight) * val;
                resPro = (fullPro / totalWeight) * val;
                resFat = (fullFat / totalWeight) * val;
                resCarb = (fullCarb / totalWeight) * val;
                unit = "г";
            } else {
                resCal = (fullCal / servings) * val;
                resPro = (fullPro / servings) * val;
                resFat = (fullFat / servings) * val;
                resCarb = (fullCarb / servings) * val;
                unit = "порц.";
            }

            int c = (int) Math.round(resCal);
            int p = (int) Math.round(resPro);
            int f = (int) Math.round(resFat);
            int cb = (int) Math.round(resCarb);
            binding.tvCalculated.setText(String.format("%d ккал / %.0f %s", c, val, unit));
            binding.tvMacrosCalc.setText(String.format("Белки: %d г | Жиры: %d г | Углеводы: %d г", p, f, cb));
        } catch (Exception e) {
            binding.tvPerServing.setText("~0 ккал / 1 порция");
            binding.tvCalculated.setText("0 ккал");
            binding.tvMacrosCalc.setText("Белки: 0 г | Жиры: 0 г | Углеводы: 0 г");
        }
    }

    private void saveToDiary() {
        String valStr = binding.etValue.getText().toString().trim();

        if (valStr.isEmpty()) {
            binding.etValue.setError("Введите значение");
            binding.etValue.requestFocus();
            return;
        }

        double entered;
        try {
            entered = Double.parseDouble(valStr);
        } catch (NumberFormatException e) {
            binding.etValue.setError("Некорректное число");
            binding.etValue.requestFocus();
            return;
        }

        if (entered < 1 || entered > 10000) {
            binding.etValue.setError("Допустимо: 1-10000");
            binding.etValue.requestFocus();
            return;
        }

        if (recipeFoods == null || recipeFoods.isEmpty()) {
            return;
        }

        try {
            String inputStr = binding.etValue.getText().toString().trim();
            double inputVal = inputStr.isEmpty() ? 1.0 : Double.parseDouble(inputStr);
            if (inputVal <= 0) { return; }

            double fullCal = 0, fullPro = 0, fullFat = 0, fullCarb = 0, totalWeight = 0;
            for (Recipe.RecipeFood rf : recipeFoods) {
                double g = rf.grams > 0 ? rf.grams : 0;
                totalWeight += g;
                double scale = g / 100.0;
                fullCal += rf.caloriesPer100g * scale;
                fullPro += rf.proteinPer100g * scale;
                fullFat += rf.fatPer100g * scale;
                fullCarb += rf.carbsPer100g * scale;
            }
            if (totalWeight <= 0) totalWeight = 1;

            double scale;
            if (isGramsMode) {
                scale = inputVal / totalWeight;
            } else {
                int servings = recipeServings > 0 ? recipeServings : 1;
                scale = (totalWeight / servings * inputVal) / totalWeight;
            }

            double dGrams = 0, dCal = 0, dPro = 0, dFat = 0, dCarb = 0;
            for (Recipe.RecipeFood rf : recipeFoods) {
                dGrams += rf.grams * scale;
                dCal += rf.caloriesPer100g * scale;
                dPro += rf.proteinPer100g * scale;
                dFat += rf.fatPer100g * scale;
                dCarb += rf.carbsPer100g * scale;
            }

            final int finalCalories = (int) Math.round(dCal);
            final int finalGrams = (int) Math.round(dGrams);
            final int finalProtein = (int) Math.round(dPro);
            final int finalFat = (int) Math.round(dFat);
            final int finalCarbs = (int) Math.round(dCarb);

            FoodLog log = new FoodLog();
            log.userId = userId;
            log.foodId = null;
            log.foodName = recipeName;
            log.mealType = meal;
            log.logDate = selectedDate;
            log.grams = finalGrams;
            log.totalCalories = finalCalories;
            log.totalProtein = finalProtein;
            log.totalFat = finalFat;
            log.totalCarbs = finalCarbs;
            log.totalSugar = 0;
            log.totalFiber = 0;

            binding.btnSave.setEnabled(false);
            binding.btnSave.setText("Сохранение...");

            repo.addLog(log, selectedDate, new SupabaseRepository.VoidCallback() {
                @Override public void onSuccess() {
                    runOnUiThread(() -> {
                        setResult(RESULT_OK);
                        finish();
                    });
                }
                @Override public void onError(String m) {
                    runOnUiThread(() -> {
                        binding.btnSave.setEnabled(true);
                        binding.btnSave.setText("Сохранить");
                    });
                }
            });
        } catch (Exception e) {
            Log.e("RECIPE_ADD", "Ошибка: " + e.getMessage());
        }
    }
}