package com.example.caloriecounter.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
import java.util.HashSet;
import java.util.Set;
import android.view.View;
import android.content.SharedPreferences;
import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;
import android.widget.TextView;
import com.example.caloriecounter.R;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.caloriecounter.databinding.ActivityFoodSearchBinding;
import com.example.caloriecounter.model.Food;
import com.example.caloriecounter.model.Recipe;
import com.example.caloriecounter.model.UserRecipe;
import com.example.caloriecounter.repository.SupabaseRepository;
import com.example.caloriecounter.viewmodel.FoodSearchViewModel;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FoodSearchActivity extends BaseActivity {
    private static final int REQUEST_CODE_MY_RECIPE = 100;
    private static final String TAG = "FAV_UI";
    private static final int REQUEST_CODE_ADD_TO_DIARY = 101;

    private ActivityFoodSearchBinding binding;
    private FoodSearchViewModel vm;

    private List<Food> allFoods = new ArrayList<>();
    private Set<String> favIds = new HashSet<>();
    private List<Recipe> allRecipes = new ArrayList<>();
    private List<UserRecipe> myRecipes = new ArrayList<>();

    private String meal, userId, selectedDate;
    private boolean productsLoaded = false;

    private static final int REQ_SCAN_BARCODE = 202;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFoodSearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String token = prefs.getString("token", null);
        String uid = prefs.getString("user_id", null);

        if (token == null || uid == null) {
            Log.e("AUTH", "Сессия не найдена! Требуется вход.");
            new AlertDialog.Builder(this)
                    .setTitle("Сессия истекла")
                    .setMessage("Пожалуйста, войдите в аккаунт заново")
                    .setPositiveButton("Войти", (d, w) -> {
                        Intent i = new Intent(this, LoginActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        finish();
                    })
                    .setCancelable(false)
                    .show();
            return;
        }
        Log.d("AUTH", "Сессия активна: userId=" + uid);

        vm = new ViewModelProvider(this).get(FoodSearchViewModel.class);
        meal = getIntent().getStringExtra("meal");
        userId = getIntent().getStringExtra("userId");
        selectedDate = getIntent().getStringExtra("selected_date");


        binding.btnScanBarcode.setOnClickListener(v -> {
            Intent i = new Intent(this, BarcodeScannerActivity.class);
            i.putExtra("userId", userId);
            i.putExtra("meal", meal);
            i.putExtra("selected_date", selectedDate);
            startActivityForResult(i, REQ_SCAN_BARCODE);
        });

        setupTabs();
        loadData();
        setupSearch();
        showContainer(binding.llProducts);
    }

    @Override protected void onResume() {
        super.onResume();
        vm.loadFavorites();
        vm.loadUserRecipes();
    }

    private void setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Все"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Избранное"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Рецепты"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Мои рецепты"));

        binding.llProducts.setVisibility(LinearLayout.GONE);
        binding.llFavorites.setVisibility(LinearLayout.GONE);
        binding.llRecipes.setVisibility(LinearLayout.GONE);
        binding.llMyRecipes.setVisibility(LinearLayout.GONE);

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {

                binding.etSearch.setText("");
                binding.llProducts.setVisibility(LinearLayout.GONE);
                binding.llFavorites.setVisibility(LinearLayout.GONE);
                binding.llRecipes.setVisibility(LinearLayout.GONE);
                binding.llMyRecipes.setVisibility(LinearLayout.GONE);

                switch (tab.getPosition()) {
                    case 0:
                        binding.llProducts.setVisibility(LinearLayout.VISIBLE);
                        filterAndRenderProducts("");
                        break;
                    case 1:
                        binding.llFavorites.setVisibility(LinearLayout.VISIBLE);
                        syncFavIdsFromViewModel();
                        renderFavorites();
                        break;
                    case 2:
                        binding.llRecipes.setVisibility(LinearLayout.VISIBLE);
                        renderRecipes();
                        break;
                    case 3:
                        binding.llMyRecipes.setVisibility(LinearLayout.VISIBLE);
                        renderMyRecipes();
                        break;
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        if (binding.tabLayout.getTabAt(0) != null) {
            binding.tabLayout.getTabAt(0).select();
        }
        binding.llProducts.setVisibility(LinearLayout.VISIBLE);
        filterAndRenderProducts("");
    }

    private void showContainer(LinearLayout target) {
        binding.llProducts.setVisibility(LinearLayout.GONE);
        binding.llFavorites.setVisibility(LinearLayout.GONE);
        binding.llRecipes.setVisibility(LinearLayout.GONE);
        binding.llMyRecipes.setVisibility(LinearLayout.GONE);
        target.setVisibility(LinearLayout.VISIBLE);
    }

    private void syncFavIdsFromViewModel() {
        Set<String> vmIds = vm.getFavoriteIds();
        if (!favIds.equals(vmIds)) {
            favIds = new HashSet<>(vmIds);
        }
    }

    private void loadData() {
        vm.getProducts().observe(this, foods -> {
            if (foods != null) {
                allFoods = foods; productsLoaded = true;
                if (binding.tabLayout.getSelectedTabPosition() == 0) filterAndRenderProducts("");
            }
        });

        vm.getFavorites().observe(this, favs -> {
            if (favs != null) {
                favIds.clear();
                for (Food f : favs) {
                    String targetId = (f.foodId != null) ? f.foodId : f.id;
                    if (targetId != null) favIds.add(targetId);
                }
                if (binding.tabLayout.getSelectedTabPosition() == 1) renderFavorites();
            }
        });

        vm.getRecipes().observe(this, r -> {
            if (r != null) {
                allRecipes = r;
                if (binding.tabLayout.getSelectedTabPosition() == 2) renderRecipes();
            }
        });

        vm.getUserRecipes().observe(this, r -> {
            if (r != null) {
                myRecipes = r;
                if (binding.tabLayout.getSelectedTabPosition() == 3) {
                    renderMyRecipes();
                }
            }
        });

        vm.loadProducts();
        vm.loadFavorites();
        vm.loadRecipes();
        vm.loadUserRecipes();
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                int tabPos = binding.tabLayout.getSelectedTabPosition();
                String query = s.toString();

                switch (tabPos) {
                    case 0: filterAndRenderProducts(query); break;
                    case 1: filterAndRenderFavorites(query); break;
                    case 2: filterAndRenderRecipes(query); break;
                    case 3: filterAndRenderMyRecipes(query); break;
                }
            }

            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void filterAndRenderProducts(String query) {
        binding.llProducts.removeAllViews();
        if (allFoods == null || allFoods.isEmpty()) {
            addEmpty(binding.llProducts, "Загрузка...");
            return;
        }
        String q = query.toLowerCase(Locale.getDefault()).trim();
        List<Food> filtered = new ArrayList<>();
        for (Food f : allFoods) {
            if (f.name != null && f.name.toLowerCase(Locale.getDefault()).contains(q)) {
                filtered.add(f);
            }
        }
        for (Food f : filtered) {
            addProductRow(binding.llProducts, f, favIds.contains(f.id) || favIds.contains(f.foodId));
        }
        if (filtered.isEmpty()) {
            addEmpty(binding.llProducts, query.isEmpty() ? "Список пуст" : "Ничего не найдено");
        }
    }

    private void filterAndRenderFavorites(String query) {
        binding.llFavorites.removeAllViews();
        if (allFoods == null || allFoods.isEmpty()) {
            addEmpty(binding.llFavorites, "Загрузка...");
            return;
        }
        String q = query.toLowerCase(Locale.getDefault()).trim();
        List<Food> filtered = new ArrayList<>();
        for (Food f : allFoods) {
            boolean isFav = favIds.contains(f.id) || favIds.contains(f.foodId);
            if (isFav && f.name != null && f.name.toLowerCase(Locale.getDefault()).contains(q)) {
                filtered.add(f);
            }
        }
        for (Food f : filtered) {
            addProductRow(binding.llFavorites, f, true);
        }
        if (filtered.isEmpty()) {
            addEmpty(binding.llFavorites, query.isEmpty() ? "Нет избранных" : "Ничего не найдено");
        }
    }

    private void filterAndRenderRecipes(String query) {
        binding.llRecipes.removeAllViews();
        if (allRecipes == null || allRecipes.isEmpty()) {
            addEmpty(binding.llRecipes, "Загрузка...");
            return;
        }
        String q = query.toLowerCase(Locale.getDefault()).trim();
        List<Recipe> filtered = new ArrayList<>();
        for (Recipe r : allRecipes) {
            if (r.name != null && r.name.toLowerCase(Locale.getDefault()).contains(q)) {
                filtered.add(r);
            }
        }
        for (Recipe r : filtered) {
            TextView tv = new TextView(this);
            tv.setText(String.format("%s (%d ккал)", r.name, r.getTotalCalories() > 0 ? r.getTotalCalories() : 0));
            tv.setTextSize(16); tv.setPadding(12, 16, 12, 16); tv.setClickable(true);
            tv.setOnClickListener(v -> openRecipeDetail(r));
            binding.llRecipes.addView(tv);
        }
        if (filtered.isEmpty()) {
            addEmpty(binding.llRecipes, query.isEmpty() ? "Рецепты не найдены" : "Ничего не найдено");
        }
    }

    private void filterAndRenderMyRecipes(String query) {
        if (binding.llMyRecipes == null) return;
        binding.llMyRecipes.removeAllViews();

        if (myRecipes == null) {
            addEmpty(binding.llMyRecipes, "Загрузка...");
            return;
        }

        String q = query.toLowerCase(Locale.getDefault()).trim();
        List<UserRecipe> filtered = new ArrayList<>();
        for (UserRecipe r : myRecipes) {
            if (r != null && r.name != null && r.name.toLowerCase(Locale.getDefault()).contains(q)) {
                filtered.add(r);
            }
        }

        for (UserRecipe r : filtered) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(36, 24, 36, 24);
            row.setClickable(true);

            TextView name = new TextView(this);
            name.setText(r.name != null ? r.name : "Без названия");
            name.setTextSize(16);
            name.setTextColor(0xFF212121);

            TextView info = new TextView(this);
            info.setText(String.format("⏱ %d мин | 🍽 %d порций", r.prepTime + r.cookTime, r.servings));
            info.setTextSize(13);
            info.setTextColor(0xFF757575);

            row.addView(name); row.addView(info);
            row.setOnClickListener(v -> {
                Intent i = new Intent(this, RecipeDetailActivity.class);
                i.putExtra("recipe_id", r.id);
                i.putExtra("recipe_name", r.name);
                i.putExtra("meal", meal);
                i.putExtra("userId", userId);
                i.putExtra("selected_date", selectedDate);
                startActivity(i);
            });
            binding.llMyRecipes.addView(row);
        }

        if (query.isEmpty()) {
            com.google.android.material.button.MaterialButton btnAdd =
                    new com.google.android.material.button.MaterialButton(this);
            btnAdd.setText("➕ Создать новый рецепт");
            btnAdd.setTextColor(android.graphics.Color.WHITE);
            btnAdd.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(com.example.caloriecounter.utils.ThemeUtils.getAccent(this))));
            btnAdd.setCornerRadius(24);
            btnAdd.setPadding(40, 30, 40, 30);
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(40, 20, 40, 40);
            btnAdd.setLayoutParams(params);
            btnAdd.setOnClickListener(v -> {
                Intent i = new Intent(this, MyRecipeEditorActivity.class);
                i.putExtra("userId", userId);
                startActivityForResult(i, 100);
            });
            binding.llMyRecipes.addView(btnAdd);
        }

        if (filtered.isEmpty() && !query.isEmpty()) {
            addEmpty(binding.llMyRecipes, "Ничего не найдено");
        }
    }

    private void renderFavorites() {
        binding.llFavorites.removeAllViews();
        List<Food> favs = new ArrayList<>();
        for (Food f : allFoods) if (favIds.contains(f.id) || favIds.contains(f.foodId)) favs.add(f);
        for (Food f : favs) addProductRow(binding.llFavorites, f, true);
        if (favs.isEmpty()) addEmpty(binding.llFavorites, "Нет избранных продуктов");
    }

    private void renderRecipes() {
        binding.llRecipes.removeAllViews();
        for (Recipe r : allRecipes) {
            TextView tv = new TextView(this);
            tv.setText(String.format("%s (%d ккал)", r.name,
                    r.getTotalCalories() > 0 ? r.getTotalCalories() : 0));
            tv.setTextSize(16); tv.setPadding(12, 16, 12, 16); tv.setClickable(true);
            tv.setOnClickListener(v -> openRecipeDetail(r));
            binding.llRecipes.addView(tv);
        }
        if (allRecipes.isEmpty()) addEmpty(binding.llRecipes, "Рецепты не найдены");
    }

    private void renderMyRecipes() {
        if (binding.llMyRecipes == null) return;
        if (binding.tabLayout.getSelectedTabPosition() != 3) return;

        binding.llMyRecipes.removeAllViews();

        if (myRecipes != null && !myRecipes.isEmpty()) {
            android.util.Log.d("MY_RECIPES", "Рендерим " + myRecipes.size() + " рецептов");
            for (UserRecipe r : myRecipes) {
                if (r == null) continue;
                android.widget.LinearLayout row = new android.widget.LinearLayout(this);
                row.setOrientation(android.widget.LinearLayout.VERTICAL);
                row.setPadding(36, 24, 36, 24);
                row.setClickable(true);
                android.widget.TextView name = new android.widget.TextView(this);
                name.setText(r.name != null ? r.name : "Без названия");
                name.setTextSize(16);
                name.setTextColor(0xFF212121);
                android.widget.TextView info = new android.widget.TextView(this);
                info.setText(String.format("⏱ %d мин | 🍽 %d порций", r.prepTime + r.cookTime, r.servings));
                info.setTextSize(13);
                info.setTextColor(0xFF757575);
                row.addView(name); row.addView(info);

                final UserRecipe recipeRef = r;
                row.setOnClickListener(v -> {
                    Intent i = new Intent(this, RecipeDetailActivity.class);
                    i.putExtra("recipe_id", recipeRef.id);
                    i.putExtra("recipe_name", recipeRef.name);
                    i.putExtra("meal", meal);
                    i.putExtra("userId", userId);
                    i.putExtra("selected_date", selectedDate);
                    startActivity(i);
                });
                binding.llMyRecipes.addView(row);
            }
        } else {
            android.widget.TextView empty = new android.widget.TextView(this);
            empty.setText("У вас пока нет своих рецептов");
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, 40, 0, 40);
            empty.setTextColor(0xFF757575);
            binding.llMyRecipes.addView(empty);
        }

        com.google.android.material.button.MaterialButton btnAdd = new com.google.android.material.button.MaterialButton(this);
        btnAdd.setText("➕ Создать новый рецепт");
        btnAdd.setTextColor(android.graphics.Color.WHITE);
        btnAdd.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(com.example.caloriecounter.utils.ThemeUtils.getAccent(this))));
        btnAdd.setCornerRadius(24);
        btnAdd.setPadding(40, 30, 40, 30);
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(40, 20, 40, 40);
        btnAdd.setLayoutParams(params);
        btnAdd.setOnClickListener(v -> {
            Intent i = new Intent(this, MyRecipeEditorActivity.class);
            i.putExtra("userId", userId);
            startActivityForResult(i, 100);
        });
        binding.llMyRecipes.addView(btnAdd);
    }



    private void addProductRow(LinearLayout container, Food f, boolean isFav) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(12, 10, 12, 10);

        TextView name = new TextView(this);
        name.setText(String.format("%s — %d ккал", f.name, f.calories));
        name.setTextSize(15);
        name.setTextColor(0xFF333333);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView star = new TextView(this);
        star.setText(isFav ? "★" : "☆");
        star.setTextSize(20);
        star.setTextColor(isFav ? 0xFFFFA000 : 0xFF999999);
        star.setPadding(8, 0, 8, 0);
        star.setClickable(true);
        star.setFocusable(false);
        star.setFocusableInTouchMode(false);

        String targetId = (f.foodId != null) ? f.foodId : f.id;

        star.setOnClickListener(v -> {
            boolean currentlyFav = favIds.contains(targetId);

            if (!currentlyFav) {
                favIds.add(targetId);
                star.setText("★");
                star.setTextColor(0xFFFFA000);
                if (binding.tabLayout.getSelectedTabPosition() == 1) renderFavorites();
            } else {
                favIds.remove(targetId);
                star.setText("☆");
                star.setTextColor(0xFF999999);
                if (binding.tabLayout.getSelectedTabPosition() == 1) renderFavorites();
            }

            if (!currentlyFav) {
                vm.addToFavorites(f, new SupabaseRepository.VoidCallback() {
                    @Override public void onSuccess() {  }
                    @Override public void onError(String m) {
                        runOnUiThread(() -> {
                            favIds.remove(targetId);
                            star.setText("☆");
                            star.setTextColor(0xFF999999);
                            if (binding.tabLayout.getSelectedTabPosition() == 1) renderFavorites();
                        });
                    }
                });
            } else {
                vm.removeFromFavorites(targetId, new SupabaseRepository.VoidCallback() {
                    @Override public void onSuccess() { }
                    @Override public void onError(String m) {
                        runOnUiThread(() -> {
                            favIds.add(targetId);
                            star.setText("★");
                            star.setTextColor(0xFFFFA000);
                            if (binding.tabLayout.getSelectedTabPosition() == 1) renderFavorites();
                        });
                    }
                });
            }
        });

        row.addView(name);
        row.addView(star);
        row.setClickable(false);
        row.setOnClickListener(v -> openFoodDetail(f));
        container.addView(row);

        View div = new View(this);
        div.setBackgroundColor(0xFFEAEAEA);
        div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        container.addView(div);
    }
    private void addEmpty(LinearLayout container, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(0, 40, 0, 40);
        tv.setTextColor(0xFF999999);
        container.addView(tv);
    }

    private void openFoodDetail(Food f) {
        Intent i = new Intent(this, FoodDetailActivity.class);
        i.putExtra("meal", meal);
        i.putExtra("foodId", f.id);
        i.putExtra("foodName", f.name);
        i.putExtra("calories", f.calories);
        i.putExtra("protein", f.protein); i.putExtra("fat", f.fat);
        i.putExtra("carbs", f.carbs);
        i.putExtra("sugar", f.sugar);
        i.putExtra("fiber", f.fiber);
        i.putExtra("userId", userId);
        i.putExtra("selected_date", selectedDate);
        startActivity(i);
    }

    private void openRecipeDetail(Recipe r) {
        Intent i = new Intent(this, RecipeDetailActivity.class);
        i.putExtra("recipe_id", r.id);
        i.putExtra("recipe_name", r.name);
        i.putExtra("meal", meal);
        i.putExtra("userId", userId);
        i.putExtra("selected_date", selectedDate);
        startActivity(i);
    }

    private void openMyRecipeEditor(UserRecipe recipe) {
        Intent i = new Intent(this, MyRecipeEditorActivity.class);
        i.putExtra("meal", meal); i.putExtra("userId", userId); i.putExtra("selected_date", selectedDate);
        if (recipe != null) {
            i.putExtra("recipe_id", recipe.id); i.putExtra("recipe_name", recipe.name);
            i.putExtra("instructions", recipe.instructions);
            i.putExtra("prep_time", recipe.prepTime);;
            i.putExtra("cook_time", recipe.cookTime);
            i.putExtra("servings", recipe.servings);
        }
        startActivity(i);
    }
}