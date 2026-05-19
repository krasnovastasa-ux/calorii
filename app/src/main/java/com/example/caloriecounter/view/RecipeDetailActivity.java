package com.example.caloriecounter.view;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.content.Intent;
import android.graphics.Color;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.caloriecounter.databinding.ActivityRecipeDetailBinding;
import com.example.caloriecounter.model.Recipe;
import com.example.caloriecounter.model.UserRecipe;
import com.example.caloriecounter.repository.SupabaseRepository;
import java.util.List;

public class RecipeDetailActivity extends BaseActivity {
    private static final int REQUEST_CODE_MY_RECIPE = 100;
    private static final String TAG = "RECIPE_DETAIL";
    private ActivityRecipeDetailBinding binding;
    private String recipeId, meal, userId, selectedDate;
    private boolean isUserRecipe = false;
    private SupabaseRepository repo;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecipeDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repo = new SupabaseRepository(this);

        recipeId = getIntent().getStringExtra("recipe_id");
        meal = getIntent().getStringExtra("meal");
        userId = getIntent().getStringExtra("userId");
        selectedDate = getIntent().getStringExtra("selected_date");

        if (recipeId == null) { finish(); return; }

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnAddRecipe.setOnClickListener(v -> addRecipeToLog());

        loadRecipeSmart();
    }

    private void loadRecipeSmart() {
        Log.d(TAG, " Ищем рецепт " + recipeId);
        repo.fetchUserRecipeById(recipeId, new SupabaseRepository.UserRecipeCallback() {
            @Override public void onSuccess(UserRecipe ur) {
                Log.d(TAG, " Загружен из user_recipes");
                isUserRecipe = true;
                Recipe r = new Recipe();
                r.id = ur.id; r.name = ur.name; r.description = ur.instructions;
                r.prepTime = ur.prepTime; r.cookTime = ur.cookTime; r.servings = ur.servings;
                renderRecipe(r);
                loadUserRecipeIngredients(recipeId);
            }
            @Override public void onError(String msg) {
                Log.d(TAG, " Не user_recipe, ищем в system");
                repo.fetchRecipeById(recipeId, new SupabaseRepository.RecipeCallback() {
                    @Override public void onSuccess(Recipe r) {
                        Log.d(TAG, " Загружен из recipes");
                        isUserRecipe = false;
                        renderRecipe(r);
                        loadRecipeIngredients(recipeId);
                    }
                    @Override public void onError(String err) {
                        Log.e(TAG, " Рецепт не найден: " + err);
                        finish();
                    }
                });
            }
        });
    }

    private void renderRecipe(Recipe r) {
        binding.tvName.setText(r.name != null ? r.name : "Без названия");
        binding.tvTime.setText(String.format("%d мин подготовки | %d мин готовки | %d порций", r.prepTime, r.cookTime, r.servings));
        String inst = r.description != null ? r.description.trim() : "";
        binding.tvInstructions.setText(inst.isEmpty() ? "Нет описания" : inst);
        binding.tvCalories.setText("Расчёт КБЖУ...");
    }

    private void loadRecipeIngredients(String rid) {
        repo.fetchRecipeFoods("eq." + rid, new SupabaseRepository.RecipeFoodListCallback() {
            @Override public void onSuccess(List<Recipe.RecipeFood> foods) {
                calculateAndShow(foods);
                applyAccentToContent();
            }
            @Override public void onError(String msg) { Log.e(TAG, "Err sys: " + msg); showEmpty(); }
        });
    }

    private void loadUserRecipeIngredients(String rid) {
        Log.d(TAG, "Загружаем user_recipe_ingredients для " + rid);
        repo.fetchUserRecipeIngredients("eq." + rid, new SupabaseRepository.RecipeFoodListCallback() {
            @Override public void onSuccess(List<Recipe.RecipeFood> foods) {
                Log.d(TAG, " Получено ингредиентов: " + (foods != null ? foods.size() : 0));
                calculateAndShow(foods);
                applyAccentToContent();
            }
            @Override public void onError(String msg) {
                Log.e(TAG, " Ошибка загрузки user_recipe_ingredients: " + msg);
                showEmpty();
            }
        });
    }

    private void calculateAndShow(List<Recipe.RecipeFood> foods) {
        binding.llIngredients.removeAllViews();
        binding.llFoods.removeAllViews();
        if (foods == null || foods.isEmpty()) { showEmpty(); return; }

        int cal=0, p=0, f=0, c=0;
        for (Recipe.RecipeFood ing : foods) {
            cal += ing.getTotalCalories(); p += ing.getTotalProtein();
            f += ing.getTotalFat(); c += ing.getTotalCarbs();
            binding.llIngredients.addView(tv("• " + (ing.foodName!=null?ing.foodName:"Продукт") + " — " + ing.grams + "г (" + ing.getTotalCalories() + " ккал)"));
        }

        binding.tvCalories.setText(String.format("%d ккал | Б: %dг | Ж: %dг | У: %dг", cal, p, f, c));

        TextView t = new TextView(this);
        t.setText("Итого: " + cal + " ккал | Б:" + p + "г Ж:" + f + "г У:" + c + "г");
        t.setPadding(12,10,12,10);
        t.setTextColor(android.graphics.Color.parseColor("#6C63FF"));
        t.setTag("accent");
        t.setTextSize(15);
        binding.llFoods.addView(t);
    }

    private void showEmpty() {
        binding.tvCalories.setText("0 ккал | Б: 0г | Ж: 0г | У: 0г");
        binding.llIngredients.addView(tv("Нет ингредиентов"));

        TextView t = new TextView(this);
        t.setText("Итого: 0 ккал");
        t.setPadding(12,10,12,10);
        t.setTextColor(android.graphics.Color.parseColor("#6C63FF"));
        t.setTag("accent");
        binding.llFoods.addView(t);

        applyAccentToContent();
    }
    private void applyAccentToContent() {
        try {
            String accent = com.example.caloriecounter.utils.ThemeUtils.getAccent(this);
            com.example.caloriecounter.utils.ThemeUtils.applyAccent(binding.tvCalories, accent);
            com.example.caloriecounter.utils.ThemeUtils.applyAccent(binding.llFoods, accent);
        } catch (Exception ignored) {}
    }

    private TextView tv(String s) { TextView t = new TextView(this); t.setText(s); t.setPadding(12,6,12,6); return t; }

    private void addRecipeToLog() {
        Intent i = new Intent(RecipeDetailActivity.this, FoodPortionCalculatorActivity.class);
        i.putExtra("recipe_id", recipeId);
        i.putExtra("recipe_name", binding.tvName.getText().toString());
        i.putExtra("meal_type", meal);
        i.putExtra("userId", userId);
        i.putExtra("selected_date", selectedDate);
        i.putExtra("is_user_recipe", isUserRecipe);
        startActivityForResult(i, 101);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == RESULT_OK) {
            setResult(RESULT_OK);
            finish();
        }
    }

    private void finishSuccess() {
        setResult(RESULT_OK);
        finish();
    }

    private void showPortionDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setBackgroundColor(0xFFF5F5F5);
        scrollView.setPadding(72, 48, 72, 48);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollView.addView(root);

        android.widget.TextView tvName = new android.widget.TextView(this);
        tvName.setText(binding.tvName.getText());
        tvName.setTextSize(24);
        tvName.setTextColor(0xFF212121);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setPadding(0, 0, 0, 72);
        root.addView(tvName);

        android.widget.TextView labelPerServing = new android.widget.TextView(this);
        labelPerServing.setText("На 1 порцию:");
        labelPerServing.setTextSize(14);
        labelPerServing.setTextColor(0xFF757575);
        labelPerServing.setPadding(0, 0, 0, 12);
        root.addView(labelPerServing);

        android.widget.TextView tvPerServing = new android.widget.TextView(this);
        tvPerServing.setText("~0 ккал / 1 порция");
        tvPerServing.setTextSize(16);
        tvPerServing.setTextColor(0xFF212121);
        tvPerServing.setTypeface(null, android.graphics.Typeface.BOLD);
        tvPerServing.setPadding(0, 0, 0, 72);
        root.addView(tvPerServing);

        android.widget.TextView labelTotal = new android.widget.TextView(this);
        labelTotal.setText("Итого:");
        labelTotal.setTextSize(14);
        labelTotal.setTextColor(0xFF757575);
        labelTotal.setPadding(0, 0, 0, 12);
        root.addView(labelTotal);

        android.widget.TextView tvCalc = new android.widget.TextView(this);
        tvCalc.setText("0 ккал");
        tvCalc.setTextSize(18);
        tvCalc.setTextColor(0xFF6200EE);
        tvCalc.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCalc.setPadding(0, 0, 0, 24);
        root.addView(tvCalc);

        android.widget.TextView tvMacros = new android.widget.TextView(this);
        tvMacros.setText("Белки: 0 г | Жиры: 0 г | Углеводы: 0 г");
        tvMacros.setTextSize(14);
        tvMacros.setTextColor(0xFF757575);
        tvMacros.setPadding(0, 0, 0, 96);
        tvMacros.setLineSpacing(12, 1);
        root.addView(tvMacros);

        com.google.android.material.textfield.TextInputLayout inputLayout =
                new com.google.android.material.textfield.TextInputLayout(this);
        inputLayout.setHint("Введите значение");
        inputLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        inputLayout.setPadding(0, 0, 0, 48);
        root.addView(inputLayout);

        com.google.android.material.textfield.TextInputEditText etValue =
                new com.google.android.material.textfield.TextInputEditText(this);
        etValue.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etValue.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(5)});
        inputLayout.addView(etValue);

        android.widget.TextView labelUnit = new android.widget.TextView(this);
        labelUnit.setText("Единица измерения:");
        labelUnit.setTextSize(14);
        labelUnit.setTextColor(0xFF757575);
        labelUnit.setPadding(0, 0, 0, 24);
        root.addView(labelUnit);

        android.widget.RadioGroup rgUnit = new android.widget.RadioGroup(this);
        rgUnit.setOrientation(LinearLayout.HORIZONTAL);
        rgUnit.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        rgUnit.setPadding(0, 0, 0, 96);
        root.addView(rgUnit);

        android.widget.RadioButton rbGrams = new android.widget.RadioButton(this);
        rbGrams.setText("Граммы");
        rbGrams.setChecked(true);
        rbGrams.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF6200EE));
        rbGrams.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        android.widget.RadioButton rbServings = new android.widget.RadioButton(this);
        rbServings.setText("Порции");
        rbServings.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF6200EE));
        rbServings.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        rgUnit.addView(rbGrams);
        rgUnit.addView(rbServings);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(btnRow);

        com.google.android.material.button.MaterialButton btnCancel =
                new com.google.android.material.button.MaterialButton(this);
        btnCancel.setText("Отмена");
        btnCancel.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFF6200EE));
        btnCancel.setStrokeWidth(6);
        btnCancel.setBackgroundColor(0x00000000);
        btnCancel.setTextColor(0xFF6200EE);
        btnCancel.setCornerRadius(48);

        LinearLayout.LayoutParams paramsCancel = new LinearLayout.LayoutParams(0, 168, 1);
        paramsCancel.setMargins(72, 0, 36, 0);
        btnCancel.setLayoutParams(paramsCancel);

        btnRow.addView(btnCancel);

        com.google.android.material.button.MaterialButton btnSave =
                new com.google.android.material.button.MaterialButton(this);
        btnSave.setText("Сохранить");
        btnSave.setCornerRadius(48);
        btnSave.setBackgroundColor(0xFF6200EE);
        btnSave.setTextColor(0xFFFFFFFF);

        LinearLayout.LayoutParams paramsSave = new LinearLayout.LayoutParams(0, 168, 1);
        paramsSave.setMargins(36, 0, 72, 0);
        btnSave.setLayoutParams(paramsSave);

        btnRow.addView(btnSave);

        dialog.setContentView(scrollView);
        dialog.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

        final int[] totals = new int[5];

        SupabaseRepository.RecipeFoodListCallback loadCb = new SupabaseRepository.RecipeFoodListCallback() {
            @Override public void onSuccess(java.util.List<com.example.caloriecounter.model.Recipe.RecipeFood> foods) {
                if (foods == null || foods.isEmpty()) { dialog.dismiss(); return; }

                for (com.example.caloriecounter.model.Recipe.RecipeFood ing : foods) {
                    totals[0] += ing.getTotalCalories();
                    totals[1] += ing.getTotalProtein();
                    totals[2] += ing.getTotalFat();
                    totals[3] += ing.getTotalCarbs();
                    totals[4] += ing.grams;
                }

                tvPerServing.setText(String.format("~%d ккал / 1 порция", totals[0]));
                tvCalc.setText(String.format("%d ккал", totals[0]));
                tvMacros.setText(String.format("Б: %dг | Ж: %dг | У: %dг", totals[1], totals[2], totals[3]));
                etValue.setText(String.valueOf(totals[4]));
            }
            @Override public void onError(String msg) { dialog.dismiss(); }
        };

        if (isUserRecipe) repo.fetchUserRecipeIngredients("eq." + recipeId, loadCb);
        else repo.fetchRecipeFoods("eq." + recipeId, loadCb);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String valStr = etValue.getText().toString().trim();
            if (valStr.isEmpty()) { return; }

            double enteredValue = Double.parseDouble(valStr);
            int selectedId = rgUnit.getCheckedRadioButtonId();
            boolean isGramsMode = (selectedId == rbGrams.getId());

            double factor = isGramsMode
                    ? (totals[4] > 0 ? (enteredValue / totals[4]) : 1.0)
                    : enteredValue;

            int finalCal = (int)Math.round(totals[0] * factor);
            int finalP = (int)Math.round(totals[1] * factor);
            int finalF = (int)Math.round(totals[2] * factor);
            int finalC = (int)Math.round(totals[3] * factor);
            int finalGrams = isGramsMode ? (int)enteredValue : (int)(totals[4] * factor);

            com.example.caloriecounter.model.FoodLog log = new com.example.caloriecounter.model.FoodLog();
            log.userId = userId;
            log.foodId = recipeId;
            log.foodName = binding.tvName.getText().toString();
            log.mealType = meal != null ? meal : "Другое";
            log.grams = finalGrams;
            log.totalCalories = finalCal;
            log.totalProtein = finalP;
            log.totalFat = finalF;
            log.totalCarbs = finalC;
            log.logDate = selectedDate != null ? selectedDate : java.time.LocalDate.now().toString();

            repo.addLog(log, log.logDate, new SupabaseRepository.VoidCallback() {
                @Override public void onSuccess() {
                    dialog.dismiss();
                    finishSuccess();
                }
                @Override public void onError(String m) {
                    dialog.dismiss();
                }
            });
        });

        dialog.show();
    }
}