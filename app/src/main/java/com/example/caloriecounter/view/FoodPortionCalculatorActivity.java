package com.example.caloriecounter.view;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.caloriecounter.R;
import com.example.caloriecounter.model.FoodLog;
import com.example.caloriecounter.model.Recipe;
import com.example.caloriecounter.repository.SupabaseRepository;

import java.util.List;

public class FoodPortionCalculatorActivity extends BaseActivity {

    private static final String TAG = "FOOD_PORTION_CALC";

    private SupabaseRepository repo;
    private TextView tvRecipeName, tvPerServing, tvCalculated, tvMacrosCalc;
    private EditText etValue;
    private RadioGroup rgUnit;
    private Button btnSave, btnCancel;

    private String recipeId, recipeName, mealType, userId, selectedDate;
    private boolean isUserRecipe;

    private List<Recipe.RecipeFood> recipeFoods;

    private int totalCal = 0, totalP = 0, totalF = 0, totalC = 0, totalGrams = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_food_portion_calculator);

        repo = new SupabaseRepository(this);
        initViews();
        loadExtras();
        setupListeners();
        loadRecipeIngredients();
    }

    private void initViews() {
        tvRecipeName = findViewById(R.id.tvRecipeName);
        tvPerServing = findViewById(R.id.tvPerServing);
        tvCalculated = findViewById(R.id.tvCalculated);
        tvMacrosCalc = findViewById(R.id.tvMacrosCalc);
        etValue = findViewById(R.id.etValue);
        rgUnit = findViewById(R.id.rgUnit);
        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);
    }

    private void loadExtras() {
        recipeId = getIntent().getStringExtra("recipe_id");
        recipeName = getIntent().getStringExtra("recipe_name");
        mealType = getIntent().getStringExtra("meal_type");
        userId = getIntent().getStringExtra("userId");
        selectedDate = getIntent().getStringExtra("selected_date");
        isUserRecipe = getIntent().getBooleanExtra("is_user_recipe", false);

        tvRecipeName.setText(recipeName != null ? recipeName : "Рецепт");
    }

    private void setupListeners() {
        btnCancel.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveRecipeToDiary());

        etValue.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePreview();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        rgUnit.setOnCheckedChangeListener((group, checkedId) -> updatePreview());
    }

    private void loadRecipeIngredients() {
        SupabaseRepository.RecipeFoodListCallback callback = new SupabaseRepository.RecipeFoodListCallback() {
            @Override
            public void onSuccess(List<Recipe.RecipeFood> foods) {
                if (foods == null || foods.isEmpty()) {
                    Toast.makeText(FoodPortionCalculatorActivity.this,
                            "Рецепт не содержит ингредиентов", Toast.LENGTH_SHORT).show();
                    return;
                }

                recipeFoods = foods;

                for (Recipe.RecipeFood ing : foods) {
                    totalCal += ing.getTotalCalories();
                    totalP += ing.getTotalProtein();
                    totalF += ing.getTotalFat();
                    totalC += ing.getTotalCarbs();
                    totalGrams += ing.grams;
                }

                etValue.setText(String.valueOf(totalGrams));
                updatePreview();
            }

            @Override
            public void onError(String msg) {
                Toast.makeText(FoodPortionCalculatorActivity.this,
                        "Ошибка загрузки: " + msg, Toast.LENGTH_SHORT).show();
            }
        };

        if (isUserRecipe) {
            repo.fetchUserRecipeIngredients("eq." + recipeId, callback);
        } else {
            repo.fetchRecipeFoods("eq." + recipeId, callback);
        }
    }

    private void updatePreview() {
        String valStr = etValue.getText().toString().trim();
        double entered = valStr.isEmpty() ? 0 : Double.parseDouble(valStr);
        boolean isGramsMode = (rgUnit.getCheckedRadioButtonId() == R.id.rbGrams);

        double factor = (totalGrams > 0)
                ? (isGramsMode ? (entered / totalGrams) : entered)
                : 0;

        int finalCal = (int)Math.round(totalCal * factor);
        int finalP  = (int)Math.round(totalP * factor);
        int finalF  = (int)Math.round(totalF * factor);
        int finalC  = (int)Math.round(totalC * factor);

        String unitText = isGramsMode ? (int)entered + " г" : (int)entered + " порц.";
        tvPerServing.setText(String.format("~%d ккал / %s", finalCal, unitText));

        tvCalculated.setText(String.format("%d ккал", finalCal));
        tvMacrosCalc.setText(String.format("Белки: %d г | Жиры: %d г | Углеводы: %d г",
                finalP, finalF, finalC));
    }

    private void saveRecipeToDiary() {
        if (recipeFoods == null || recipeFoods.isEmpty()) {
            Toast.makeText(this, "Ингредиенты ещё не загружены", Toast.LENGTH_SHORT).show();
            return;
        }

        String valStr = etValue.getText().toString().trim();
        if (valStr.isEmpty()) {
            etValue.setError("Введите значение");
            etValue.requestFocus();
            return;
        }

        double entered;
        try {
            entered = Double.parseDouble(valStr);
        } catch (NumberFormatException e) {
            etValue.setError("Некорректное число");
            etValue.requestFocus();
            return;
        }

        if (entered < 1 || entered > 10000) {
            etValue.setError("Допустимо: 1-10000");
            etValue.requestFocus();
            return;
        }

        boolean isGramsMode = (rgUnit.getCheckedRadioButtonId() == R.id.rbGrams);
        double factor = (totalGrams > 0)
                ? (isGramsMode ? (entered / totalGrams) : entered)
                : 1.0;

        FoodLog log = new FoodLog();
        log.userId = userId;
        log.foodId = recipeId;
        log.foodName = recipeName;
        log.mealType = mealType != null ? mealType : "Другое";
        log.grams = isGramsMode ? (int)entered : (int)(totalGrams * factor);
        log.totalCalories = (int)Math.round(totalCal * factor);
        log.totalProtein  = (int)Math.round(totalP * factor);
        log.totalFat      = (int)Math.round(totalF * factor);
        log.totalCarbs    = (int)Math.round(totalC * factor);
        log.logDate = selectedDate != null ? selectedDate : java.time.LocalDate.now().toString();

        btnSave.setEnabled(false);
        btnSave.setText("Сохранение...");

        repo.addLog(log, log.logDate, new SupabaseRepository.VoidCallback() {
            @Override
            public void onSuccess() {
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(String m) {
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Сохранить");
                    Toast.makeText(FoodPortionCalculatorActivity.this,
                            "Ошибка: " + m, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}