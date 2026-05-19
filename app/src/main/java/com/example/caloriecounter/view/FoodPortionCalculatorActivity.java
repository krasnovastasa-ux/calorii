package com.example.caloriecounter.view;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.caloriecounter.R;
import com.example.caloriecounter.model.FoodLog;
import com.example.caloriecounter.repository.SupabaseRepository;

public class FoodPortionCalculatorActivity extends AppCompatActivity {
    private SupabaseRepository repo;
    private TextView tvRecipeName, tvPerServing, tvCalculated, tvMacrosCalc;
    private EditText etValue;
    private RadioGroup rgUnit;
    private Button btnSave, btnCancel;

    private String recipeId, recipeName, mealType, userId, selectedDate;
    private boolean isUserRecipe;
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
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updatePreview(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        rgUnit.setOnCheckedChangeListener((group, checkedId) -> updatePreview());
    }

    private void loadRecipeIngredients() {
        SupabaseRepository.RecipeFoodListCallback callback = new SupabaseRepository.RecipeFoodListCallback() {
            @Override public void onSuccess(java.util.List<com.example.caloriecounter.model.Recipe.RecipeFood> foods) {
                if (foods == null || foods.isEmpty()) { return; }
                for (com.example.caloriecounter.model.Recipe.RecipeFood ing : foods) {
                    totalCal += ing.getTotalCalories();
                    totalP += ing.getTotalProtein();
                    totalF += ing.getTotalFat();
                    totalC += ing.getTotalCarbs();
                    totalGrams += ing.grams;
                }
                etValue.setText(String.valueOf(totalGrams));
                updatePreview();
            }
            @Override public void onError(String msg) { }
        };
        if (isUserRecipe) repo.fetchUserRecipeIngredients("eq." + recipeId, callback);
        else repo.fetchRecipeFoods("eq." + recipeId, callback);
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
        tvMacrosCalc.setText(String.format("Белки: %d г | Жиры: %d г | Углеводы: %d г", finalP, finalF, finalC));
    }

    private void saveRecipeToDiary() {
        String valStr = etValue.getText().toString().trim();
        if (valStr.isEmpty() || Double.parseDouble(valStr) <= 0) {
            return;
        }

        double entered = Double.parseDouble(valStr);
        boolean isGramsMode = (rgUnit.getCheckedRadioButtonId() == R.id.rbGrams);
        double factor = (totalGrams > 0) ? (isGramsMode ? (entered / totalGrams) : entered) : 1.0;

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

        repo.addLog(log, log.logDate, new SupabaseRepository.VoidCallback() {
            @Override public void onSuccess() {
                setResult(RESULT_OK);
                finish();
            }
            @Override public void onError(String m) {
            }
        });
    }
}