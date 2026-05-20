package com.example.caloriecounter.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
import java.util.HashSet;
import android.widget.AdapterView;
import java.util.Collections;
import android.widget.ArrayAdapter;
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
import android.widget.PopupMenu;
import java.util.Collections;
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

    private enum SortType { NONE, A_Z, Z_A, CAL_ASC, CAL_DESC }
    private SortType currentSort = SortType.NONE;
    private String selectedCategory = "Все";

    private final String[] CATEGORIES = {
            "Все", "Хлеб", "Гарниры", "Мясо", "Рыба", "Молочные",
            "Овощи", "Фрукты", "Супы", "Десерты", "Напитки", "Соусы", "Снеки", "Орехи"
    };

    private static final int REQ_SCAN_BARCODE = 202;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFoodSearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String token = prefs.getString("token", null);
        String uid = prefs.getString("user_id", null);

        if (token == null || uid == null) {
            Log.e("AUTH", "Сессия не найдена. Требуется вход.");
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
        setupFilterButtons();
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

        hideAllLists();

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                hideAllLists();
                binding.etSearch.setText("");

                int pos = tab.getPosition();

                if (pos == 2 || pos == 3) {
                    binding.btnCategoryMenu.setVisibility(View.GONE);
                } else {
                    binding.btnCategoryMenu.setVisibility(View.VISIBLE);
                }

                switch (pos) {
                    case 0:
                        binding.llProducts.setVisibility(LinearLayout.VISIBLE);
                        applyFiltersAndSort();
                        break;
                    case 1:
                        binding.llFavorites.setVisibility(LinearLayout.VISIBLE);
                        syncFavIdsFromViewModel();
                        renderFavorites("");
                        break;
                    case 2:
                        binding.llRecipes.setVisibility(LinearLayout.VISIBLE);
                        renderRecipes("");
                        break;
                    case 3:
                        binding.llMyRecipes.setVisibility(LinearLayout.VISIBLE);
                        renderMyRecipes("");
                        break;
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        if (binding.tabLayout.getTabAt(0) != null) {
            binding.tabLayout.getTabAt(0).select();
        }
    }

    private void hideAllLists() {
        binding.llProducts.setVisibility(LinearLayout.GONE);
        binding.llFavorites.setVisibility(LinearLayout.GONE);
        binding.llRecipes.setVisibility(LinearLayout.GONE);
        binding.llMyRecipes.setVisibility(LinearLayout.GONE);
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
                if (binding.tabLayout.getSelectedTabPosition() == 0) applyFiltersAndSort();
            }
        });

        vm.getFavorites().observe(this, favs -> {
            if (favs != null) {
                favIds.clear();
                for (Food f : favs) {
                    String targetId = (f.foodId != null) ? f.foodId : f.id;
                    if (targetId != null) favIds.add(targetId);
                }
                if (binding.tabLayout.getSelectedTabPosition() == 1) renderFavorites("");
            }
        });

        vm.getRecipes().observe(this, r -> {
            if (r != null) {
                allRecipes = r;
                if (binding.tabLayout.getSelectedTabPosition() == 2) renderRecipes("");
            }
        });

        vm.getUserRecipes().observe(this, r -> {
            if (r != null) {
                myRecipes = r;
                calculateUserRecipeTotalsAsync(myRecipes, () -> {
                    if (binding.tabLayout.getSelectedTabPosition() == 3) renderMyRecipes("");
                });
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
                    case 0: applyFiltersAndSort(); break;
                    case 1: applyFiltersAndSort(); break;
                    case 2: applyFiltersAndSort(); break;
                    case 3: applyFiltersAndSort(); break;
                }
            }

            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void applyFiltersAndSort() {
        int tabPos = binding.tabLayout.getSelectedTabPosition();
        String query = binding.etSearch.getText().toString().toLowerCase(Locale.getDefault()).trim();

        updateCategoryButtonText();

        switch (tabPos) {
            case 0: renderProducts(query); break;
            case 1: renderFavorites(query); break;
            case 2: renderRecipes(query); break;
            case 3: renderMyRecipes(query); break;
        }
    }

    private void renderProducts(String query) {
        binding.llProducts.removeAllViews();
        if (allFoods == null || allFoods.isEmpty()) {
            addEmpty(binding.llProducts, "Загрузка...");
            return;
        }

        List<Food> filtered = new ArrayList<>();
        for (Food f : allFoods) {
            boolean matchCat = selectedCategory.equals("Все") ||
                    (f.category != null && f.category.equals(selectedCategory));
            boolean matchQuery = f.name != null && f.name.toLowerCase(Locale.getDefault()).contains(query);

            if (matchCat && matchQuery) filtered.add(f);
        }
        applySortingToFoodList(filtered);

        for (Food f : filtered) {
            addProductRow(binding.llProducts, f, favIds.contains(f.id) || favIds.contains(f.foodId));
        }
        if (filtered.isEmpty()) {
            addEmpty(binding.llProducts, query.isEmpty() ? "Список пуст" : "Ничего не найдено");
        }
    }

    private void renderFavorites(String query) {
        binding.llFavorites.removeAllViews();
        if (allFoods == null || allFoods.isEmpty()) {
            addEmpty(binding.llFavorites, "Загрузка...");
            return;
        }

        List<Food> filtered = new ArrayList<>();
        for (Food f : allFoods) {
            boolean isFav = favIds.contains(f.id) || favIds.contains(f.foodId);
            if (!isFav) continue;

            boolean matchCat = selectedCategory.equals("Все") ||
                    (f.category != null && f.category.equals(selectedCategory));
            boolean matchQuery = f.name != null && f.name.toLowerCase(Locale.getDefault()).contains(query);

            if (matchCat && matchQuery) filtered.add(f);
        }
        applySortingToFoodList(filtered);

        for (Food f : filtered) {
            addProductRow(binding.llFavorites, f, true);
        }
        if (filtered.isEmpty()) {
            addEmpty(binding.llFavorites, query.isEmpty() ? "Нет избранных" : "Ничего не найдено");
        }
    }

    private void renderRecipes(String query) {
        binding.llRecipes.removeAllViews();
        if (allRecipes == null || allRecipes.isEmpty()) {
            addEmpty(binding.llRecipes, "Загрузка...");
            return;
        }

        List<Recipe> filtered = new ArrayList<>();
        for (Recipe r : allRecipes) {
            boolean matchQuery = r.name != null && r.name.toLowerCase(Locale.getDefault()).contains(query);
            if (matchQuery) filtered.add(r);
        }
        applySortingToRecipeList(filtered);

        for (Recipe r : filtered) {
            TextView tv = new TextView(this);
            tv.setText(String.format("%s (%d ккал)", r.name, r.getTotalCalories()));
            tv.setTextSize(16); tv.setPadding(12, 16, 12, 16); tv.setClickable(true);
            tv.setOnClickListener(v -> openRecipeDetail(r));
            binding.llRecipes.addView(tv);
        }
        if (filtered.isEmpty()) {
            addEmpty(binding.llRecipes, query.isEmpty() ? "Рецепты не найдены" : "Ничего не найдено");
        }
    }

    private void renderMyRecipes(String query) {
        if (binding.llMyRecipes == null) return;
        binding.llMyRecipes.removeAllViews();

        if (myRecipes == null) {
            addEmpty(binding.llMyRecipes, "Загрузка...");
            return;
        }

        List<UserRecipe> filtered = new ArrayList<>();
        for (UserRecipe r : myRecipes) {
            boolean matchQuery = r.name != null && r.name.toLowerCase(Locale.getDefault()).contains(query);
            if (matchQuery) filtered.add(r);
        }
        applySortingToUserRecipeList(filtered);

        for (UserRecipe r : filtered) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(36, 24, 36, 24);
            row.setClickable(true);

            TextView name = new TextView(this);
            name.setText(r.name != null ? r.name : "Без названия");
            name.setTextSize(16); name.setTextColor(0xFF212121);

            TextView info = new TextView(this);
            info.setText(String.format("⏱ %d мин | 🍽 %d порций", r.prepTime + r.cookTime, r.servings));
            info.setTextSize(13); info.setTextColor(0xFF757575);

            row.addView(name); row.addView(info);
            row.setOnClickListener(v -> {
                Intent i = new Intent(this, RecipeDetailActivity.class);
                i.putExtra("recipe_id", r.id); i.putExtra("recipe_name", r.name);
                i.putExtra("meal", meal); i.putExtra("userId", userId);
                i.putExtra("selected_date", selectedDate);
                startActivity(i);
            });
            binding.llMyRecipes.addView(row);
        }

        if (query.isEmpty()) {
            com.google.android.material.button.MaterialButton btnAdd = new com.google.android.material.button.MaterialButton(this);
            btnAdd.setText("Создать новый рецепт");
            btnAdd.setTextColor(android.graphics.Color.WHITE);
            String accent = com.example.caloriecounter.utils.ThemeUtils.getAccent(this);
            btnAdd.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(accent)));
            btnAdd.setCornerRadius(24); btnAdd.setPadding(40, 30, 40, 30);
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

    private void applySortingToFoodList(List<Food> list) {
        switch (currentSort) {
            case A_Z: Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name)); break;
            case Z_A: Collections.sort(list, (a, b) -> b.name.compareToIgnoreCase(a.name)); break;
            case CAL_ASC: Collections.sort(list, (a, b) -> Integer.compare(a.calories, b.calories)); break;
            case CAL_DESC: Collections.sort(list, (a, b) -> Integer.compare(b.calories, a.calories)); break;
        }
    }

    private void applySortingToRecipeList(List<Recipe> list) {
        switch (currentSort) {
            case A_Z: Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name)); break;
            case Z_A: Collections.sort(list, (a, b) -> b.name.compareToIgnoreCase(a.name)); break;
            case CAL_ASC: Collections.sort(list, (a, b) -> Integer.compare(a.getTotalCalories(), b.getTotalCalories())); break;
            case CAL_DESC: Collections.sort(list, (a, b) -> Integer.compare(b.getTotalCalories(), a.getTotalCalories())); break;
        }
    }

    private void applySortingToUserRecipeList(List<UserRecipe> list) {
        switch (currentSort) {
            case A_Z: Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name)); break;
            case Z_A: Collections.sort(list, (a, b) -> b.name.compareToIgnoreCase(a.name)); break;
            case CAL_ASC: Collections.sort(list, (a, b) -> Integer.compare(a.totalCalories, b.totalCalories)); break;
            case CAL_DESC: Collections.sort(list, (a, b) -> Integer.compare(b.totalCalories, a.totalCalories)); break;
        }
    }

    private void addProductRow(LinearLayout container, Food f, boolean isFav) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(12, 10, 12, 10);

        TextView name = new TextView(this);
        name.setText(String.format("%s — %d ккал", f.name, f.calories));
        name.setTextSize(15); name.setTextColor(0xFF333333);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView star = new TextView(this);
        star.setText(isFav ? "★" : "☆");
        star.setTextSize(20);
        star.setTextColor(isFav ? 0xFFFFA000 : 0xFF999999);
        star.setPadding(8, 0, 8, 0); star.setClickable(true);
        star.setFocusable(false); star.setFocusableInTouchMode(false);

        String targetId = (f.foodId != null) ? f.foodId : f.id;

        star.setOnClickListener(v -> {
            boolean currentlyFav = favIds.contains(targetId);
            if (!currentlyFav) {
                favIds.add(targetId);
                star.setText("★"); star.setTextColor(0xFFFFA000);
                if (binding.tabLayout.getSelectedTabPosition() == 1) renderFavorites("");
            } else {
                favIds.remove(targetId);
                star.setText("☆"); star.setTextColor(0xFF999999);
                if (binding.tabLayout.getSelectedTabPosition() == 1) renderFavorites("");
            }

            if (!currentlyFav) vm.addToFavorites(f, new SupabaseRepository.VoidCallback() {
                @Override public void onSuccess() { }
                @Override public void onError(String m) {
                    runOnUiThread(() -> {
                        favIds.remove(targetId); star.setText("☆"); star.setTextColor(0xFF999999);
                    });
                }
            }); else {
                vm.removeFromFavorites(targetId, new SupabaseRepository.VoidCallback() {
                    @Override public void onSuccess() { }
                    @Override public void onError(String m) {
                        runOnUiThread(() -> {
                            favIds.add(targetId); star.setText("★"); star.setTextColor(0xFFFFA000);
                        });
                    }
                });
            }
        });

        row.addView(name); row.addView(star); row.setClickable(false);
        row.setOnClickListener(v -> openFoodDetail(f));
        container.addView(row);

        View div = new View(this); div.setBackgroundColor(0xFFEAEAEA);
        div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        container.addView(div);
    }

    private void addEmpty(LinearLayout container, String text) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(0, 40, 0, 40); tv.setTextColor(0xFF999999);
        container.addView(tv);
    }

    private void openFoodDetail(Food f) {
        Intent i = new Intent(this, FoodDetailActivity.class);
        i.putExtra("meal", meal); i.putExtra("foodId", f.id); i.putExtra("foodName", f.name);
        i.putExtra("calories", f.calories); i.putExtra("protein", f.protein);
        i.putExtra("fat", f.fat); i.putExtra("carbs", f.carbs);
        i.putExtra("sugar", f.sugar); i.putExtra("fiber", f.fiber);
        i.putExtra("userId", userId); i.putExtra("selected_date", selectedDate);
        startActivity(i);
    }

    private void openRecipeDetail(Recipe r) {
        Intent i = new Intent(this, RecipeDetailActivity.class);
        i.putExtra("recipe_id", r.id); i.putExtra("recipe_name", r.name);
        i.putExtra("meal", meal); i.putExtra("userId", userId);
        i.putExtra("selected_date", selectedDate);
        startActivity(i);
    }

    private void openMyRecipeEditor(UserRecipe recipe) {
        Intent i = new Intent(this, MyRecipeEditorActivity.class);
        i.putExtra("meal", meal); i.putExtra("userId", userId); i.putExtra("selected_date", selectedDate);
        if (recipe != null) {
            i.putExtra("recipe_id", recipe.id); i.putExtra("recipe_name", recipe.name);
            i.putExtra("instructions", recipe.instructions);
            i.putExtra("prep_time", recipe.prepTime);
            i.putExtra("cook_time", recipe.cookTime);
            i.putExtra("servings", recipe.servings);
        }
        startActivity(i);
    }

    private void setupFilterButtons() {
        String accentHex = com.example.caloriecounter.utils.ThemeUtils.getAccent(this);
        int accentColor = android.graphics.Color.parseColor(accentHex);

        binding.btnSortMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, binding.btnSortMenu);
            popup.getMenu().add(0, 1, 0, "Без сортировки");
            popup.getMenu().add(0, 2, 1, "А до Я");
            popup.getMenu().add(0, 3, 2, "Я до А");
            popup.getMenu().add(0, 4, 3, "Ккал: по возрастанию");
            popup.getMenu().add(0, 5, 4, "Ккал: по убыванию");

            int currentId = getCurrentMenuItemId();
            popup.getMenu().findItem(currentId).setChecked(true);
            popup.getMenu().setGroupCheckable(0, true, true);
            popup.getMenu().findItem(currentId).setIcon(new android.graphics.drawable.ColorDrawable(accentColor));

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == 2) currentSort = SortType.A_Z;
                else if (id == 3) currentSort = SortType.Z_A;
                else if (id == 4) currentSort = SortType.CAL_ASC;
                else if (id == 5) currentSort = SortType.CAL_DESC;
                else currentSort = SortType.NONE;

                updateSortButtonText();
                applyFiltersAndSort();
                return true;
            });
            popup.show();
            try {
                android.view.MenuItem item = popup.getMenu().findItem(currentId);
                if(item != null) item.setIcon(new android.graphics.drawable.ColorDrawable(accentColor));
            } catch(Exception ignored){}
        });

        binding.btnCategoryMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, binding.btnCategoryMenu);
            for (int i = 0; i < CATEGORIES.length; i++) {
                popup.getMenu().add(0, i, i, CATEGORIES[i]);
            }
            int currentPos = 0;
            for(int i=0; i<CATEGORIES.length; i++) {
                if(CATEGORIES[i].equals(selectedCategory)) { currentPos = i; break; }
            }
            popup.getMenu().findItem(currentPos).setChecked(true);
            popup.getMenu().setGroupCheckable(0, true, true);
            popup.getMenu().findItem(currentPos).setIcon(new android.graphics.drawable.ColorDrawable(accentColor));

            popup.setOnMenuItemClickListener(item -> {
                selectedCategory = CATEGORIES[item.getItemId()];
                updateCategoryButtonText();
                applyFiltersAndSort();
                return true;
            });
            popup.show();
            try {
                android.view.MenuItem item = popup.getMenu().findItem(currentPos);
                if(item != null) item.setIcon(new android.graphics.drawable.ColorDrawable(accentColor));
            } catch(Exception ignored){}
        });

        updateSortButtonText();
        updateCategoryButtonText();
    }

    private int getCurrentMenuItemId() {
        if (currentSort == SortType.NONE) return 1;
        else if (currentSort == SortType.A_Z) return 2;
        else if (currentSort == SortType.Z_A) return 3;
        else if (currentSort == SortType.CAL_ASC) return 4;
        else if (currentSort == SortType.CAL_DESC) return 5;
        return 1;
    }

    private void updateSortButtonText() {
        switch (currentSort) {
            case A_Z: binding.btnSortMenu.setText("А до Я"); break;
            case Z_A: binding.btnSortMenu.setText("Я до А"); break;
            case CAL_ASC: binding.btnSortMenu.setText("Ккал возр."); break;
            case CAL_DESC: binding.btnSortMenu.setText("Ккал убыв."); break;
            default: binding.btnSortMenu.setText("Сортировка");
        }
    }

    private void updateCategoryButtonText() {
        if (selectedCategory.equals("Все")) binding.btnCategoryMenu.setText("Категория");
        else binding.btnCategoryMenu.setText(selectedCategory);
    }

    private void calculateUserRecipeTotalsAsync(List<UserRecipe> recipes, Runnable onComplete) {
        if (recipes == null || recipes.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(recipes.size());

        for (UserRecipe recipe : recipes) {
            if (recipe.totalCalories > 0) {
                pending.decrementAndGet();
                continue;
            }
            vm.getRepo().fetchUserRecipeIngredients("eq." + recipe.id, new SupabaseRepository.RecipeFoodListCallback() {
                @Override public void onSuccess(List<Recipe.RecipeFood> foods) {
                    int total = 0;
                    if (foods != null) for (Recipe.RecipeFood f : foods) total += f.getTotalCalories();
                    recipe.totalCalories = total;
                    if (pending.decrementAndGet() == 0 && onComplete != null) onComplete.run();
                }
                @Override public void onError(String msg) {
                    if (pending.decrementAndGet() == 0 && onComplete != null) onComplete.run();
                }
            });
        }
    }
}