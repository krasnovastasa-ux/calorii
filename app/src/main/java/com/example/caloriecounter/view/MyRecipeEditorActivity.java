package com.example.caloriecounter.view;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.caloriecounter.databinding.ActivityMyRecipeEditorBinding;
import com.example.caloriecounter.model.Food;
import com.example.caloriecounter.model.UserRecipe;
import com.example.caloriecounter.model.UserRecipeIngredient;
import com.example.caloriecounter.repository.SupabaseRepository;
import java.util.ArrayList;
import java.util.List;

public class MyRecipeEditorActivity extends BaseActivity {
    private static final String TAG = "MY_RECIPE_EDITOR";
    private ActivityMyRecipeEditorBinding binding;
    private SupabaseRepository repo;
    private String userId, recipeId;
    private final List<IngredientRow> ingredientRows = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");

        binding = ActivityMyRecipeEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repo = new SupabaseRepository(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        userId = prefs.getString("user_id", "");
        recipeId = getIntent().getStringExtra("recipe_id");

        if (userId.isEmpty()) { finish(); return; }

        setupClicks();
        if (recipeId == null) clearFields();
        else loadRecipe();
    }

    private void setupClicks() {
        View btnSave = findView("btnSave");
        if (btnSave != null) btnSave.setOnClickListener(v -> save());

        View btnCancel = findView("btnCancel");
        if (btnCancel != null) btnCancel.setOnClickListener(v -> finish());

        View btnBack = findView("btnBack");
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        View btnAdd = findView("btnAddIngredient");
        if (btnAdd != null) btnAdd.setOnClickListener(v -> showFoodPicker());
    }

    private View findView(String id) {
        int res = getResources().getIdentifier(id, "id", getPackageName());
        return res != 0 ? findViewById(res) : null;
    }

    private void setTxt(String id, String val) { View v = findView(id); if (v instanceof EditText) ((EditText)v).setText(val); }
    private String getTxt(String id) { View v = findView(id); return (v instanceof EditText) ? ((EditText)v).getText().toString().trim() : ""; }
    private void setVis(String id, int vis) { View v = findView(id); if (v != null) v.setVisibility(vis); }

    private void clearFields() {
        setTxt("etName", ""); setTxt("etPrepTime", ""); setTxt("etCookTime", "");
        setTxt("etServings", ""); setTxt("etInstructions", "");
        View list = findView("llIngredientsList");
        if (list instanceof LinearLayout) ((LinearLayout)list).removeAllViews();
        ingredientRows.clear();
    }

    private void loadRecipe() {
        setVis("progressBar", View.VISIBLE);
        repo.fetchUserRecipeById(recipeId, new SupabaseRepository.UserRecipeCallback() {
            @Override public void onSuccess(UserRecipe r) {
                setTxt("etName", r.name); setTxt("etPrepTime", String.valueOf(r.prepTime));
                setTxt("etCookTime", String.valueOf(r.cookTime)); setTxt("etServings", String.valueOf(r.servings));
                setTxt("etInstructions", r.instructions != null ? r.instructions : "");
                loadIngredients();
            }
            @Override public void onError(String m) { setVis("progressBar", View.GONE); }
        });
    }

    private void loadIngredients() {
        repo.fetchUserRecipeIngredients("eq." + recipeId, new SupabaseRepository.RecipeFoodListCallback() {
            @Override public void onSuccess(List<com.example.caloriecounter.model.Recipe.RecipeFood> foods) {
                clearFields();
                if (foods != null) for (com.example.caloriecounter.model.Recipe.RecipeFood f : foods)
                    addRow(f.foodName, f.grams, f.foodId, f.caloriesPer100g, f.proteinPer100g, f.fatPer100g, f.carbsPer100g);
                setVis("progressBar", View.GONE);
            }
            @Override public void onError(String m) { setVis("progressBar", View.GONE); Log.e(TAG, "Load ing: " + m); }
        });
    }

    private void showFoodPicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MyRecipeEditorActivity.this);
        builder.setTitle("Выберите ингредиент");

        LinearLayout root = new LinearLayout(MyRecipeEditorActivity.this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 24, 36, 24);

        EditText etSearch = new EditText(MyRecipeEditorActivity.this);
        etSearch.setHint("Поиск продукта...");
        etSearch.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        etSearch.setPadding(24, 20, 24, 20);
        root.addView(etSearch);

        android.widget.ListView lvFoods = new android.widget.ListView(MyRecipeEditorActivity.this);
        lvFoods.setDivider(new android.graphics.drawable.ColorDrawable(0xFFEAEAEA));
        lvFoods.setDividerHeight(3);
        root.addView(lvFoods);

        builder.setView(root);
        AlertDialog dialog = builder.create();

        final List<Food> allFoods = new ArrayList<>();
        final List<Food> filteredFoods = new ArrayList<>();

        repo.fetchFoods(new SupabaseRepository.FoodCallback() {
            @Override public void onSuccess(List<Food> foods) {
                if (foods != null) {
                    allFoods.clear();
                    allFoods.addAll(foods);
                    filteredFoods.clear();
                    filteredFoods.addAll(foods);
                    renderFoodList(lvFoods, filteredFoods, dialog);
                }
            }
            @Override public void onError(String m) {
            }
        });

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase(java.util.Locale.getDefault()).trim();
                filteredFoods.clear();
                for (Food f : allFoods) {
                    if (f.name != null && f.name.toLowerCase(java.util.Locale.getDefault()).contains(query)) {
                        filteredFoods.add(f);
                    }
                }
                renderFoodList(lvFoods, filteredFoods, dialog);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        try {
            String accent = com.example.caloriecounter.utils.ThemeUtils.getAccent(this);
            int color = android.graphics.Color.parseColor(accent);
            etSearch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        } catch (Exception ignored) {}

        dialog.show();
    }
    private void renderFoodList(android.widget.ListView lvFoods,
                                List<Food> foods,
                                AlertDialog dialog) {
        if (foods == null || foods.isEmpty()) {
            android.widget.ArrayAdapter<String> emptyAdapter =
                    new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new String[]{"Ничего не найдено"});
            lvFoods.setAdapter(emptyAdapter);
            return;
        }

        java.util.List<String> names = new java.util.ArrayList<>();
        for (Food f : foods) {
            names.add(f.name + " — " + f.calories + " ккал");
        }

        android.widget.ArrayAdapter<String> adapter =
                new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
        lvFoods.setAdapter(adapter);

        lvFoods.setOnItemClickListener((parent, view, position, id) -> {
            Food selected = foods.get(position);

            EditText etGrams = new EditText(MyRecipeEditorActivity.this);
            etGrams.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            etGrams.setHint("Грамм (100)");
            etGrams.setText("100");

            AlertDialog amountDialog = new AlertDialog.Builder(MyRecipeEditorActivity.this)
                    .setTitle(selected.name)
                    .setView(etGrams)
                    .setPositiveButton("Добавить", (d2, w2) -> {
                        String val = etGrams.getText().toString().trim();
                        int grams = val.isEmpty() ? 100 : Integer.parseInt(val);
                        addRow(selected.name, grams, selected.id, selected.calories, selected.protein, selected.fat, selected.carbs);
                    })
                    .setNegativeButton("Отмена", null)
                    .create();

            amountDialog.show();

            try {
                String accent = com.example.caloriecounter.utils.ThemeUtils.getAccent(this);
                int color = android.graphics.Color.parseColor(accent);

                android.widget.Button btnPositive = amountDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
                android.widget.Button btnNegative = amountDialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);

                if (btnPositive != null) btnPositive.setTextColor(color);
                if (btnNegative != null) btnNegative.setTextColor(color);
            } catch (Exception ignored) {}
        });

    }

    private void addRow(String name, int grams, String foodId, int c, int p, int f, int cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(12,8,12,8);
        TextView t = new TextView(this); t.setText(name + " — " + grams + "г");
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView d = new TextView(this); d.setText("✕"); d.setTextColor(0xFFD32F2F); d.setPadding(16,0,0,0);
        d.setOnClickListener(v -> { View list = findView("llIngredientsList"); if(list instanceof LinearLayout) ((LinearLayout)list).removeView(row); ingredientRows.removeIf(r -> r.row == row); });
        row.addView(t); row.addView(d);
        View list = findView("llIngredientsList");
        if (list instanceof LinearLayout) ((LinearLayout)list).addView(row);
        ingredientRows.add(new IngredientRow(row, name, grams, foodId, c, p, f, cb));
    }

    private void save() {
        Log.d(TAG, "save() START");
        String name = getTxt("etName");
        if (name.isEmpty()) { return; }

        if (PreferenceManager.getDefaultSharedPreferences(this).getString("token", null) == null) {
            return;
        }

        UserRecipe recipe = new UserRecipe();
        recipe.userId = userId;
        recipe.name = name;
        recipe.instructions = getTxt("etInstructions");

        String pt = getTxt("etPrepTime"); recipe.prepTime = pt.isEmpty() ? 0 : Integer.parseInt(pt);
        String ct = getTxt("etCookTime"); recipe.cookTime = ct.isEmpty() ? 0 : Integer.parseInt(ct);
        String sv = getTxt("etServings"); recipe.servings = sv.isEmpty() ? 1 : Integer.parseInt(sv);

        setVis("progressBar", View.VISIBLE);
        Log.d(TAG, "Отправляем рецепт в Supabase...");

        repo.createUserRecipe(recipe, new SupabaseRepository.UserRecipeCallback() {
            @Override public void onSuccess(UserRecipe saved) {
                String id = saved.id != null ? saved.id : recipeId;
                Log.d(TAG, "Рецепт сохранён! ID=" + id);

                if (id != null) {
                    Log.d(TAG, "Вызываем saveIngredients('" + id + "')");
                    saveIngredients(id);
                } else {
                    Log.e(TAG, "ID рецепта = null!");
                    setVis("progressBar", View.GONE);
                }
            }

            @Override
            public void onError(String m) {
                Log.e(TAG, "Ошибка сохранения рецепта: " + m);
                setVis("progressBar", View.GONE);
            }
        });
        Log.d(TAG, " save() END (ожидание ответа)");
    }
    private void saveIngredients(String rid) {
        Log.d(TAG, "saveIngredients('" + rid + "') START");
        Log.d(TAG, "   ingredientRows.size() = " + ingredientRows.size());

        if (ingredientRows.isEmpty()) {
            Log.w(TAG, "   ⚠Список ингредиентов пуст");
            finishWithResult();
            return;
        }

        Log.d(TAG, " Сохраняем " + ingredientRows.size() + " ингредиентов...");
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(ingredientRows.size());

        for (int i = 0; i < ingredientRows.size(); i++) {
            IngredientRow r = ingredientRows.get(i);

            int totalCal = (int) Math.round(r.cal100 * r.grams / 100.0);
            int totalP = (int) Math.round(r.pro100 * r.grams / 100.0);
            int totalF = (int) Math.round(r.fat100 * r.grams / 100.0);
            int totalC = (int) Math.round(r.carb100 * r.grams / 100.0);

            Log.d(TAG, "   [" + (i+1) + "] " + r.name + " | " + r.grams + "г | " + totalCal);

            UserRecipeIngredient ing = new UserRecipeIngredient();
            ing.recipeId = rid;
            ing.foodId = r.foodId;
            ing.name = r.name;
            ing.grams = r.grams;
            ing.calories = totalCal;
            ing.protein = totalP;
            ing.fat = totalF;
            ing.carbs = totalC;

            repo.addUserRecipeIngredient(ing, new SupabaseRepository.VoidCallback() {
                @Override public void onSuccess() {
                    int left = counter.decrementAndGet();
                    Log.d(TAG, "      Сохранено! Осталось: " + left);
                    if (left == 0) finishWithResult();
                }
                @Override public void onError(String m) {
                    int left = counter.decrementAndGet();
                    Log.e(TAG, "      Ошибка: " + m + " (осталось: " + left + ")");
                    if (left == 0) finishWithResult();
                }
            });
        }
    }

    private void finishWithResult() {
        setVis("progressBar", View.GONE);
        setResult(RESULT_OK);
        finish();
    }

    private static class IngredientRow {
        View row; String name, foodId; int grams, cal100, pro100, fat100, carb100;
        IngredientRow(View r, String n, int g, String f, int c, int p, int ft, int cb) {
            row=r; name=n; grams=g; foodId=f; cal100=c; pro100=p; fat100=ft; carb100=cb;
        }
    }
}